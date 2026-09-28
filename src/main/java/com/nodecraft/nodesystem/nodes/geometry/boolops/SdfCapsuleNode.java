package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CapsuleSdfData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_capsule",
    displayName = "SDF Capsule",
    description = "Builds a capsule signed-distance-field primitive from segment endpoints and radius",
    category = "geometry.sdf",
    order = 12
)
public class SdfCapsuleNode extends AbstractSdfNode {
    @NodeProperty(displayName = "Default Radius", category = "SDF", order = 1)
    private double defaultRadius = 2.0d;

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String OUTPUT_SDF_ID = "output_sdf";

    public SdfCapsuleNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_capsule");
        addInputPort(new BasePort(INPUT_START_ID, "Start", "Segment start point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "Segment end point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Capsule radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Capsule signed distance field", NodeDataType.SDF, this));
        addValidAndErrorOutputs("True when endpoints and radius are valid");
    }

    @Override
    public String getDescription() {
        return "Builds a capsule signed-distance-field primitive from segment endpoints and radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d start = resolveOptionalPoint(INPUT_START_ID, null);
        Vector3d end = resolveOptionalPoint(INPUT_END_ID, null);
        if (start == null || end == null) {
            writeFailure("Capsule requires finite axis endpoints");
            return;
        }

        Double radius = resolvePositiveDouble(INPUT_RADIUS_ID, defaultRadius);
        if (radius == null) {
            writeFailure("Capsule radius must be finite and > 0");
            return;
        }

        String axisError = PrimitiveGeometryValidator.validateCapsule(start, end, radius);
        if (axisError != null) {
            writeFailure(axisError);
            return;
        }

        SignedDistanceFieldData sdf = new CapsuleSdfData(start, end, radius);
        outputValues.put(OUTPUT_SDF_ID, sdf);
        markSuccess();
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_SDF_ID);
        markInvalid(error);
    }
}
