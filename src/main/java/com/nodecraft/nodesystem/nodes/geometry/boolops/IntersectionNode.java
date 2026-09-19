package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Creates a voxel-evaluated intersection geometry value.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.intersection",
    displayName = "Intersection",
    description = "Creates a voxel-evaluated intersection geometry value from two geometry inputs",
    category = "geometry.boolean",
    order = 4
)
public class IntersectionNode extends BaseNode {

    private static final String INPUT_LEFT_ID = "input_left";
    private static final String INPUT_RIGHT_ID = "input_right";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public IntersectionNode() {
        super(UUID.randomUUID(), "geometry.boolean.intersection");

        addInputPort(new BasePort(INPUT_LEFT_ID, "Left Geometry", "First geometry operand", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_RIGHT_ID, "Right Geometry", "Second geometry operand", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Voxel-evaluated intersection geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when both geometry inputs are available", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates a voxel-evaluated intersection geometry value from two geometry inputs";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object leftObj = inputValues.get(INPUT_LEFT_ID);
        Object rightObj = inputValues.get(INPUT_RIGHT_ID);

        GeometryData result = null;
        boolean valid = false;

        if (leftObj instanceof GeometryData leftGeometry && rightObj instanceof GeometryData rightGeometry) {
            result = new IntersectionGeometryData(leftGeometry, rightGeometry);
            valid = true;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, result);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}
