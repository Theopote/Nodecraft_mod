package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.GeometryMirror;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PlacementBlockUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.mirror_block_positions",
    displayName = "Mirror Block Positions",
    description = "Mirrors a block position list across a plane and snaps results to the block grid",
    category = "transform.placement",
    order = 7
)
public class MirrorBlockPositionsNode extends AbstractPlacementNode {

    public enum MirrorPlane {
        XY, YZ, XZ
    }

    @NodeProperty(displayName = "Default Plane", category = "Mirror", order = 1)
    private MirrorPlane mirrorPlane = MirrorPlane.XZ;

    private static final String INPUT_BLOCK_POSITIONS_ID = "input_block_positions";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_NORMAL_ID = "input_normal";

    private static final String OUTPUT_BLOCK_POSITIONS_ID = "output_block_positions";
    private static final String OUTPUT_INPUT_COUNT_ID = "output_input_count";
    private static final String OUTPUT_OUTPUT_COUNT_ID = "output_output_count";

    public MirrorBlockPositionsNode() {
        super("transform.placement.mirror_block_positions");

        addInputPort(new BasePort(INPUT_BLOCK_POSITIONS_ID, "Block Positions", "The block positions to mirror", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Mirror plane override", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Point on mirror plane", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Normal vector of mirror plane", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_POSITIONS_ID, "Block Positions", "Mirrored block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INPUT_COUNT_ID, "Input Count", "Number of input block positions", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_OUTPUT_COUNT_ID, "Output Count", "Number of output block positions", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
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
        Object coordinatesObj = inputValues.get(INPUT_BLOCK_POSITIONS_ID);
        if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            writeInvalid("Missing block position list", 0);
            return;
        }

        String listError = PlacementBlockUtils.validateBlockListSize(coordinates);
        if (listError != null) {
            writeInvalid(listError, coordinates.size());
            return;
        }

        PlaneResolution planeResolution = resolvePlane();
        if (planeResolution.error() != null) {
            writeInvalid(planeResolution.error(), coordinates.size());
            return;
        }

        BlockPosList result = new BlockPosList();
        for (BlockPos pos : coordinates) {
            Vector3d mirrored = GeometryMirror.mirrorPoint(BlockSpace.cellCenter(pos), planeResolution.plane());
            BlockPos snapped = PlacementBlockUtils.trySnapCellCenter(mirrored);
            if (snapped == null) {
                writeInvalid("Non-finite mirror result", coordinates.size());
                return;
            }
            result.add(snapped);
        }

        outputValues.put(OUTPUT_BLOCK_POSITIONS_ID, result);
        outputValues.put(OUTPUT_INPUT_COUNT_ID, coordinates.size());
        outputValues.put(OUTPUT_OUTPUT_COUNT_ID, result.size());
        markSuccess();
    }

    private record PlaneResolution(@Nullable PlaneData plane, @Nullable String error) {
    }

    /**
     * Plane XOR (Point+Normal): connected Plane → plane input; both Point+Normal → custom plane;
     * partial Point/Normal → fail; none → property default plane.
     */
    private PlaneResolution resolvePlane() {
        boolean planeConnected = OptionalPortDrive.isConnected(this, INPUT_PLANE_ID);
        boolean pointConnected = OptionalPortDrive.isConnected(this, INPUT_POINT_ID);
        boolean normalConnected = OptionalPortDrive.isConnected(this, INPUT_NORMAL_ID);

        if (planeConnected && (pointConnected || normalConnected)) {
            return new PlaneResolution(null, "Connect Plane or Point+Normal, not both");
        }
        if (pointConnected != normalConnected) {
            return new PlaneResolution(null, "Connect both Point and Normal for a custom mirror plane");
        }

        if (planeConnected) {
            PlaneData plane = OptionalPortDrive.resolveOptionalPlane(this, INPUT_PLANE_ID, null);
            if (plane == null) {
                return new PlaneResolution(null, "Plane connected but invalid");
            }
            return new PlaneResolution(plane, null);
        }

        if (pointConnected) {
            Vector3d point = OptionalPortDrive.resolveOptionalPoint(this, INPUT_POINT_ID, null);
            Vector3d normal = OptionalPortDrive.resolveOptionalVector(this, INPUT_NORMAL_ID, null);
            if (point == null || normal == null) {
                return new PlaneResolution(null, "Point or Normal connected but invalid");
            }
            PlaneData plane = PlaneData.canonical(point, normal);
            if (plane == null) {
                return new PlaneResolution(null, "Mirror plane point/normal must define a valid finite plane");
            }
            return new PlaneResolution(plane, null);
        }

        return new PlaneResolution(switch (mirrorPlane == null ? MirrorPlane.XZ : mirrorPlane) {
            case XY -> PlaneData.XY_PLANE;
            case YZ -> PlaneData.YZ_PLANE;
            case XZ -> PlaneData.XZ_PLANE;
        }, null);
    }

    private void writeInvalid(String error, int inputCount) {
        putEmptyBlockListOutputs(OUTPUT_BLOCK_POSITIONS_ID);
        putIntOutputs(inputCount, OUTPUT_INPUT_COUNT_ID);
        putIntOutputs(0, OUTPUT_OUTPUT_COUNT_ID);
        markInvalid(error);
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
