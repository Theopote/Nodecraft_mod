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
 * Forwards a unified geometry object without changing it.
 */
@NodeInfo(
    id = "control.flow.geometry_passthrough",
    displayName = "Geometry Passthrough",
    description = "Forwards one geometry input to a geometry output",
    category = "control.flow"
)
public class GeometryPassthroughNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_HAS_GEOMETRY_ID = "output_has_geometry";

    public GeometryPassthroughNode() {
        super(UUID.randomUUID(), "control.flow.geometry_passthrough");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Forwarded geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_HAS_GEOMETRY_ID, "Has Geometry", "Whether the input contained geometry", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Forwards one geometry input to a geometry output";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        GeometryData geometry = geometryObj instanceof GeometryData data ? data : null;

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_HAS_GEOMETRY_ID, geometry != null);
    }
}
