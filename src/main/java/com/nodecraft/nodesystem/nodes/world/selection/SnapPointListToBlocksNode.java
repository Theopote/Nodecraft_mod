package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.PointUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.selection.snap_points_to_blocks",
    displayName = "Snap Point List To Blocks",
    description = "Snaps a strict POINT_LIST onto the block cell lattice; any malformed member fails the whole node",
    category = "world.selection",
    order = 3
)
public class SnapPointListToBlocksNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Snap Mode", category = "Snap", order = 1)
    private WorldSelectionResolveUtils.SnapMode snapMode = WorldSelectionResolveUtils.SnapMode.NEAREST_CENTER;

    @NodeProperty(displayName = "Remove Duplicates", category = "Snap", order = 2)
    private boolean removeDuplicates = true;

    public SnapPointListToBlocksNode() {
        super(UUID.randomUUID(), "world.selection.snap_points_to_blocks");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Strict POINT_LIST to snap onto the block cell lattice",
            NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks",
            "Snapped block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of snapped block positions in the final output", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "Whether the POINT_LIST was strict and snapped successfully", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when snap fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Snaps a strict POINT_LIST onto the block cell lattice; any malformed member fails the whole node";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> points = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        if (points == null) {
            publishInvalid("Points must be a non-empty strict POINT_LIST of finite PointData entries.");
            return;
        }

        LinkedHashSet<BlockPos> uniquePositions = new LinkedHashSet<>();
        BlockPosList blocks = new BlockPosList();

        for (Vector3d point : points) {
            BlockPos snapped = WorldSelectionResolveUtils.snapPointToBlock(point, snapMode);
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
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public WorldSelectionResolveUtils.SnapMode getSnapMode() {
        return snapMode == null ? WorldSelectionResolveUtils.SnapMode.NEAREST_CENTER : snapMode;
    }

    public void setSnapMode(WorldSelectionResolveUtils.SnapMode snapMode) {
        this.snapMode = snapMode == null ? WorldSelectionResolveUtils.SnapMode.NEAREST_CENTER : snapMode;
        markDirty();
    }

    public void setSnapModeString(String mode) {
        setSnapMode(WorldSelectionResolveUtils.SnapMode.fromLegacyOrName(mode));
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
        state.put("snapMode", getSnapMode().name());
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
}
