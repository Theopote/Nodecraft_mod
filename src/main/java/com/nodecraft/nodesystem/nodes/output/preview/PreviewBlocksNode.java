package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.GhostBlockPlacement;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.preview.preview_blocks",
    displayName = "Preview Blocks",
    description = "Previews block coordinates or block lists as temporary ghost blocks.",
    category = "output.preview",
    order = 1
)
public class PreviewBlocksNode extends BaseCustomUINode {

    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_COORDS_ID = "input_coords";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";
    private static final String OUTPUT_BLOCK_COUNT_ID = "output_block_count";

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

    private UUID previewId = UUID.randomUUID();
    public PreviewBlocksNode() {
        super(UUID.randomUUID(), "output.preview.preview_blocks");

        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "Block list or block position list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_COORDS_ID, "Coordinates", "Fallback coordinate list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Ghost block type", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Preview instance identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_COUNT_ID, "Block Count", "Number of previewed blocks", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Previews block coordinates or block lists as temporary ghost blocks.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        int blockCount = 0;

        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);
        Object coordsObj = inputValues.get(INPUT_COORDS_ID);
        Object blockTypeObj = inputValues.get(INPUT_BLOCK_TYPE_ID);

        String effectiveBlockType = blockTypeObj instanceof String value && !value.isBlank() ? value : blockType;

        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
        } else {
            List<GhostBlockPlacement> placements = new ArrayList<>();
            collectPlacements(blocksObj, effectiveBlockType, placements);
            collectPlacements(coordsObj, effectiveBlockType, placements);

            blockCount = placements.size();
            PreviewManager.hideNodePreviews(getId().toString());

            if (!placements.isEmpty()) {
                PreviewOptions options = new PreviewOptions()
                        .ghostBlockMode()
                        .setOpacity(transparency)
                        .setDuration(duration);
                options.showOutline = showOutline;

                String newPreviewId = PreviewManager.showGhostBlockPlacements(
                        getId().toString(),
                        placements,
                        options
                );
                if (newPreviewId != null) {
                    previewId = UUID.nameUUIDFromBytes(newPreviewId.getBytes());
                    success = true;
                }
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId.toString());
        outputValues.put(OUTPUT_BLOCK_COUNT_ID, blockCount);
    }

    private void collectPlacements(Object source, String effectiveBlockType, List<GhostBlockPlacement> out) {
        if (source instanceof BlockPosList blockPosList) {
            for (BlockPos pos : blockPosList.getPositions()) {
                out.add(new GhostBlockPlacement(
                        new Vec3d(pos.getX(), pos.getY(), pos.getZ()),
                        effectiveBlockType,
                        transparency
                ));
            }
            return;
        }

        if (source instanceof List<?> list) {
            for (Object item : list) {
                GhostBlockPlacement placement = toPlacement(item, effectiveBlockType);
                if (placement != null) {
                    out.add(placement);
                }
            }
            return;
        }

        GhostBlockPlacement placement = toPlacement(source, effectiveBlockType);
        if (placement != null) {
            out.add(placement);
        }
    }

    private GhostBlockPlacement toPlacement(Object value, String effectiveBlockType) {
        if (value instanceof Coordinate coordinate) {
            return new GhostBlockPlacement(
                    new Vec3d(coordinate.getX(), coordinate.getY(), coordinate.getZ()),
                    effectiveBlockType,
                    transparency
            );
        }
        if (value instanceof BlockPos pos) {
            return new GhostBlockPlacement(
                    new Vec3d(pos.getX(), pos.getY(), pos.getZ()),
                    effectiveBlockType,
                    transparency
            );
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
