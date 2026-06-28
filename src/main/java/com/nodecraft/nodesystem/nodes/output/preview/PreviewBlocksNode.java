package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlocksPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewRequest;
import com.nodecraft.nodesystem.preview.protocol.PreviewStyle;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Block ghost 预览节点：节点层只应产出 {@link PreviewBlock}/{@link PreviewBlocksPayload}，
 * 不要再把渲染器侧类型（或中间 DTO）当作跨层协议（见 v1.1 类级改造清单）。
 */
@NodeInfo(
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
    private static final String INPUT_COORDS_ID = "input_coords";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";
    private static final String OUTPUT_BLOCK_COUNT_ID = "output_block_count";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    // Execution throttling: prevents rapid re-execution when node is selected (which causes flickering)
    private volatile long lastExecutionTime = 0;
    private static final long MIN_EXECUTION_INTERVAL_MS = 50;
    private static final long EMPTY_INPUT_HOLD_MS = 750;

    @NodeProperty(displayName = "Block Type", category = "Preview", order = 2)
    private String blockType = "minecraft:stone";

    @NodeProperty(displayName = "Show Outline", category = "Preview", order = 3)
    private boolean showOutline = true;

    @NodeProperty(displayName = "Transparency", category = "Preview", order = 4)
    private float transparency = 0.5f;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 5)
    private int duration = 30;

    private volatile String cachedPreviewId;
    private volatile int cachedInputSignature = 0;
    private volatile String cachedEffectiveBlockType;
    private volatile long lastNonEmptyInputAt = 0L;

    private UUID previewId = UUID.randomUUID();
    public PreviewBlocksNode() {
        super(UUID.randomUUID(), "output.preview.preview_blocks");

        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "Block list or block position list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_PLACEMENTS_ID, "Block Placements", "Position and block assignments to preview", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_PLACEMENTS_TREE_ID, "Block Placements Tree", "Tree-grouped position and block assignments to preview", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_COORDS_ID, "Coordinates", "Fallback coordinate list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Ghost block type", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Preview instance identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_COUNT_ID, "Block Count", "Number of previewed blocks", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // Throttle rapid re-execution when node is selected (prevents flickering)
        long now = System.currentTimeMillis();
        if (previewEnabled && now - lastExecutionTime < MIN_EXECUTION_INTERVAL_MS) {
            // Skip execution if called too soon
            return;
        }
        lastExecutionTime = now;
        boolean success = false;
        int blockCount = 0;

        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);
        Object placementsObj = inputValues.get(INPUT_BLOCK_PLACEMENTS_ID);
        Object placementsTreeObj = inputValues.get(INPUT_BLOCK_PLACEMENTS_TREE_ID);
        Object coordsObj = inputValues.get(INPUT_COORDS_ID);
        Object blockTypeObj = inputValues.get(INPUT_BLOCK_TYPE_ID);

        String effectiveBlockType = blockTypeObj instanceof String value && !value.isBlank() ? value : blockType;

        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
            cachedPreviewId = null;
            cachedInputSignature = 0;
            cachedEffectiveBlockType = null;
        } else {
            List<PreviewBlock> previewBlocks = new ArrayList<>();
            collectPreviewBlocks(blocksObj, effectiveBlockType, previewBlocks);
            collectPreviewBlocks(placementsObj, effectiveBlockType, previewBlocks);
            collectPreviewBlocks(placementsTreeObj, effectiveBlockType, previewBlocks);
            collectPreviewBlocks(coordsObj, effectiveBlockType, previewBlocks);

            blockCount = previewBlocks.size();

            if (!previewBlocks.isEmpty()) {
                lastNonEmptyInputAt = now;
                int inputSignature = computePreviewBlocksSignature(previewBlocks);
                boolean unchanged = inputSignature == cachedInputSignature
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
                        cachedEffectiveBlockType = effectiveBlockType;
                        success = true;
                    }
                }
            } else {
                boolean keepExisting = cachedPreviewId != null
                    && PreviewManager.hasActivePreview(cachedPreviewId)
                    && (now - lastNonEmptyInputAt) < EMPTY_INPUT_HOLD_MS;
                if (keepExisting) {
                    success = true;
                } else {
                    PreviewManager.hideNodePreviews(getId().toString());
                    cachedPreviewId = null;
                    cachedInputSignature = 0;
                    cachedEffectiveBlockType = null;
                }
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId.toString());
        outputValues.put(OUTPUT_BLOCK_COUNT_ID, blockCount);
    }

    private int computePreviewBlocksSignature(List<PreviewBlock> blocks) {
        int hash = 17;
        hash = 31 * hash + blocks.size();
        long sx = 0L;
        long sy = 0L;
        long sz = 0L;
        int xh = 0;
        for (PreviewBlock block : blocks) {
            long x = Math.round(block.x());
            long y = Math.round(block.y());
            long z = Math.round(block.z());
            sx += x;
            sy += y;
            sz += z;
            xh ^= (int) (31L * x + 17L * y + z);
        }
        hash = 31 * hash + Long.hashCode(sx);
        hash = 31 * hash + Long.hashCode(sy);
        hash = 31 * hash + Long.hashCode(sz);
        hash = 31 * hash + xh;
        return hash;
    }

    private void collectPreviewBlocks(Object source, String effectiveBlockType, List<PreviewBlock> out) {
        if (source instanceof DataTreeData tree) {
            for (DataTreeData.Branch branch : tree.getBranches()) {
                for (Object item : branch.items()) {
                    PreviewBlock block = toPreviewBlock(item, effectiveBlockType);
                    if (block != null) {
                        out.add(block);
                    }
                }
            }
            return;
        }

        if (source instanceof BlockPosList blockPosList) {
            for (BlockPos pos : blockPosList.getPositions()) {
                if (pos != null) {
                    out.add(new PreviewBlock(pos.getX(), pos.getY(), pos.getZ(), effectiveBlockType));
                }
            }
            return;
        }

        if (source instanceof List<?> list) {
            for (Object item : list) {
                PreviewBlock block = toPreviewBlock(item, effectiveBlockType);
                if (block != null) {
                    out.add(block);
                }
            }
            return;
        }

        PreviewBlock block = toPreviewBlock(source, effectiveBlockType);
        if (block != null) {
            out.add(block);
        }
    }

    private @Nullable PreviewBlock toPreviewBlock(Object value, String effectiveBlockType) {
        if (value instanceof BlockPlacementData placement
            && placement.pos() != null
            && placement.blockId() != null
            && !placement.blockId().isBlank()) {
            BlockPos pos = placement.pos();
            return new PreviewBlock(pos.getX(), pos.getY(), pos.getZ(), placement.blockId());
        }
        if (value instanceof Coordinate coordinate) {
            return new PreviewBlock(coordinate.getX(), coordinate.getY(), coordinate.getZ(), effectiveBlockType);
        }
        if (value instanceof BlockPos pos) {
            return new PreviewBlock(pos.getX(), pos.getY(), pos.getZ(), effectiveBlockType);
        }
        return null;
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
