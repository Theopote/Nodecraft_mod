package com.nodecraft.nodesystem.nodes.spatial.construct;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "spatial.generators.sphere_by_center_radius",
    displayName = "Sphere By Center Radius",
    description = "Constructs sphere geometry from a center point and radius",
    category = "spatial.construct"
)
public class SphereByCenterRadiusNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_SPHERE_ID = "output_sphere";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_DIAMETER_ID = "output_diameter";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SphereByCenterRadiusNode() {
        super(UUID.randomUUID(), "spatial.generators.sphere_by_center_radius");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Sphere center as point, vector, or block coordinate", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Sphere radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_SPHERE_ID, "Sphere", "Constructed sphere geometry", NodeDataType.SPHERE, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved sphere center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_DIAMETER_ID, "Diameter", "Resolved diameter", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a sphere could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs sphere geometry from a center point and radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);

        if (center == null || !(radiusObj instanceof Number radiusNumber)) {
            writeEmptyOutputs();
            return;
        }

        double radius = radiusNumber.doubleValue();
        if (!Double.isFinite(radius) || radius < 0.0d) {
            writeEmptyOutputs();
            return;
        }

        SphereData sphere = new SphereData(center, radius);
        outputValues.put(OUTPUT_SPHERE_ID, sphere);
        outputValues.put(OUTPUT_GEOMETRY_ID, sphere);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_DIAMETER_ID, radius * 2.0d);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SPHERE_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_DIAMETER_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }
}
