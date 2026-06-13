package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "world.selection.snap_points_to_blocks",
    displayName = "Snap Point List To Blocks",
    description = "Snaps a point list onto the block grid using an explicit snap mode",
    category = "world.selection",
    order = 4
)
public class SnapPointListToBlocksNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_COUNT_ID = "output_valid_count";
    private static final String OUTPUT_SKIPPED_COUNT_ID = "output_skipped_count";

    private SnapPointToBlockNode.SnapMode snapMode = SnapPointToBlockNode.SnapMode.NEAREST;
    private boolean removeDuplicates = true;

    public SnapPointListToBlocksNode() {
        super(UUID.randomUUID(), "world.selection.snap_points_to_blocks");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Collection of Point, Vector, Position, or Block Coordinate values to snap onto the block grid",
            NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks",
            "Snapped block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of snapped block coordinates in the final output", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_COUNT_ID, "Valid Count",
            "Number of inputs that were successfully resolved to points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SKIPPED_COUNT_ID, "Skipped Count",
            "Number of inputs skipped because they were not valid points", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDisplayName() {
        return "Snap Point List To Blocks";
    }

    @Override
    public String getDescription() {
        return "Snaps a point list onto the block grid using an explicit snap mode";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object value = inputValues.get(INPUT_POINTS_ID);

        Collection<?> values;
        if (value instanceof Collection<?> collection) {
            values = collection;
        } else {
            outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_COUNT_ID, 0);
            outputValues.put(OUTPUT_SKIPPED_COUNT_ID, 0);
            return;
        }

        LinkedHashSet<BlockPos> uniquePositions = new LinkedHashSet<>();
        BlockPosList blocks = new BlockPosList();
        int validCount = 0;
        int skippedCount = 0;

        for (Object entry : values) {
            Vector3d point = WorldSelectionResolveUtils.resolveVector3d(entry);
            if (point == null) {
                skippedCount++;
                continue;
            }

            BlockPos snapped = new BlockPos(snap(point.x), snap(point.y), snap(point.z));
            validCount++;

            if (removeDuplicates) {
                uniquePositions.add(snapped);
            } else {
                blocks.add(snapped);
            }
        }

        if (removeDuplicates) {
            blocks.addAll(uniquePositions);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
        outputValues.put(OUTPUT_VALID_COUNT_ID, validCount);
        outputValues.put(OUTPUT_SKIPPED_COUNT_ID, skippedCount);
    }

    public SnapPointToBlockNode.SnapMode getSnapMode() {
        return snapMode;
    }

    public void setSnapMode(SnapPointToBlockNode.SnapMode snapMode) {
        this.snapMode = snapMode == null ? SnapPointToBlockNode.SnapMode.NEAREST : snapMode;
        markDirty();
    }

    public void setSnapModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setSnapMode(SnapPointToBlockNode.SnapMode.NEAREST);
            return;
        }
        try {
            setSnapMode(SnapPointToBlockNode.SnapMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setSnapMode(SnapPointToBlockNode.SnapMode.NEAREST);
        }
    }

    public boolean isRemoveDuplicates() {
        return removeDuplicates;
    }

    public void setRemoveDuplicates(boolean removeDuplicates) {
        this.removeDuplicates = removeDuplicates;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("snapMode", snapMode.name());
        state.put("removeDuplicates", removeDuplicates);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object mode = stateMap.get("snapMode");
            if (mode instanceof String modeString) {
                setSnapModeString(modeString);
            }

            Object dedupe = stateMap.get("removeDuplicates");
            if (dedupe instanceof Boolean enabled) {
                setRemoveDuplicates(enabled);
            }
        }
    }

    private int snap(double value) {
        return switch (snapMode) {
            case FLOOR -> (int) Math.floor(value);
            case CEIL -> (int) Math.ceil(value);
            case NEAREST -> (int) Math.round(value);
        };
    }

}
