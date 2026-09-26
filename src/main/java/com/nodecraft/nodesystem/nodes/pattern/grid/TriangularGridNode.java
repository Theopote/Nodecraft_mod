package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
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
    id = "pattern.grid.triangular_grid",
    displayName = "Triangular Grid",
    description = "Generates triangular lattice anchor points with alternating row offsets",
    category = "pattern.grid",
    order = 4
)
public class TriangularGridNode extends BaseNode {

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_SIDE_LENGTH_ID = "input_side_length";
    private static final String INPUT_U_COUNT_ID = "input_u_count";
    private static final String INPUT_V_COUNT_ID = "input_v_count";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_FLIP_ID = "output_flip";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TriangularGridNode() {
        super(UUID.randomUUID(), "pattern.grid.triangular_grid");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Grid origin anchor point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_SIDE_LENGTH_ID, "Side Length", "Triangle side length / grid spacing", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_U_COUNT_ID, "U Count", "Number of positions along the U axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_V_COUNT_ID, "V Count", "Number of positions along the V axis", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Triangular lattice anchor points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FLIP_ID, "Flip", "Alternating orientation flag for downstream instancing at each lattice anchor", NodeDataType.BOOLEAN_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted anchor points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a triangular grid was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates triangular lattice anchor points with alternating row offsets";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
        if (origin == null) {
            origin = new Vector3d(0.0d, 0.0d, 0.0d);
        }

        int uCount = getInteger(INPUT_U_COUNT_ID, 8);
        int vCount = getInteger(INPUT_V_COUNT_ID, 8);
        if (uCount <= 0 || vCount <= 0) {
            writeEmpty();
            return;
        }

        double side = getDouble(INPUT_SIDE_LENGTH_ID, 2.0d);
        if (!Double.isFinite(side) || side <= 0.0d) {
            writeEmpty();
            return;
        }

        GenerationLimits.GridAxisCounts gridCounts = GenerationLimits.clampExclusiveGridCounts(uCount, vCount, 1, 1);
        uCount = gridCounts.xCount();
        vCount = gridCounts.yCount();

        double rowStep = side * Math.sqrt(3.0d) * 0.5d;
        List<Vector3d> points = new ArrayList<>(uCount * vCount);
        List<Boolean> orientation = new ArrayList<>(uCount * vCount);

        for (int v = 0; v < vCount; v++) {
            boolean oddRow = (v & 1) == 1;
            double rowOffsetX = oddRow ? side * 0.5d : 0.0d;
            for (int u = 0; u < uCount; u++) {
                double x = u * side + rowOffsetX;
                double z = v * rowStep;
                points.add(new Vector3d(origin).add(x, 0.0d, z));
                orientation.add(((u + v) & 1) == 0);
            }
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_FLIP_ID, List.copyOf(orientation));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
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
        outputValues.put(OUTPUT_FLIP_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
