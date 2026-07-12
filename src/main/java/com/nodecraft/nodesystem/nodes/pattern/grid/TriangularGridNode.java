package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "pattern.grid.triangle_grid",
    displayName = "Triangular Grid",
    description = "Repeats coordinates on a triangular lattice with alternating row offsets.",
    category = "pattern.grid",
    order = 3
)
public class TriangularGridNode extends BaseNode {

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_SIDE_LENGTH_ID = "input_side_length";
    private static final String INPUT_U_COUNT_ID = "input_u_count";
    private static final String INPUT_V_COUNT_ID = "input_v_count";

    private static final String OUTPUT_COORDINATES_ID = "output_grid_coordinates";
    private static final String OUTPUT_ANCHORS_ID = "output_anchors";
    private static final String OUTPUT_TRIANGLE_UP_ID = "output_triangle_up";

    public TriangularGridNode() {
        super(UUID.randomUUID(), "pattern.grid.triangle_grid");
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Coordinates to repeat", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_SIDE_LENGTH_ID, "Side Length", "Triangle side length / grid spacing", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_U_COUNT_ID, "U Count", "Repetitions on U axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_V_COUNT_ID, "V Count", "Repetitions on V axis", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Grid Coordinates", "Coordinates repeated on triangular lattice anchors", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ANCHORS_ID, "Anchors", "Triangular lattice anchor points", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TRIANGLE_UP_ID, "Triangle Up", "Per-anchor orientation flag list (true=up, false=down)", NodeDataType.LIST, this));
    }

    @Override
    public String getDescription() {
        return "Repeats coordinates on a triangular lattice with alternating row offsets.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordsObj = inputValues.get(INPUT_COORDINATES_ID);
        if (!(coordsObj instanceof BlockPosList source) || source.isEmpty()) {
            outputValues.put(OUTPUT_COORDINATES_ID, new BlockPosList());
            outputValues.put(OUTPUT_ANCHORS_ID, new BlockPosList());
            outputValues.put(OUTPUT_TRIANGLE_UP_ID, List.of());
            return;
        }

        double side = Math.max(0.25d, getDouble(INPUT_SIDE_LENGTH_ID, 2.0d));
        int uCount = GenerationLimits.clampGridAxis(getInt(INPUT_U_COUNT_ID, 8));
        int vCount = GenerationLimits.clampGridAxis(getInt(INPUT_V_COUNT_ID, 8));
        double rowStep = side * Math.sqrt(3.0d) * 0.5d;

        BlockPosList anchors = new BlockPosList();
        List<Boolean> orientation = new ArrayList<>();
        BlockPosList result = new BlockPosList();
        for (int v = -vCount; v <= vCount; v++) {
            boolean oddRow = (Math.floorMod(v, 2) != 0);
            double rowOffsetX = oddRow ? side * 0.5d : 0.0d;
            for (int u = -uCount; u <= uCount; u++) {
                double x = u * side + rowOffsetX;
                double z = v * rowStep;
                BlockPos anchor = BlockPos.ofFloored(x, 0.0d, z);
                anchors.add(anchor);
                orientation.add(((u + v) & 1) == 0);
                for (BlockPos sourcePos : source) {
                    result.add(sourcePos.add(anchor.getX(), 0, anchor.getZ()));
                }
            }
        }

        outputValues.put(OUTPUT_COORDINATES_ID, result);
        outputValues.put(OUTPUT_ANCHORS_ID, anchors);
        outputValues.put(OUTPUT_TRIANGLE_UP_ID, List.copyOf(orientation));
    }

    private double getDouble(String portId, double fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    private int getInt(String portId, int fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.intValue() : fallback;
    }
}
