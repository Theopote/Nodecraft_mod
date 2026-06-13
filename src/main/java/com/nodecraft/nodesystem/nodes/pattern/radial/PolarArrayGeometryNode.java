package com.nodecraft.nodesystem.nodes.pattern.radial;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryTransform;
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
    id = "pattern.radial.polar_array_geometry",
    displayName = "Polar Array Geometry",
    description = "Creates repeated geometry copies around a center point and axis",
    category = "pattern.radial",
    order = 3
)
public class PolarArrayGeometryNode extends BaseNode {

    @NodeProperty(displayName = "Include Original", category = "Array", order = 1)
    private boolean includeOriginal = true;

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

    public PolarArrayGeometryNode() {
        super(UUID.randomUUID(), "pattern.radial.polar_array_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to copy", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Array center point", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Rotation axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of rotated copies", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_TOTAL_ANGLE_ID, "Total Angle", "Total angle to distribute copies over in degrees", NodeDataType.DOUBLE, this));

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

        Vector3d center = inputValues.get(INPUT_CENTER_ID) instanceof Vector3d value ? new Vector3d(value) : new Vector3d();
        Vector3d axis = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d value ? new Vector3d(value) : new Vector3d(0.0d, 1.0d, 0.0d);
        int count = Math.max(0, getInputInteger(INPUT_COUNT_ID, 1));
        double totalAngle = getInputDouble(INPUT_TOTAL_ANGLE_ID, 360.0d);
        if (!isFinite(center) || !isFinite(axis) || axis.lengthSquared() <= 1.0e-12d || !Double.isFinite(totalAngle)) {
            writeResult(List.of(), false);
            return;
        }

        axis.normalize();
        double angleStep = count == 0 ? 0.0d : totalAngle / count;
        List<GeometryData> copies = new ArrayList<>(count + (includeOriginal ? 1 : 0));
        if (includeOriginal) {
            copies.add(geometry);
        }
        for (int i = 1; i <= count; i++) {
            Quaterniond quaternion = new Quaterniond(new AxisAngle4d(Math.toRadians(angleStep * i), axis.x, axis.y, axis.z));
            Matrix3d rotation = new Matrix3d().set(quaternion);
            GeometryData copy = GeometryTransform.transformAround(geometry, center, rotation, 1.0d);
            if (copy != null) {
                copies.add(copy);
            }
        }

        writeResult(copies, !copies.isEmpty());
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

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private int getInputInteger(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private void writeResult(List<GeometryData> copies, boolean valid) {
        outputValues.put(OUTPUT_GEOMETRIES_ID, List.copyOf(copies));
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, buildCopyTree(copies));
        outputValues.put(OUTPUT_GEOMETRY_ID, copies.isEmpty() ? null : new CompositeGeometryData(copies));
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
