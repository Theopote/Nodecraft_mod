package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "transform.basic_transforms.mirror_coordinates",
    displayName = "Mirror Coordinates",
    description = "Mirrors a block coordinate list across a plane and snaps results to the block grid",
    category = "transform.basic_transforms",
    order = 4
)
public class MirrorCoordinatesNode extends BaseNode {

    public enum MirrorPlane {
        XY, YZ, XZ, CUSTOM
    }

    public enum RoundingMode {
        ROUND, FLOOR, CEIL
    }

    @NodeProperty(displayName = "Default Plane", category = "Mirror", order = 1)
    private MirrorPlane mirrorPlane = MirrorPlane.XZ;

    @NodeProperty(displayName = "Rounding Mode", category = "Coordinate Transform", order = 2)
    private RoundingMode roundingMode = RoundingMode.ROUND;

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_NORMAL_ID = "input_normal";

    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_INPUT_COUNT_ID = "output_input_count";
    private static final String OUTPUT_OUTPUT_COUNT_ID = "output_output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MirrorCoordinatesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.mirror_coordinates");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "The coordinates to mirror", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Mirror plane override", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Point on mirror plane", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Normal vector of mirror plane", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", "Mirrored coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INPUT_COUNT_ID, "Input Count", "Number of input coordinates", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_OUTPUT_COUNT_ID, "Output Count", "Number of output coordinates", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the coordinate mirror succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Mirrors a block coordinate list across a plane and snaps results to the block grid";
    }

    @Override
    public String getDisplayName() {
        return "Mirror Coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            writeResult(new BlockPosList(), 0, false);
            return;
        }

        PlaneData plane = resolvePlane();
        if (plane == null || !isUsableVector(plane.getNormal())) {
            writeResult(new BlockPosList(), coordinates.size(), false);
            return;
        }

        BlockPosList result = new BlockPosList();
        for (BlockPos pos : coordinates) {
            Vector3d mirrored = mirrorPoint(new Vector3d(pos.getX(), pos.getY(), pos.getZ()), plane);
            result.add(new BlockPos(roundToBlock(mirrored.x), roundToBlock(mirrored.y), roundToBlock(mirrored.z)));
        }

        writeResult(result, coordinates.size(), true);
    }

    private PlaneData resolvePlane() {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (planeObj instanceof PlaneData plane) {
            return plane;
        }

        Object pointObj = inputValues.get(INPUT_POINT_ID);
        Object normalObj = inputValues.get(INPUT_NORMAL_ID);
        if (pointObj instanceof BlockPos point && normalObj instanceof Vector3d normalInput) {
            Vector3d normal = new Vector3d(normalInput);
            if (!isUsableVector(normal)) {
                return null;
            }
            normal.normalize();
            return new PlaneData(new Vector3d(point.getX(), point.getY(), point.getZ()), normal);
        }

        return switch (mirrorPlane == null ? MirrorPlane.XZ : mirrorPlane) {
            case XY -> new PlaneData(new Vector3d(), new Vector3d(0.0d, 0.0d, 1.0d));
            case YZ -> new PlaneData(new Vector3d(), new Vector3d(1.0d, 0.0d, 0.0d));
            case XZ, CUSTOM -> new PlaneData(new Vector3d(), new Vector3d(0.0d, 1.0d, 0.0d));
        };
    }

    private Vector3d mirrorPoint(Vector3d point, PlaneData plane) {
        Vector3d normal = new Vector3d(plane.getNormal());
        if (normal.lengthSquared() > 1.0e-12d) {
            normal.normalize();
        }
        double distance = plane.signedDistanceTo(point);
        return new Vector3d(point).sub(normal.mul(2.0d * distance));
    }

    private int roundToBlock(double value) {
        RoundingMode mode = roundingMode == null ? RoundingMode.ROUND : roundingMode;
        return switch (mode) {
            case FLOOR -> (int) Math.floor(value);
            case CEIL -> (int) Math.ceil(value);
            case ROUND -> (int) Math.round(value);
        };
    }

    private boolean isUsableVector(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z)
            && vector.lengthSquared() > 1.0e-12d;
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

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    public void setRoundingMode(RoundingMode roundingMode) {
        if (roundingMode != null && this.roundingMode != roundingMode) {
            this.roundingMode = roundingMode;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("mirrorPlane", mirrorPlane.name());
        state.put("roundingMode", roundingMode.name());
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
        if (stateMap.get("roundingMode") instanceof String roundingName) {
            try {
                setRoundingMode(RoundingMode.valueOf(roundingName));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
