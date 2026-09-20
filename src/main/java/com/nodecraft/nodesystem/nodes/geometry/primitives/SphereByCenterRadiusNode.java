package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.sphere",
    displayName = "Sphere By Center Radius",
    description = "Constructs sphere geometry from a center point and radius",
    category = "geometry.primitives",
    order = 3
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

    @NodeProperty(displayName = "Center X", category = "Center", order = 1,
        description = "Default center X when Center port is unconnected")
    private double centerX = 0.0d;

    @NodeProperty(displayName = "Center Y", category = "Center", order = 2,
        description = "Default center Y when Center port is unconnected")
    private double centerY = 0.0d;

    @NodeProperty(displayName = "Center Z", category = "Center", order = 3,
        description = "Default center Z when Center port is unconnected")
    private double centerZ = 0.0d;

    @NodeProperty(displayName = "Radius", category = "Size", order = 10,
        description = "Default radius when Radius port is unconnected")
    private double radius = 5.0d;

    public SphereByCenterRadiusNode() {
        super(UUID.randomUUID(), "geometry.primitives.sphere");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Sphere center (overrides property)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Sphere radius (overrides property)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_SPHERE_ID, "Sphere", "Constructed sphere geometry", NodeDataType.SPHERE, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved sphere center", NodeDataType.POINT, this));
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
        Vector3d center = resolveCenter();
        double resolvedRadius = resolveRadius();

        if (center == null || !Double.isFinite(resolvedRadius) || resolvedRadius <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        SphereData sphere = new SphereData(center, resolvedRadius);
        outputValues.put(OUTPUT_SPHERE_ID, sphere);
        outputValues.put(OUTPUT_GEOMETRY_ID, sphere);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_RADIUS_ID, resolvedRadius);
        outputValues.put(OUTPUT_DIAMETER_ID, resolvedRadius * 2.0d);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d resolveCenter() {
        Vector3d fromPort = SpatialValueResolver.resolveVector3d(inputValues.get(INPUT_CENTER_ID));
        if (fromPort != null) {
            return fromPort;
        }
        return new Vector3d(centerX, centerY, centerZ);
    }

    private double resolveRadius() {
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        if (radiusObj instanceof Number number) {
            return number.doubleValue();
        }
        return radius;
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SPHERE_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_DIAMETER_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("centerX", centerX);
        state.put("centerY", centerY);
        state.put("centerZ", centerZ);
        state.put("radius", radius);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("centerX") instanceof Number n) {
            centerX = n.doubleValue();
        }
        if (map.get("centerY") instanceof Number n) {
            centerY = n.doubleValue();
        }
        if (map.get("centerZ") instanceof Number n) {
            centerZ = n.doubleValue();
        }
        if (map.get("radius") instanceof Number n) {
            radius = n.doubleValue();
        }
    }
}
