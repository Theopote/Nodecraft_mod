package com.nodecraft.nodesystem.nodes.geometry.combine;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structural combine of geometry values into one composite container.
 * Not an analytic solid-union; voxel bake merges child blocks with set-union.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.combine.geometry",
    displayName = "Combine Geometry",
    description = "Structural grouping of geometries into a composite. Bake/voxelize merges blocks (set union); not an analytic BRep union or SDF smooth union.",
    category = "geometry.combine",
    order = 0
)
public class CombineGeometryNode extends BaseNode {

    private static final int MIN_INPUT_COUNT = 2;
    private static final int MAX_INPUT_COUNT = 16;
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(
        displayName = "Input Count",
        category = "Settings",
        order = 1,
        description = "Number of geometry inputs to expose."
    )
    private int inputCount = 4;

    public CombineGeometryNode() {
        super(UUID.randomUUID(), "geometry.combine.geometry");
        rebuildInputPorts();
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry",
            "Combined geometry (raw when one leaf, composite when multiple)", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of leaf geometries in the output", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when at least one connected geometry input was combined", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Structural grouping of geometries into a composite. Bake/voxelize merges blocks (set union); not an analytic BRep union or SDF smooth union.";
    }

    @Override
    public String getDisplayName() {
        return "Combine Geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<GeometryData> leaves = new ArrayList<>();

        for (int i = 0; i < inputCount; i++) {
            String portId = inputPortId(i);
            if (!OptionalPortDrive.isConnected(this, portId)) {
                continue;
            }
            Object value = inputValues.get(portId);
            if (!(value instanceof GeometryData geometry)) {
                writeInvalid("Geometry " + (i + 1) + " is connected but invalid");
                return;
            }
            CompositeGeometryData.appendLeaves(leaves, geometry);
        }

        if (leaves.isEmpty()) {
            writeInvalid("No geometry inputs are connected");
            return;
        }

        GeometryData packed = GeometryOutputUtils.packGeometry(leaves);
        if (packed == null) {
            writeInvalid("Unable to combine geometry inputs");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, packed);
        outputValues.put(OUTPUT_COUNT_ID, leaves.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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
