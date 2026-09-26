package com.nodecraft.nodesystem.nodes.pattern.radial;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryTransform;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.radial.polar_array",
    displayName = "Polar Array",
    description = "Creates repeated geometry copies around a center point and axis",
    category = "pattern.radial",
    order = 0
)
public class PolarArrayNode extends BaseNode {

    private static final double ANGLE_EPS = 1.0e-9d;

    @NodeProperty(
        displayName = "Include End",
        category = "Array",
        order = 1,
        description = "When true and Count >= 2, the last instance sits at Total Angle. Ignored for exact full-circle angles (multiple of 360°) so the start is not duplicated."
    )
    private boolean includeEnd = false;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_TOTAL_ANGLE_ID = "input_total_angle";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_GEOMETRIES_ID = "output_geometries";
    private static final String OUTPUT_GEOMETRY_TREE_ID = "output_geometry_tree";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PolarArrayNode() {
        super(UUID.randomUUID(), "pattern.radial.polar_array");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to copy", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Array center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Rotation axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Total number of emitted instances around the center", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_TOTAL_ANGLE_ID, "Total Angle", "Total angle span in degrees. Full circles (multiple of 360°) never emit a duplicate at 360°.", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all copies", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRIES_ID, "Geometries", "List of copied geometry values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_TREE_ID, "Geometry Tree", "One branch per emitted geometry copy", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted geometry copies", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the array was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates repeated geometry copies around a center point and axis";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(List.of(), false);
            return;
        }

        Vector3d center = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_CENTER_ID));
        if (center == null) {
            center = new Vector3d();
        }
        Vector3d axis = SpatialValueResolver.resolveVector(inputValues.get(INPUT_AXIS_ID));
        if (axis == null) {
            axis = new Vector3d(0.0d, 1.0d, 0.0d);
        }
        int count = GenerationLimits.clampGeometryInstanceCount(getInputInteger(INPUT_COUNT_ID, 1));
        double totalAngle = getInputDouble(INPUT_TOTAL_ANGLE_ID, 360.0d);
        if (count == 0 || !isFinite(center) || !isFinite(axis) || axis.lengthSquared() <= 1.0e-12d || !Double.isFinite(totalAngle)) {
            writeResult(List.of(), false);
            return;
        }

        axis.normalize();
        boolean fullCircle = isFullCircle(totalAngle);
        boolean useInclusiveEnd = includeEnd && !fullCircle && count >= 2;
        List<GeometryData> copies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double degrees = useInclusiveEnd
                ? totalAngle * i / (double) (count - 1)
                : totalAngle * i / (double) count;
            if (Math.abs(degrees) <= ANGLE_EPS) {
                copies.add(geometry);
                continue;
            }
            Quaterniond quaternion = new Quaterniond(new AxisAngle4d(Math.toRadians(degrees), axis.x, axis.y, axis.z));
            Matrix3d rotation = new Matrix3d().set(quaternion);
            GeometryData copy = GeometryTransform.transformAround(geometry, center, rotation, 1.0d);
            if (copy != null) {
                copies.add(copy);
            }
        }

        writeResult(copies, !copies.isEmpty());
    }

    static boolean isFullCircle(double totalAngleDegrees) {
        if (!Double.isFinite(totalAngleDegrees) || Math.abs(totalAngleDegrees) <= ANGLE_EPS) {
            return false;
        }
        double mod = Math.abs(totalAngleDegrees) % 360.0d;
        return mod <= ANGLE_EPS || Math.abs(mod - 360.0d) <= ANGLE_EPS;
    }

    public boolean isIncludeEnd() {
        return includeEnd;
    }

    public void setIncludeEnd(boolean includeEnd) {
        if (this.includeEnd != includeEnd) {
            this.includeEnd = includeEnd;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of("includeEnd", includeEnd);
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("includeEnd") instanceof Boolean value) {
            setIncludeEnd(value);
        }
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private int getInputInteger(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Integer i ? i : fallback;
    }

    private boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private void writeResult(List<GeometryData> copies, boolean valid) {
        outputValues.put(OUTPUT_GEOMETRIES_ID, List.copyOf(copies));
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, buildCopyTree(copies));
        if (copies.isEmpty()) {
            outputValues.put(OUTPUT_GEOMETRY_ID, null);
        } else if (copies.size() == 1) {
            outputValues.put(OUTPUT_GEOMETRY_ID, copies.getFirst());
        } else {
            outputValues.put(OUTPUT_GEOMETRY_ID, new CompositeGeometryData(copies));
        }
        outputValues.put(OUTPUT_COUNT_ID, copies.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private DataTreeData buildCopyTree(List<GeometryData> copies) {
        List<DataTreeData.Branch> branches = new ArrayList<>(copies.size());
        for (int i = 0; i < copies.size(); i++) {
            branches.add(new DataTreeData.Branch(List.of(i), List.of(copies.get(i))));
        }
        return new DataTreeData(branches);
    }
}
