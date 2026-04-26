package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "math.fields.vector_constant",
    displayName = "Vector Field Constant",
    description = "Builds a vector field that returns a constant vector everywhere.",
    category = "math.fields",
    order = 5
)
public class VectorFieldConstantNode extends BaseNode {

    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";
    private static final String OUTPUT_FIELD_ID = "output_field";

    public VectorFieldConstantNode() {
        super(UUID.randomUUID(), "math.fields.vector_constant");

        addInputPort(new BasePort(INPUT_X_ID, "X", "Constant vector X", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Constant vector Y", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Constant vector Z", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Vector field F(p) = v", NodeDataType.VECTOR_FIELD, this));
    }

    @Override
    public String getDescription() {
        return "Builds a vector field that returns a constant vector everywhere.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Field Constant";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double x = getInputDouble(INPUT_X_ID, 0.0d);
        double y = getInputDouble(INPUT_Y_ID, 0.0d);
        double z = getInputDouble(INPUT_Z_ID, 0.0d);

        VectorFieldData field = (point, dest) -> dest.set(x, y, z);
        outputValues.put(OUTPUT_FIELD_ID, field);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
