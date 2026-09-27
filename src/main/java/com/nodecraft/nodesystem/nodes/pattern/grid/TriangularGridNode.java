package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
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
    id = "pattern.grid.triangular_grid",
    displayName = "Triangular Grid",
    description = "Generates triangular lattice anchor points with alternating row offsets",
    category = "pattern.grid",
    order = 4
)
public class TriangularGridNode extends AbstractPatternGridNode {

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_SIDE_LENGTH_ID = "input_side_length";
    private static final String INPUT_U_COUNT_ID = "input_u_count";
    private static final String INPUT_V_COUNT_ID = "input_v_count";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_FLIP_ID = "output_flip";
    private static final String OUTPUT_COUNT_ID = "output_count";

    @NodeProperty(displayName = "Side Length", category = "Grid", order = 1)
    private double sideLength = 2.0d;

    @NodeProperty(displayName = "U Count", category = "Grid", order = 2)
    private int uCount = 8;

    @NodeProperty(displayName = "V Count", category = "Grid", order = 3)
    private int vCount = 8;

    public TriangularGridNode() {
        super(UUID.randomUUID(), "pattern.grid.triangular_grid");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Grid origin anchor point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_SIDE_LENGTH_ID, "Side Length", "Triangle side length / grid spacing", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_U_COUNT_ID, "U Count", "Number of positions along the U axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_V_COUNT_ID, "V Count", "Number of positions along the V axis", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Triangular lattice anchor points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FLIP_ID, "Flip", "Alternating orientation flag for downstream instancing at each lattice anchor", NodeDataType.BOOLEAN_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted anchor points", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Generates triangular lattice anchor points with alternating row offsets";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = resolveOptionalOrigin(this, INPUT_ORIGIN_ID);
        if (origin == null) {
            writeFail("Origin connected but invalid");
            return;
        }

        Integer resolvedU = OptionalPortDrive.resolveOptionalInteger(this, INPUT_U_COUNT_ID, uCount);
        Integer resolvedV = OptionalPortDrive.resolveOptionalInteger(this, INPUT_V_COUNT_ID, vCount);
        if (resolvedU == null) {
            writeFail("U Count connected but invalid");
            return;
        }
        if (resolvedV == null) {
            writeFail("V Count connected but invalid");
            return;
        }

        String productError = GenerationLimits.validateGridProduct(
            resolvedU, resolvedV, GenerationLimits.MAX_LAYOUT_INSTANCES);
        if (productError != null) {
            writeFail(productError);
            return;
        }

        Double side = OptionalPortDrive.resolveOptionalDouble(this, INPUT_SIDE_LENGTH_ID, sideLength);
        if (side == null) {
            writeFail("Side Length connected but invalid");
            return;
        }
        if (!(side > 0.0d)) {
            writeFail("Side Length must be > 0");
            return;
        }

        double rowStep = side * Math.sqrt(3.0d) * 0.5d;
        List<Vector3d> points = new ArrayList<>(resolvedU * resolvedV);
        List<Boolean> orientation = new ArrayList<>(resolvedU * resolvedV);

        for (int v = 0; v < resolvedV; v++) {
            boolean oddRow = (v & 1) == 1;
            double rowOffsetX = oddRow ? side * 0.5d : 0.0d;
            for (int u = 0; u < resolvedU; u++) {
                double x = u * side + rowOffsetX;
                double z = v * rowStep;
                Vector3d point = new Vector3d(origin).add(x, 0.0d, z);
                if (!PointUtils.isFinite(point)) {
                    writeFail("Non-finite output point");
                    return;
                }
                points.add(point);
                orientation.add(((u + v) & 1) == 0);
            }
        }

        markSuccess();
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_FLIP_ID, List.copyOf(orientation));
        putIntOutputs(points.size(), OUTPUT_COUNT_ID);
    }

    private void writeFail(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_FLIP_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("sideLength", sideLength);
        state.put("uCount", uCount);
        state.put("vCount", vCount);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("sideLength") instanceof Number value) {
            sideLength = value.doubleValue();
        }
        if (map.get("uCount") instanceof Number value) {
            uCount = value.intValue();
        }
        if (map.get("vCount") instanceof Number value) {
            vCount = value.intValue();
        }
    }
}
