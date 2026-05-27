package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.TorusSdfData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_torus",
    displayName = "SDF Torus",
    description = "Builds a torus signed-distance-field primitive around the Y axis from center and radii",
    category = "geometry.boolean",
    order = 13
)
public class SdfTorusNode extends BaseNode {
    @NodeProperty(displayName = "Default Major Radius", category = "SDF", order = 1)
    private double defaultMajorRadius = 6.0d;
    @NodeProperty(displayName = "Default Minor Radius", category = "SDF", order = 2)
    private double defaultMinorRadius = 2.0d;

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_MAJOR_RADIUS_ID = "input_major_radius";
    private static final String INPUT_MINOR_RADIUS_ID = "input_minor_radius";

    private static final String OUTPUT_SDF_ID = "output_sdf";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SdfTorusNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_torus");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Torus center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_MAJOR_RADIUS_ID, "Major Radius", "Distance from center to tube center", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MINOR_RADIUS_ID, "Minor Radius", "Tube radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Torus signed distance field", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when center and radii are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds a torus signed-distance-field primitive around the Y axis from center and radii";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        double major = getInputDouble(INPUT_MAJOR_RADIUS_ID, defaultMajorRadius);
        double minor = getInputDouble(INPUT_MINOR_RADIUS_ID, defaultMinorRadius);
        if (center == null || major <= 0.0d || minor <= 0.0d) {
            outputValues.put(OUTPUT_SDF_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        SignedDistanceFieldData sdf = new TorusSdfData(center, major, minor);
        outputValues.put(OUTPUT_SDF_ID, sdf);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d resolvePoint(Object value) {
        return SpatialValueResolver.resolveVector3d(value);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
