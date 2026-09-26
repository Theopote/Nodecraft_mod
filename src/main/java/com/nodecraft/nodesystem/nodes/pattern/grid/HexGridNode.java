package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.grid.hex_grid",
    displayName = "Hex Grid",
    description = "Generates hexagonal lattice anchor points on the X/Z plane",
    category = "pattern.grid",
    order = 3
)
public class HexGridNode extends BaseNode {
    public enum Orientation {
        FLAT_TOP,
        POINTY_TOP
    }

    @NodeProperty(displayName = "Orientation", category = "Grid", order = 1)
    private Orientation orientation = Orientation.FLAT_TOP;

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_Q_COUNT_ID = "input_q_count";
    private static final String INPUT_R_COUNT_ID = "input_r_count";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public HexGridNode() {
        super(UUID.randomUUID(), "pattern.grid.hex_grid");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Grid origin anchor point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Hex cell spacing radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Q_COUNT_ID, "Q Count", "Number of columns along the q axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_R_COUNT_ID, "R Count", "Number of rows along the r axis", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Hex grid anchor points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted anchor points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a hex grid was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates hexagonal lattice anchor points on the X/Z plane with configurable spacing and orientation";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
        if (origin == null) {
            origin = new Vector3d(0.0d, 0.0d, 0.0d);
        }

        int qCount = getInteger(INPUT_Q_COUNT_ID, 4);
        int rCount = getInteger(INPUT_R_COUNT_ID, 4);
        if (qCount <= 0 || rCount <= 0) {
            writeEmpty();
            return;
        }

        double radius = getDouble(INPUT_RADIUS_ID, 1.0d);
        if (!Double.isFinite(radius) || radius <= 0.0d) {
            writeEmpty();
            return;
        }

        GenerationLimits.GridAxisCounts gridCounts = GenerationLimits.clampExclusiveGridCounts(qCount, rCount, 1, 1);
        qCount = gridCounts.xCount();
        rCount = gridCounts.yCount();

        List<Vector3d> points = new ArrayList<>(qCount * rCount);
        for (int r = 0; r < rCount; r++) {
            for (int q = 0; q < qCount; q++) {
                Vector3d offset = axialToWorld(q, r, radius, orientation);
                points.add(new Vector3d(origin).add(offset));
            }
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d axialToWorld(int q, int r, double radius, Orientation orientationMode) {
        double x;
        double z;
        if (orientationMode == Orientation.POINTY_TOP) {
            x = radius * (Math.sqrt(3.0d) * (q + r * 0.5d));
            z = radius * (1.5d * r);
        } else {
            x = radius * (1.5d * q);
            z = radius * (Math.sqrt(3.0d) * (r + q * 0.5d));
        }
        return new Vector3d(x, 0.0d, z);
    }

    private double getDouble(String portId, double fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    private int getInteger(String portId, int fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Integer i ? i : fallback;
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
