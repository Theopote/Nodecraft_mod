package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "transform.deformations.spherical_displace",
    displayName = "Spherical Displace",
    description = "Applies radial displacement with spherical distance falloff around a center point.",
    category = "transform.deformations",
    order = 8
)
public class SphericalDisplaceNode extends BaseNode {

    @NodeProperty(displayName = "Strength", category = "Spherical", order = 1)
    private double strength = 1.0d;

    @NodeProperty(displayName = "Radius", category = "Spherical", order = 2)
    private double radius = 8.0d;

    @NodeProperty(displayName = "Falloff Power", category = "Spherical", order = 3)
    private double falloffPower = 1.0d;

    @NodeProperty(displayName = "Affect Outside Radius", category = "Spherical", order = 4)
    private boolean affectOutsideRadius = false;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_POWER_ID = "input_power";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SphericalDisplaceNode() {
        super(UUID.randomUUID(), "transform.deformations.spherical_displace");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Input point list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Center of spherical displacement", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Optional displacement strength override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Optional influence radius override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_POWER_ID, "Falloff Power", "Optional falloff power override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Displaced points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when displacement was applied", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Spherical Displace";
    }

    @Override
    public String getDescription() {
        return "Applies radial displacement with spherical distance falloff around a center point.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (!(pointsObj instanceof List<?> inputPoints)) {
            writeInvalid();
            return;
        }

        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        if (center == null) {
            center = new Vector3d(0.0d, 0.0d, 0.0d);
        }
        double resolvedStrength = resolveDouble(inputValues.get(INPUT_STRENGTH_ID), strength);
        double resolvedRadius = Math.max(1.0e-9d, Math.abs(resolveDouble(inputValues.get(INPUT_RADIUS_ID), radius)));
        double resolvedPower = Math.max(1.0e-6d, resolveDouble(inputValues.get(INPUT_POWER_ID), falloffPower));

        List<Vector3d> out = new ArrayList<>(inputPoints.size());
        for (Object entry : inputPoints) {
            Vector3d point = resolvePoint(entry);
            if (point == null) {
                continue;
            }

            Vector3d radial = new Vector3d(point).sub(center);
            double distance = radial.length();
            if (distance <= 1.0e-12d) {
                out.add(new Vector3d(point));
                continue;
            }

            double normalized = distance / resolvedRadius;
            if (!affectOutsideRadius && normalized > 1.0d) {
                out.add(new Vector3d(point));
                continue;
            }
            double clamped = Math.max(0.0d, Math.min(1.0d, 1.0d - normalized));
            double weight = Math.pow(clamped, resolvedPower);
            if (affectOutsideRadius && normalized > 1.0d) {
                weight = -Math.pow(normalized - 1.0d, resolvedPower);
            }

            Vector3d dir = radial.normalize();
            Vector3d displaced = new Vector3d(point).add(dir.mul(resolvedStrength * weight));
            out.add(displaced);
        }

        if (out.isEmpty()) {
            writeInvalid();
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(out));
        outputValues.put(OUTPUT_COUNT_ID, out.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double resolveDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof Vector3d v) return new Vector3d(v);
        if (value instanceof Vec3d v) return new Vector3d(v.x, v.y, v.z);
        if (value instanceof PointData p) return new Vector3d(p.getPosition());
        if (value instanceof BlockPos b) return new Vector3d(b.getX(), b.getY(), b.getZ());
        return null;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("strength", strength);
        state.put("radius", radius);
        state.put("falloffPower", falloffPower);
        state.put("affectOutsideRadius", affectOutsideRadius);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("strength") instanceof Number n) strength = n.doubleValue();
        if (map.get("radius") instanceof Number n) radius = n.doubleValue();
        if (map.get("falloffPower") instanceof Number n) falloffPower = n.doubleValue();
        if (map.get("affectOutsideRadius") instanceof Boolean b) affectOutsideRadius = b;
    }
}
