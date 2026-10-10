package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import com.nodecraft.nodesystem.preview.PreviewBlocksSignature;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlocksPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewRequest;
import com.nodecraft.nodesystem.preview.protocol.PreviewStyle;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Block ghost preview: typed block/placement inputs → {@link PreviewBlocksPayload} via GHOST.
 */
@NodeInfo(
    effect = NodeEffect.PREVIEW_WRITE,
    id = "output.preview.preview_blocks",
    displayName = "Preview Blocks",
    description = "Previews block coordinates, placements, or placement trees as temporary ghost blocks.",
    category = "output.preview",
    order = 1
)
public class PreviewBlocksNode extends BaseCustomUINode {

    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_BLOCK_PLACEMENTS_ID = "input_block_placements";
    private static final String INPUT_BLOCK_PLACEMENTS_TREE_ID = "input_block_placements_tree";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";
    private static final String OUTPUT_BLOCK_COUNT_ID = "output_block_count";
    private static final String OUTPUT_SOURCE_COUNT_ID = "output_source_count";
    private static final String OUTPUT_PREVIEW_COUNT_ID = "output_preview_count";
    private static final String OUTPUT_TRUNCATED_ID = "output_truncated";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Block Type", category = "Preview", order = 2)
    private String blockType = "minecraft:stone";

    @NodeProperty(displayName = "Show Outline", category = "Preview", order = 3)
    private boolean showOutline = true;

