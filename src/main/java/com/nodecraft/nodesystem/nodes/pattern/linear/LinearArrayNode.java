package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryStructureUtils;
import com.nodecraft.nodesystem.util.GeometryTransform;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.linear.linear_array",
    displayName = "Linear Array",
    description = "Creates repeated geometry copies along a direction vector",
    category = "pattern.linear",
    order = 0
)
public class LinearArrayNode extends AbstractPatternLinearNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_DIRECTION_ID = "input_direction";
    private static final String INPUT_DISTANCE_ID = "input_distance";
    private static final String INPUT_COUNT_ID = "input_count";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_GEOMETRY_TREE_ID = "output_geometry_tree";
    private static final String OUTPUT_COUNT_ID = "output_count";

    @NodeProperty(displayName = "Distance", category = "Array", order = 1)
    private double distance = 1.0d;

    @NodeProperty(displayName = "Count", category = "Array", order = 2)
    private int count = 1;

    public LinearArrayNode() {
        super(UUID.randomUUID(), "pattern.linear.linear_array");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to copy", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", "Array direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Distance between consecutive instances", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Total number of emitted instances (including the original at offset 0)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all copies", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_TREE_ID, "Geometry Tree", "One branch per emitted geometry copy", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted geometry copies", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Creates repeated geometry copies along a direction vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeFail("Missing or invalid Geometry");
            return;
        }

        Vector3d direction = resolveOptionalDirection(this, INPUT_DIRECTION_ID, new Vector3d(1.0d, 0.0d, 0.0d));
        if (direction == null) {
            writeFail("Direction connected but invalid or zero");
            return;
        }

        Double resolvedDistance = OptionalPortDrive.resolveOptionalDouble(this, INPUT_DISTANCE_ID, distance);
        if (resolvedDistance == null) {
            writeFail("Distance connected but invalid");
            return;
        }

        Integer resolvedCount = OptionalPortDrive.resolveOptionalInteger(this, INPUT_COUNT_ID, count);
        if (resolvedCount == null) {
            writeFail("Count connected but invalid");
            return;
        }
        if (resolvedCount <= 0) {
            writeFail("Count must be >= 1");
            return;
        }
        if (resolvedCount > 1 && !(resolvedDistance > 0.0d)) {
            writeFail("Distance must be > 0");
            return;
        }
        if (resolvedCount > GenerationLimits.MAX_GEOMETRY_INSTANCES) {
            writeFail("Count exceeds MAX_GEOMETRY_INSTANCES");
            return;
        }

        long maxInstances = GenerationLimits.MAX_GEOMETRY_INSTANCES;
        long sourceLeaves = GeometryStructureUtils.countLeavesBounded(geometry, maxInstances);
        if (sourceLeaves > maxInstances
            || sourceLeaves * (long) resolvedCount > maxInstances) {
            writeFail("Array workload exceeds limit (source leaves × Count > MAX_GEOMETRY_INSTANCES)");
            return;
        }

        Vector3d step = new Vector3d(direction).mul(resolvedDistance);
        List<GeometryData> copies = new ArrayList<>(resolvedCount);
        for (int i = 0; i < resolvedCount; i++) {
            if (i == 0) {
                copies.add(geometry);
                continue;
            }
            GeometryData copy = GeometryTransform.transform(geometry, new Vector3d(step).mul(i), 0.0d, 0.0d, 0.0d, 1.0d);
            if (copy == null) {
                writeFail("Geometry copy transform failed");
                return;
            }
            copies.add(copy);
        }

        writeSuccess(copies);
    }

    private void writeFail(String error) {
        markInvalid(error);
        putNullOutputs(OUTPUT_GEOMETRY_ID);
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, new DataTreeData(List.of()));
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    private void writeSuccess(List<GeometryData> copies) {
        markSuccess();
        if (copies.isEmpty()) {
            putNullOutputs(OUTPUT_GEOMETRY_ID);
        } else if (copies.size() == 1) {
            outputValues.put(OUTPUT_GEOMETRY_ID, copies.getFirst());
        } else {
            outputValues.put(OUTPUT_GEOMETRY_ID, new CompositeGeometryData(copies));
        }
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, buildCopyTree(copies));
        putIntOutputs(copies.size(), OUTPUT_COUNT_ID);
    }

    private DataTreeData buildCopyTree(List<GeometryData> copies) {
        List<DataTreeData.Branch> branches = new ArrayList<>(copies.size());
        for (int i = 0; i < copies.size(); i++) {
            branches.add(new DataTreeData.Branch(List.of(i), List.of(copies.get(i))));
        }
        return new DataTreeData(branches);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("distance", distance);
        state.put("count", count);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("distance") instanceof Number value) {
            distance = value.doubleValue();
        }
        if (map.get("count") instanceof Number value) {
            count = value.intValue();
        }
    }
}
