package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryTransform;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "pattern.grid.grid_array_geometry",
    displayName = "Grid Array Geometry",
    description = "Creates rectangular or box arrays of geometry using X, Y, and optional Z directions",
    category = "pattern.grid",
    order = 1
)
public class GridArrayGeometryNode extends BaseNode {

    private static final double EPS = 1.0e-12d;

    @NodeProperty(displayName = "Include Original", category = "Array", order = 1)
    private boolean includeOriginal = true;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_X_DIRECTION_ID = "input_x_direction";
    private static final String INPUT_X_DISTANCE_ID = "input_x_distance";
    private static final String INPUT_X_COUNT_ID = "input_x_count";
    private static final String INPUT_Y_DIRECTION_ID = "input_y_direction";
    private static final String INPUT_Y_DISTANCE_ID = "input_y_distance";
    private static final String INPUT_Y_COUNT_ID = "input_y_count";
    private static final String INPUT_Z_DIRECTION_ID = "input_z_direction";
    private static final String INPUT_Z_DISTANCE_ID = "input_z_distance";
    private static final String INPUT_Z_COUNT_ID = "input_z_count";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_GEOMETRIES_ID = "output_geometries";
    private static final String OUTPUT_OFFSETS_ID = "output_offsets";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public GridArrayGeometryNode() {
        super(UUID.randomUUID(), "pattern.grid.grid_array_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to copy", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_X_DIRECTION_ID, "X Direction", "First array direction", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_X_DISTANCE_ID, "X Distance", "Spacing along X Direction", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_COUNT_ID, "X Count", "Number of positions along X Direction", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Y_DIRECTION_ID, "Y Direction", "Second array direction", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Y_DISTANCE_ID, "Y Distance", "Spacing along Y Direction", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_COUNT_ID, "Y Count", "Number of positions along Y Direction", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Z_DIRECTION_ID, "Z Direction", "Optional third array direction for box arrays", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Z_DISTANCE_ID, "Z Distance", "Spacing along Z Direction", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Z_COUNT_ID, "Z Count", "Number of positions along Z Direction. Use 1 for rectangular arrays.", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all grid copies", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRIES_ID, "Geometries", "List of copied geometry values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_OFFSETS_ID, "Offsets", "Offset vectors used for each copy", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted geometry copies", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the grid array was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates rectangular or box arrays of geometry using X, Y, and optional Z directions";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(List.of(), List.of(), false);
            return;
        }

        Vector3d xStep = resolveStep(INPUT_X_DIRECTION_ID, INPUT_X_DISTANCE_ID, new Vector3d(1.0d, 0.0d, 0.0d), 1.0d);
        Vector3d yStep = resolveStep(INPUT_Y_DIRECTION_ID, INPUT_Y_DISTANCE_ID, new Vector3d(0.0d, 1.0d, 0.0d), 1.0d);
        Vector3d zStep = resolveStep(INPUT_Z_DIRECTION_ID, INPUT_Z_DISTANCE_ID, new Vector3d(0.0d, 0.0d, 1.0d), 1.0d);
        int xCount = Math.max(1, getInputInt(INPUT_X_COUNT_ID, 1));
        int yCount = Math.max(1, getInputInt(INPUT_Y_COUNT_ID, 1));
        int zCount = Math.max(1, getInputInt(INPUT_Z_COUNT_ID, 1));
        if (xStep == null || yStep == null || zStep == null) {
            writeResult(List.of(), List.of(), false);
            return;
        }

        int capacity = xCount * yCount * zCount;
        List<GeometryData> copies = new ArrayList<>(capacity);
        List<Vector3d> offsets = new ArrayList<>(capacity);

        for (int z = 0; z < zCount; z++) {
            for (int y = 0; y < yCount; y++) {
                for (int x = 0; x < xCount; x++) {
                    if (!includeOriginal && x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    Vector3d offset = new Vector3d(xStep).mul(x)
                        .add(new Vector3d(yStep).mul(y))
                        .add(new Vector3d(zStep).mul(z));
                    GeometryData copy = (x == 0 && y == 0 && z == 0)
                        ? geometry
                        : GeometryTransform.transform(geometry, offset, 0.0d, 0.0d, 0.0d, 1.0d);
                    if (copy != null) {
                        copies.add(copy);
                        offsets.add(offset);
                    }
                }
            }
        }

        writeResult(copies, offsets, !copies.isEmpty());
    }

    private @Nullable Vector3d resolveStep(String directionId, String distanceId, Vector3d fallbackDirection, double fallbackDistance) {
        Vector3d direction = inputValues.get(directionId) instanceof Vector3d value ? new Vector3d(value) : new Vector3d(fallbackDirection);
        double distance = inputValues.get(distanceId) instanceof Number number ? number.doubleValue() : fallbackDistance;
        if (!isFinite(direction) || direction.lengthSquared() <= EPS || !Double.isFinite(distance)) {
            return null;
        }
        return direction.normalize().mul(distance);
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private void writeResult(List<GeometryData> copies, List<Vector3d> offsets, boolean valid) {
        outputValues.put(OUTPUT_GEOMETRIES_ID, List.copyOf(copies));
        outputValues.put(OUTPUT_GEOMETRY_ID, copies.isEmpty() ? null : new CompositeGeometryData(copies));
        outputValues.put(OUTPUT_OFFSETS_ID, List.copyOf(offsets));
        outputValues.put(OUTPUT_COUNT_ID, copies.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    public boolean isIncludeOriginal() {
        return includeOriginal;
    }

    public void setIncludeOriginal(boolean includeOriginal) {
        if (this.includeOriginal != includeOriginal) {
            this.includeOriginal = includeOriginal;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of("includeOriginal", includeOriginal);
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("includeOriginal") instanceof Boolean value) {
            setIncludeOriginal(value);
        }
    }
}
