package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Creates a voxel-evaluated difference geometry value.
 */
@NodeInfo(
    id = "geometry.boolean.difference",
    displayName = "Difference",
    description = "Creates a voxel-evaluated difference geometry value by subtracting cutter geometry from a base geometry",
    category = "geometry.boolean",
    order = 3
)
public class DifferenceNode extends BaseNode {

    private static final String INPUT_BASE_ID = "input_base";
    private static final String INPUT_CUTTER_ID = "input_cutter";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DifferenceNode() {
        super(UUID.randomUUID(), "geometry.boolean.difference");

        addInputPort(new BasePort(INPUT_BASE_ID, "Base Geometry", "Geometry to subtract from", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CUTTER_ID, "Cutter Geometry", "Geometry that will be removed from the base", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Voxel-evaluated difference geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when both base and cutter geometry are available", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates a voxel-evaluated difference geometry value by subtracting cutter geometry from a base geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object baseObj = inputValues.get(INPUT_BASE_ID);
        Object cutterObj = inputValues.get(INPUT_CUTTER_ID);

        GeometryData result = null;
        boolean valid = false;

        if (baseObj instanceof GeometryData baseGeometry && cutterObj instanceof GeometryData cutterGeometry) {
            result = new DifferenceGeometryData(baseGeometry, cutterGeometry);
            valid = true;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, result);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}