    @NodeProperty(displayName = "Transparency", category = "Preview", order = 4)
    private float transparency = 0.5f;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 5)
    private int duration = 30;

    private volatile String cachedPreviewId;
    private volatile long cachedInputSignature = 0L;
    private volatile long cachedStyleSignature = 0L;
    private volatile String cachedEffectiveBlockType;

    private UUID previewId = UUID.randomUUID();

    public PreviewBlocksNode() {
        super(UUID.randomUUID(), "output.preview.preview_blocks");

        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "Block list or block position list", NodeDataType.BLOCK_LIST, this, false, false));
        addInputPort(new BasePort(INPUT_BLOCK_PLACEMENTS_ID, "Block Placements", "Position and block assignments to preview", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_PLACEMENTS_TREE_ID, "Block Placements Tree", "Tree-grouped position and block assignments to preview", NodeDataType.DATA_TREE, this, false, false));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Ghost block type", NodeDataType.STRING, this, false, false));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Preview instance identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_COUNT_ID, "Block Count", "Number of previewed blocks (alias of Preview Count)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SOURCE_COUNT_ID, "Source Count", "Number of source placement entries visited", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_COUNT_ID, "Preview Count", "Number of unique cells sent to preview", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TRUNCATED_ID, "Truncated", "Whether the preview budget truncated the payload", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why preview failed or was empty", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        int sourceCount = 0;
        int previewCount = 0;
        boolean truncated = false;
        String error = "";

        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);
        Object placementsObj = inputValues.get(INPUT_BLOCK_PLACEMENTS_ID);
        Object placementsTreeObj = inputValues.get(INPUT_BLOCK_PLACEMENTS_TREE_ID);
        Object blockTypeObj = inputValues.get(INPUT_BLOCK_TYPE_ID);

        String effectiveBlockType = blockTypeObj instanceof String value && !value.isBlank() ? value : blockType;

        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
            cachedPreviewId = null;
            cachedInputSignature = 0L;
            cachedStyleSignature = 0L;
            cachedEffectiveBlockType = null;
            error = "Preview disabled";
            publish(success, sourceCount, previewCount, truncated, error);
            return;
        }

        CollectResult collected = collectBudgeted(
            blocksObj, placementsObj, placementsTreeObj, effectiveBlockType);
        sourceCount = collected.sourceCount();
        truncated = collected.truncated();
        List<PreviewBlock> previewBlocks = collected.blocks();
        previewCount = previewBlocks.size();

        if (!previewBlocks.isEmpty()) {
            long inputSignature = PreviewBlocksSignature.computeContentFingerprint(previewBlocks);
            long styleSignature = PreviewBlocksSignature.computeStyleFingerprint(
                transparency, showOutline, duration);
            boolean unchanged = inputSignature == cachedInputSignature
                && styleSignature == cachedStyleSignature
                && effectiveBlockType.equals(cachedEffectiveBlockType)
                && cachedPreviewId != null
                && PreviewManager.hasActivePreview(cachedPreviewId);

            if (unchanged) {
                success = true;
            } else {
                PreviewBlocksPayload payload = new PreviewBlocksPayload(previewBlocks);
                PreviewStyle style = PreviewStyle.forGhostBlocks(
                    1.0f,
                    1.0f,
                    1.0f,
                    transparency,
                    showOutline,
                    null,
                    2.0f,
                    0.1f,
                    duration * 20
                );
                String newPreviewId = PreviewManager.showPreview(
                    new PreviewRequest(getId().toString(), payload, style, PreviewBackend.GHOST, context)
                );
                if (newPreviewId != null) {
                    previewId = UUID.nameUUIDFromBytes(newPreviewId.getBytes());
                    cachedPreviewId = newPreviewId;
                    cachedInputSignature = inputSignature;
                    cachedStyleSignature = styleSignature;
                    cachedEffectiveBlockType = effectiveBlockType;
                    success = true;
                } else {
                    error = "Preview renderer rejected the payload";
                }
            }
        } else {
            // Empty payload: PreviewManager owns empty-input grace / hide timing.
            PreviewManager.showPreview(new PreviewRequest(
                getId().toString(),
                new PreviewBlocksPayload(List.of()),
                PreviewStyle.forGhostBlocks(
                    1.0f, 1.0f, 1.0f, transparency, showOutline, null, 2.0f, 0.1f, duration * 20),
                PreviewBackend.GHOST,
                context
            ));
            if (cachedPreviewId != null && PreviewManager.hasActivePreview(cachedPreviewId)) {
                success = true;
            } else {
                cachedPreviewId = null;
                cachedInputSignature = 0L;
                cachedStyleSignature = 0L;
                cachedEffectiveBlockType = null;
                error = truncated ? "Preview truncated with no cells" : "No preview blocks";
            }
        }

        publish(success, sourceCount, previewCount, truncated, error);
    }

    private void publish(boolean success, int sourceCount, int previewCount, boolean truncated, String error) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId.toString());
        outputValues.put(OUTPUT_BLOCK_COUNT_ID, previewCount);
        outputValues.put(OUTPUT_SOURCE_COUNT_ID, sourceCount);
        outputValues.put(OUTPUT_PREVIEW_COUNT_ID, previewCount);
        outputValues.put(OUTPUT_TRUNCATED_ID, truncated);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private CollectResult collectBudgeted(
        Object blocksObj,
        Object placementsObj,
        Object placementsTreeObj,
        String effectiveBlockType
    ) {
        LinkedHashMap<BlockPos, PreviewBlock> byCell = new LinkedHashMap<>();
        CollectState state = new CollectState();
        int maxCells = GenerationLimits.MAX_PREVIEW_BLOCKS;
        int maxSourceVisits = GenerationLimits.MAX_BLOCK_PLACEMENTS;

        collectInto(blocksObj, effectiveBlockType, byCell, state, maxCells, maxSourceVisits);
        if (!state.visitBudgetExhausted) {
            collectInto(placementsObj, effectiveBlockType, byCell, state, maxCells, maxSourceVisits);
        }
        if (!state.visitBudgetExhausted) {
            collectInto(placementsTreeObj, effectiveBlockType, byCell, state, maxCells, maxSourceVisits);
        }
        boolean truncated = state.truncated || state.visitBudgetExhausted;
        return new CollectResult(List.copyOf(byCell.values()), state.sourceCount, truncated);
    }

    private void collectInto(
        Object source,
        String effectiveBlockType,
        LinkedHashMap<BlockPos, PreviewBlock> byCell,
        CollectState state,
        int maxCells,
        int maxSourceVisits
    ) {
        if (source == null || state.visitBudgetExhausted) {
            return;
        }
        switch (source) {
            case DataTreeData tree -> {
                for (DataTreeData.Branch branch : tree.getBranches()) {
                    for (Object item : branch.items()) {
                        if (!acceptItem(item, effectiveBlockType, byCell, state, maxCells, maxSourceVisits)) {
                            return;
                        }
                    }
                }
                return;
            }
            case BlockPosList blockPosList -> {
                for (BlockPos pos : blockPosList.getPositions()) {
                    if (!acceptItem(pos, effectiveBlockType, byCell, state, maxCells, maxSourceVisits)) {
                        return;
                    }
                }
                return;
            }
            case List<?> list -> {
                for (Object item : list) {
                    if (!acceptItem(item, effectiveBlockType, byCell, state, maxCells, maxSourceVisits)) {
                        return;
                    }
                }
                return;
            }
            default -> {
            }
        }
        acceptItem(source, effectiveBlockType, byCell, state, maxCells, maxSourceVisits);
    }

    /** @return false when visit budget is exhausted and callers should stop */
    private boolean acceptItem(
        Object item,
        String effectiveBlockType,
        LinkedHashMap<BlockPos, PreviewBlock> byCell,
        CollectState state,
        int maxCells,
        int maxSourceVisits
    ) {
        if (state.sourceCount >= maxSourceVisits) {
            state.visitBudgetExhausted = true;
            state.truncated = true;
            return false;
        }
        state.sourceCount++;
        PreviewBlock block = toPreviewBlock(item, effectiveBlockType);
        if (block == null) {
            return true;
        }
        BlockPos cell = PreviewBlocksSignature.toCell(block);
        if (byCell.containsKey(cell)) {
            byCell.put(cell, block);
            return true;
        }
        if (byCell.size() >= maxCells) {
            state.truncated = true;
            return true;
        }
        byCell.put(cell, block);
        return true;
    }

    private static final class CollectState {
        int sourceCount;
        boolean truncated;
        boolean visitBudgetExhausted;
    }

    private @Nullable PreviewBlock toPreviewBlock(Object value, String effectiveBlockType) {
        if (value instanceof BlockPlacementData placement
            && placement.pos() != null
            && placement.blockId() != null
            && !placement.blockId().isBlank()) {
            BlockPos pos = placement.pos();
            if (pos != null) {
                return new PreviewBlock(pos.getX(), pos.getY(), pos.getZ(), placement.blockId(), placement.stateData());
            }
        }
        if (value instanceof Coordinate(int x, int y, int z)) {
            return new PreviewBlock(x, y, z, effectiveBlockType);
        }
        if (value instanceof BlockPos pos) {
            return new PreviewBlock(pos.getX(), pos.getY(), pos.getZ(), effectiveBlockType);
        }
        return null;
    }

    private record CollectResult(List<PreviewBlock> blocks, int sourceCount, boolean truncated) {
    }

    @Override
    protected float calculateUIHeight() {
        return 0.0f;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 0.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return false;
    }

    public String getBlockType() {
        return blockType;
    }

    public void setBlockType(String value) {
        if (value != null && !value.equals(blockType)) {
            blockType = value;
            markDirty();
        }
    }

    public boolean isPreviewEnabled() {
        return previewEnabled;
    }

    public void setPreviewEnabled(boolean value) {
        if (previewEnabled != value) {
            previewEnabled = value;
            markDirty();
        }
    }

    public float getTransparency() {
        return transparency;
    }

    public void setTransparency(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        if (transparency != clamped) {
            transparency = clamped;
            markDirty();
        }
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int value) {
        int clamped = Math.max(1, value);
        if (duration != clamped) {
            duration = clamped;
            markDirty();
        }
    }

    public boolean isShowOutline() {
        return showOutline;
    }

    public void setShowOutline(boolean value) {
        if (showOutline != value) {
            showOutline = value;
            markDirty();
        }
    }

    @Override
    public @Nullable Object getNodeState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("blockType", blockType);
        state.put("transparency", transparency);
        state.put("duration", duration);
        state.put("showOutline", showOutline);
        state.put("previewId", previewId.toString());
        return state;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("previewEnabled") instanceof Boolean value) {
            previewEnabled = value;
        }
        if (map.get("blockType") instanceof String value) {
            blockType = value;
        }
        if (map.get("transparency") instanceof Number value) {
            transparency = Math.max(0.0f, Math.min(1.0f, value.floatValue()));
        }
        if (map.get("duration") instanceof Number value) {
            duration = Math.max(1, value.intValue());
        }
        if (map.get("showOutline") instanceof Boolean value) {
            showOutline = value;
        }
        if (map.get("previewId") instanceof String value) {
            try {
                previewId = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                previewId = UUID.randomUUID();
            }
        }
    }
}
