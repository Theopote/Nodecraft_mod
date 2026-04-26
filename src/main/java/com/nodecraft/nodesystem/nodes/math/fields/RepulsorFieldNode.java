package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "math.fields.repulsor_field",
    displayName = "Repulsor Field",
    description = "Inverts a vector field direction (repulsion) with optional strength scaling.",
    category = "math.fields",
    order = 12
)
public class RepulsorFieldNode extends BaseNode {

    @NodeProperty(displayName = "Strength", category = "Repulsor", order = 1)
    private double strength = 1.0d;

    private static final String INPUT_FIELD_ID = "input_field";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String OUTPUT_FIELD_ID = "output_field";

    private final Vector3d tmp = new Vector3d();

    public RepulsorFieldNode() {
        super(UUID.randomUUID(), "math.fields.repulsor_field");

        addInputPort(new BasePort(INPUT_FIELD_ID, "Field", "Input attractor/vector field", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Repulsion scale override", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Repulsor vector field output", NodeDataType.VECTOR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object fieldObj = inputValues.get(INPUT_FIELD_ID);
        if (!(fieldObj instanceof VectorFieldData field)) {
            outputValues.put(OUTPUT_FIELD_ID, null);
            return;
        }

        double effectiveStrength = getInputDouble(INPUT_STRENGTH_ID, strength);
        VectorFieldData repulsor = (point, dest) -> {
            field.sampleVector(point, tmp);
            dest.set(tmp).mul(-effectiveStrength);
        };

        outputValues.put(OUTPUT_FIELD_ID, repulsor);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
