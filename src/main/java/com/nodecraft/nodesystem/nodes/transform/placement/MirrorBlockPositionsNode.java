package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.GeometryMirror;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.mirror_block_positions",
    displayName = "Mirror Block Positions",
    description = "Mirrors a block position list across a plane and snaps results to the block grid",
    category = "transform.placement",
    order = 7
)
public class MirrorBlockPositionsNode extends BaseNode {

    public enum MirrorPlane {
        XY, YZ, XZ
    }

    @NodeProperty(displayName = "Default Plane", category = "Mirror", order = 1)
    private MirrorPlane mirrorPlane = MirrorPlane.XZ;

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_NORMAL_ID = "input_normal";

    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_INPUT_COUNT_ID = "output_input_count";
    private static final String OUTPUT_OUTPUT_COUNT_ID = "output_output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MirrorBlockPositionsNode() {
        super(UUID.randomUUID(), "transform.placement.mirror_block_positions");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Block Positions", "The block positions to mirror", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Mirror plane override", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Point on mirror plane", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Normal vector of mirror plane", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Block Positions", "Mirrored block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INPUT_COUNT_ID, "Input Count", "Number of input block positions", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_OUTPUT_COUNT_ID, "Output Count", "Number of output block positions", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the block position mirror succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Mirrors a block position list across a plane and snaps results to the block grid";
    }

    @Override
    public String getDisplayName() {
        return "Mirror Block Positions";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            writeResult(new BlockPosList(), 0, false);
            return;
        }

        PlaneData plane = resolvePlane();
        if (plane == null) {
            writeResult(new BlockPosList(), coordinates.size(), false);
            return;
        }

        BlockPosList result = new BlockPosList();
        for (BlockPos pos : coordinates) {
            Vector3d mirrored = GeometryMirror.mirrorPoint(BlockSpace.cellCenter(pos), plane);
            result.add(BlockSpace.snapCellCenter(mirrored));
        }

        writeResult(result, coordinates.size(), true);
    }

    /**
     * Plane resolution: connected Plane → Point+Normal custom path → Default Plane property.
     */
    private @Nullable PlaneData resolvePlane() {
        if (OptionalPortDrive.isConnected(this, INPUT_PLANE_ID)) {
            return OptionalPortDrive.resolveOptionalPlane(this, INPUT_PLANE_ID, null);
        }

        boolean pointConnected = OptionalPortDrive.isConnected(this, INPUT_POINT_ID);
        boolean normalConnected = OptionalPortDrive.isConnected(this, INPUT_NORMAL_ID);
        if (pointConnected || normalConnected) {
            if (!pointConnected || !normalConnected) {
                return null;
            }
            Vector3d point = OptionalPortDrive.resolveOptionalPoint(this, INPUT_POINT_ID, null);
            Vector3d normal = OptionalPortDrive.resolveOptionalVector(this, INPUT_NORMAL_ID, null);
            if (point == null || normal == null || !VectorUtils.isNonZero(normal)) {
                return null;
            }
            return PlaneData.canonical(point, normal);
        }

        return switch (mirrorPlane == null ? MirrorPlane.XZ : mirrorPlane) {
            case XY -> PlaneData.XY_PLANE;
            case YZ -> PlaneData.YZ_PLANE;
            case XZ -> PlaneData.XZ_PLANE;
        };
    }

    private void writeResult(BlockPosList result, int inputCount, boolean valid) {
        outputValues.put(OUTPUT_COORDINATES_ID, result);
        outputValues.put(OUTPUT_INPUT_COUNT_ID, inputCount);
        outputValues.put(OUTPUT_OUTPUT_COUNT_ID, result.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    public MirrorPlane getMirrorPlane() {
        return mirrorPlane;
    }

    public void setMirrorPlane(MirrorPlane plane) {
        if (plane != null && this.mirrorPlane != plane) {
            this.mirrorPlane = plane;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("mirrorPlane", mirrorPlane.name());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        if (stateMap.get("mirrorPlane") instanceof String planeName) {
            try {
                setMirrorPlane(MirrorPlane.valueOf(planeName));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
