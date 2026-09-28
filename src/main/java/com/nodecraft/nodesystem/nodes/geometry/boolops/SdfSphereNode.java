package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.SphereSdfData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_sphere",
    displayName = "SDF Sphere",
    description = "Builds a sphere signed-distance-field primitive from center and radius",
    category = "geometry.sdf",
    order = 10
)
public class SdfSphereNode extends AbstractSdfNode {
    @NodeProperty(displayName = "Default Radius", category = "SDF", order = 1)
    private double defaultRadius = 4.0d;

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String OUTPUT_SDF_ID = "output_sdf";

    public SdfSphereNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_sphere");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Sphere center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Sphere radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Sphere signed distance field", NodeDataType.SDF, this));
        addValidAndErrorOutputs("True when center and radius are valid");
    }

    @Override
    public String getDescription() {
        return "Builds a sphere signed-distance-field primitive from center and radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveOptionalPoint(INPUT_CENTER_ID, null);
        if (center == null) {
            writeFailure(isPortConnected(INPUT_CENTER_ID)
                ? "Center input must be a finite point"
                : "Sphere requires a finite center");
            return;
        }

        Double radius = resolvePositiveDouble(INPUT_RADIUS_ID, defaultRadius);
        if (radius == null) {
            writeFailure("Sphere radius must be finite and > 0");
            return;
        }

        SignedDistanceFieldData sdf = new SphereSdfData(center, radius);
        outputValues.put(OUTPUT_SDF_ID, sdf);
        markSuccess();
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_SDF_ID);
        markInvalid(error);
    }
}
