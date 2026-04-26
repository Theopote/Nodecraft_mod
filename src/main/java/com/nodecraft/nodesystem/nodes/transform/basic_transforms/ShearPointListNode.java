package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

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
    id = "transform.basic_transforms.shear",
    displayName = "Shear Point List",
    description = "Applies axial shear deformation to a point list around an origin.",
    category = "transform.basic_transforms",
    order = 11
)
public class ShearPointListNode extends BaseNode {

    public enum ShearAxis {
        X,
        Y,
        Z
    }

    @NodeProperty(displayName = "Shear Axis", category = "Shear", order = 1)
    private ShearAxis shearAxis = ShearAxis.X;

    @NodeProperty(displayName = "Factor U", category = "Shear", order = 2)
    private double factorU = 0.0d;

    @NodeProperty(displayName = "Factor V", category = "Shear", order = 3)
    private double factorV = 0.0d;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_FACTOR_U_ID = "input_factor_u";
    private static final String INPUT_FACTOR_V_ID = "input_factor_v";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ShearPointListNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.shear");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Points to shear", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Shear origin", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_FACTOR_U_ID, "Factor U", "Optional override for first shear factor", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_FACTOR_V_ID, "Factor V", "Optional override for second shear factor", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sheared point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when shear was applied", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Shear Point List";
    }

    @Override
    public String getDescription() {
        return "Applies axial shear deformation to a point list around an origin.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (!(pointsObj instanceof List<?> pointList)) {
            writeInvalid();
            return;
        }

        Vector3d origin = resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
        if (origin == null) {
            origin = new Vector3d(0.0d, 0.0d, 0.0d);
        }
        double kU = resolveDouble(inputValues.get(INPUT_FACTOR_U_ID), factorU);
        double kV = resolveDouble(inputValues.get(INPUT_FACTOR_V_ID), factorV);

        List<Vector3d> out = new ArrayList<>(pointList.size());
        for (Object entry : pointList) {
            Vector3d p = resolvePoint(entry);
            if (p == null) {
                continue;
            }
            out.add(applyShear(p, origin, kU, kV));
        }

        if (out.isEmpty()) {
            writeInvalid();
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(out));
        outputValues.put(OUTPUT_COUNT_ID, out.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d applyShear(Vector3d point, Vector3d origin, double kU, double kV) {
        double rx = point.x - origin.x;
        double ry = point.y - origin.y;
        double rz = point.z - origin.z;

        double sx = rx;
        double sy = ry;
        double sz = rz;

        ShearAxis axis = shearAxis == null ? ShearAxis.X : shearAxis;
        switch (axis) {
            case X -> sx = rx + kU * ry + kV * rz;
            case Y -> sy = ry + kU * rx + kV * rz;
            case Z -> sz = rz + kU * rx + kV * ry;
        }
        return new Vector3d(origin.x + sx, origin.y + sy, origin.z + sz);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double resolveDouble(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
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
        state.put("shearAxis", shearAxis != null ? shearAxis.name() : ShearAxis.X.name());
        state.put("factorU", factorU);
        state.put("factorV", factorV);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object axisValue = map.get("shearAxis");
        if (axisValue instanceof String text) {
            try {
                shearAxis = ShearAxis.valueOf(text);
            } catch (IllegalArgumentException ignored) {
                shearAxis = ShearAxis.X;
            }
        }
        if (map.get("factorU") instanceof Number n) factorU = n.doubleValue();
        if (map.get("factorV") instanceof Number n) factorV = n.doubleValue();
    }
}
