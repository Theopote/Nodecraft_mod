package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.grid.hex_grid",
    displayName = "Hex Grid",
    description = "Generates hexagonal lattice anchor points on the X/Z plane",
    category = "pattern.grid",
    order = 3
)
public class HexGridNode extends AbstractPatternGridNode {
    public enum Orientation {
        FLAT_TOP,
        POINTY_TOP
    }

    @NodeProperty(displayName = "Orientation", category = "Grid", order = 1)
    private Orientation orientation = Orientation.FLAT_TOP;

    @NodeProperty(displayName = "Hex Radius", category = "Grid", order = 2,
        description = "Distance from hex center to a corner; also equals hex side length")
    private double radius = 1.0d;

    @NodeProperty(displayName = "Q Count", category = "Grid", order = 3)
    private int qCount = 4;

    @NodeProperty(displayName = "R Count", category = "Grid", order = 4)
    private int rCount = 4;

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_Q_COUNT_ID = "input_q_count";
    private static final String INPUT_R_COUNT_ID = "input_r_count";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public HexGridNode() {
        super(UUID.randomUUID(), "pattern.grid.hex_grid");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Grid origin anchor point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Hex Radius",
            "Distance from hex center to a corner; also equals hex side length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Q_COUNT_ID, "Q Count", "Number of columns along the q axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_R_COUNT_ID, "R Count", "Number of rows along the r axis", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Hex grid anchor points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted anchor points", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Generates hexagonal lattice anchor points on the X/Z plane with configurable spacing and orientation";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = resolveOptionalOrigin(this, INPUT_ORIGIN_ID);
        if (origin == null) {
            writeFail("Origin connected but invalid");
            return;
        }

        Integer resolvedQ = OptionalPortDrive.resolveOptionalInteger(this, INPUT_Q_COUNT_ID, qCount);
        Integer resolvedR = OptionalPortDrive.resolveOptionalInteger(this, INPUT_R_COUNT_ID, rCount);
        if (resolvedQ == null) {
            writeFail("Q Count connected but invalid");
            return;
        }
        if (resolvedR == null) {
            writeFail("R Count connected but invalid");
            return;
        }

        String productError = GenerationLimits.validateGridProduct(
            resolvedQ, resolvedR, GenerationLimits.MAX_LAYOUT_INSTANCES);
        if (productError != null) {
            writeFail(productError);
            return;
        }

        Double resolvedRadius = OptionalPortDrive.resolveOptionalDouble(this, INPUT_RADIUS_ID, radius);
        if (resolvedRadius == null) {
            writeFail("Radius connected but invalid");
            return;
        }
        if (!(resolvedRadius > 0.0d)) {
            writeFail("Radius must be > 0");
            return;
        }

        List<Vector3d> points = new ArrayList<>(resolvedQ * resolvedR);
        for (int r = 0; r < resolvedR; r++) {
            for (int q = 0; q < resolvedQ; q++) {
                Vector3d offset = axialToWorld(q, r, resolvedRadius, orientation);
                points.add(new Vector3d(origin).add(offset));
            }
        }

        commitPointList(OUTPUT_POINTS_ID, OUTPUT_COUNT_ID, points);
    }

    private Vector3d axialToWorld(int q, int r, double cellRadius, Orientation orientationMode) {
        double x;
        double z;
        if (orientationMode == Orientation.POINTY_TOP) {
            x = cellRadius * (Math.sqrt(3.0d) * (q + r * 0.5d));
            z = cellRadius * (1.5d * r);
        } else {
            x = cellRadius * (1.5d * q);
            z = cellRadius * (Math.sqrt(3.0d) * (r + q * 0.5d));
        }
        return new Vector3d(x, 0.0d, z);
    }

    private void writeFail(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("orientation", orientation.name());
        state.put("radius", radius);
        state.put("qCount", qCount);
        state.put("rCount", rCount);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object mode = map.get("orientation");
        if (mode instanceof String text) {
            try {
                orientation = Orientation.valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Keep existing orientation on invalid saved enum (P2).
            }
        }
        if (map.get("radius") instanceof Number value) {
            radius = value.doubleValue();
        }
        if (map.get("qCount") instanceof Number value) {
            qCount = value.intValue();
        }
        if (map.get("rCount") instanceof Number value) {
            rCount = value.intValue();
        }
    }
}
