package com.nodecraft.nodesystem.nodes.control.flow;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Conditionally forwards geometry based on a boolean gate.
 */
@NodeInfo(
    id = "control.flow.geometry_gate",
    displayName = "Geometry Gate",
    description = "Passes geometry through only when enabled",
    category = "control.flow"
)
public class GeometryGateNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_ENABLED_ID = "input_enabled";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_ACTIVE_ID = "output_active";

    public GeometryGateNode() {
        super(UUID.randomUUID(), "control.flow.geometry_gate");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_ENABLED_ID, "Enabled", "Whether geometry should pass through", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Forwarded geometry when enabled", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_ACTIVE_ID, "Active", "Whether the gate forwarded geometry", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Passes geometry through only when enabled";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        GeometryData geometry = geometryObj instanceof GeometryData data ? data : null;

        Object enabledObj = inputValues.get(INPUT_ENABLED_ID);
        boolean enabled = !(enabledObj instanceof Boolean value) || value;

        GeometryData outputGeometry = enabled ? geometry : null;

        outputValues.put(OUTPUT_GEOMETRY_ID, outputGeometry);
        outputValues.put(OUTPUT_ACTIVE_ID, outputGeometry != null);
    }
}
