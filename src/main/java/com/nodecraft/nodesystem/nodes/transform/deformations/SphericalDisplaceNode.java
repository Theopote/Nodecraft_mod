package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.spherical_displace",
    displayName = "Spherical Displace",
    description = "Applies radial displacement with spherical distance falloff around a center point.",
    category = "transform.deformations",
    order = 5
)
public class SphericalDisplaceNode extends BaseNode {

    @NodeProperty(displayName = "Strength", category = "Spherical", order = 1)
    private double strength = 1.0d;

    @NodeProperty(displayName = "Radius", category = "Spherical", order = 2)
    private double radius = 8.0d;

    @NodeProperty(displayName = "Falloff Power", category = "Spherical", order = 3)
    private double falloffPower = 1.0d;

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

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Input point list", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Center of spherical displacement", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Optional displacement strength override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Optional influence radius override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_POWER_ID, "Falloff Power", "Optional falloff power override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Displaced points", NodeDataType.POINT_LIST, this));
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
        List<Vector3d> inputPoints = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        Vector3d center = OptionalPortDrive.resolveOptionalPoint(this, INPUT_CENTER_ID, new Vector3d());
        Double resolvedStrength = OptionalPortDrive.resolveOptionalDouble(this, INPUT_STRENGTH_ID, strength);
        Double resolvedRadius = OptionalPortDrive.resolveOptionalDouble(this, INPUT_RADIUS_ID, radius);
        Double resolvedPower = OptionalPortDrive.resolveOptionalDouble(this, INPUT_POWER_ID, falloffPower);

        if (inputPoints == null
                || center == null
                || resolvedStrength == null
                || resolvedRadius == null
                || resolvedRadius <= 0.0d
                || resolvedPower == null
                || resolvedPower <= 0.0d) {
            writeInvalid();
            return;
        }

        List<Vector3d> out = new ArrayList<>(inputPoints.size());
        for (Vector3d point : inputPoints) {
            Vector3d radial = new Vector3d(point).sub(center);
            double distance = radial.length();
            if (distance <= 1.0e-12d) {
                out.add(new Vector3d(point));
                continue;
            }

            double normalized = distance / resolvedRadius;
            if (normalized > 1.0d) {
                out.add(new Vector3d(point));
                continue;
            }
            double clamped = Math.max(0.0d, Math.min(1.0d, 1.0d - normalized));
            double weight = Math.pow(clamped, resolvedPower);

            Vector3d dir = radial.normalize();
            out.add(new Vector3d(point).add(dir.mul(resolvedStrength * weight)));
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(out));
        outputValues.put(OUTPUT_COUNT_ID, out.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("strength", strength);
        state.put("radius", radius);
        state.put("falloffPower", falloffPower);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("strength") instanceof Number value && Double.isFinite(value.doubleValue())) {
            strength = value.doubleValue();
        }
        if (map.get("radius") instanceof Number value && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0.0d) {
            radius = value.doubleValue();
        }
        if (map.get("falloffPower") instanceof Number value && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0.0d) {
            falloffPower = value.doubleValue();
        }
    }
}
