package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unions multiple geometry values into one composite geometry object.
 * This is a structural geometry union used by the v1 modeling pipeline.
 */
@NodeInfo(
    id = "geometry.boolean.union",
    displayName = "Combine Geometry",
    description = "Merges multiple geometry inputs into one composite value (voxel union when baked; not SDF smooth union)",
    category = "geometry.boolean",
    order = 2
)
public class GeometryUnionNode extends BaseNode {

    private static final int MIN_INPUT_COUNT = 2;
    private static final int MAX_INPUT_COUNT = 16;
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(
        displayName = "Input Count",
        category = "Settings",
        order = 1,
        description = "Number of geometry inputs to expose."
    )
    private int inputCount = 4;

    public GeometryUnionNode() {
        super(UUID.randomUUID(), "geometry.boolean.union");
        rebuildInputPorts();
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry union result", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of geometry inputs that were merged", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least one geometry input was resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Merges multiple geometry inputs into one composite value (voxel union when baked; not SDF smooth union)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<GeometryData> geometries = new ArrayList<>();

        for (int i = 0; i < inputCount; i++) {
            Object value = inputValues.get(inputPortId(i));
            if (value instanceof GeometryData geometry) {
                geometries.add(geometry);
            }
        }

        CompositeGeometryData result = geometries.isEmpty() ? null : new CompositeGeometryData(geometries);
        outputValues.put(OUTPUT_GEOMETRY_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, geometries.size());
        outputValues.put(OUTPUT_VALID_ID, !geometries.isEmpty());
    }

    private void rebuildInputPorts() {
        inputPorts.clear();
        for (int i = 0; i < inputCount; i++) {
            addInputPort(new BasePort(
                inputPortId(i),
                "Geometry " + (i + 1),
                "Geometry input " + (i + 1),
                NodeDataType.GEOMETRY,
                this
            ));
        }
    }

    private String inputPortId(int index) {
        return "input_geometry_" + index;
    }

    public int getInputCount() {
        return inputCount;
    }

    public void setInputCount(int inputCount) {
        int resolved = Math.max(MIN_INPUT_COUNT, Math.min(MAX_INPUT_COUNT, inputCount));
        if (this.inputCount != resolved) {
            this.inputCount = resolved;
            rebuildInputPorts();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("inputCount", inputCount);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("inputCount") instanceof Number value) {
            setInputCount(value.intValue());
        }
    }
}
