package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
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
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.linear.linear_array_geometry",
    displayName = "Linear Array Geometry",
    description = "Creates repeated geometry copies along a direction vector",
    category = "pattern.linear",
    order = 5
)
public class LinearArrayGeometryNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_DIRECTION_ID = "input_direction";
    private static final String INPUT_DISTANCE_ID = "input_distance";
    private static final String INPUT_COUNT_ID = "input_count";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_GEOMETRIES_ID = "output_geometries";
    private static final String OUTPUT_GEOMETRY_TREE_ID = "output_geometry_tree";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public LinearArrayGeometryNode() {
        super(UUID.randomUUID(), "pattern.linear.linear_array_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to copy", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", "Array direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Distance between consecutive instances", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Total number of emitted instances (including the original at offset 0)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all copies", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRIES_ID, "Geometries", "List of copied geometry values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_TREE_ID, "Geometry Tree", "One branch per emitted geometry copy", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted geometry copies", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the array was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates repeated geometry copies along a direction vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(List.of(), false);
            return;
        }

        Vector3d direction = SpatialValueResolver.resolveVector(inputValues.get(INPUT_DIRECTION_ID));
        if (direction == null) {
            direction = new Vector3d(1.0d, 0.0d, 0.0d);
        }
        double distance = getInputDouble(INPUT_DISTANCE_ID, 1.0d);
        int count = GenerationLimits.clampGeometryInstanceCount(getInputInteger(INPUT_COUNT_ID, 1));
        if (count == 0 || !isFinite(direction) || direction.lengthSquared() <= 1.0e-12d || !Double.isFinite(distance)) {
            writeResult(List.of(), false);
            return;
        }

        direction.normalize().mul(distance);
        List<GeometryData> copies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (i == 0) {
                copies.add(geometry);
                continue;
            }
            GeometryData copy = GeometryTransform.transform(geometry, new Vector3d(direction).mul(i), 0.0d, 0.0d, 0.0d, 1.0d);
            if (copy != null) {
                copies.add(copy);
            }
        }

        writeResult(copies, !copies.isEmpty());
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
