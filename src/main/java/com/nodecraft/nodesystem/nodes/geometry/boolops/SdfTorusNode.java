package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.TorusSdfData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_torus",
    displayName = "SDF Torus",
    description = "Builds a ring-torus signed-distance-field primitive around the Y axis from center and radii",
    category = "geometry.sdf",
    order = 13
)
public class SdfTorusNode extends AbstractSdfNode {
    @NodeProperty(displayName = "Default Major Radius", category = "SDF", order = 1)
    private double defaultMajorRadius = 6.0d;
    @NodeProperty(displayName = "Default Minor Radius", category = "SDF", order = 2)
    private double defaultMinorRadius = 2.0d;

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_MAJOR_RADIUS_ID = "input_major_radius";
    private static final String INPUT_MINOR_RADIUS_ID = "input_minor_radius";
    private static final String OUTPUT_SDF_ID = "output_sdf";

    public SdfTorusNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_torus");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Torus center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_MAJOR_RADIUS_ID, "Major Radius", "Distance from center to tube center", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MINOR_RADIUS_ID, "Minor Radius", "Tube radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Torus signed distance field", NodeDataType.SDF, this));
        addValidAndErrorOutputs("True when center and ring-torus radii are valid");
    }

    @Override
    public String getDescription() {
        return "Builds a ring-torus signed-distance-field primitive around the Y axis from center and radii";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveOptionalPoint(INPUT_CENTER_ID, null);
        if (center == null) {
            writeFailure(isPortConnected(INPUT_CENTER_ID)
                ? "Center input must be a finite point"
                : "Torus requires a finite center");
            return;
        }

        Double major = resolvePositiveDouble(INPUT_MAJOR_RADIUS_ID, defaultMajorRadius);
        Double minor = resolvePositiveDouble(INPUT_MINOR_RADIUS_ID, defaultMinorRadius);
        if (major == null || minor == null) {
            writeFailure("Torus radii must be finite and > 0");
            return;
        }
        if (!(minor < major)) {
            writeFailure("Torus requires 0 < minor radius < major radius (ring torus)");
            return;
        }

        SignedDistanceFieldData sdf = new TorusSdfData(center, major, minor);
        outputValues.put(OUTPUT_SDF_ID, sdf);
        markSuccess();
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_SDF_ID);
        markInvalid(error);
    }
}
