package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "pattern.grid.hex_grid",
    displayName = "Hex Grid",
    description = "Repeats coordinates on a flat-top hexagonal lattice (X/Z) with configurable spacing",
    category = "pattern.grid",
    order = 2
)
public class HexGridNode extends BaseNode {
    public enum Orientation {
        FLAT_TOP,
        POINTY_TOP
    }

    @NodeProperty(displayName = "Orientation", category = "Grid", order = 1)
    private Orientation orientation = Orientation.FLAT_TOP;

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_Q_COUNT_ID = "input_q_count";
    private static final String INPUT_R_COUNT_ID = "input_r_count";
    private static final String OUTPUT_COORDINATES_ID = "output_grid_coordinates";

    public HexGridNode() {
        super(UUID.randomUUID(), "pattern.grid.hex_grid");
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Coordinates to repeat", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Hex cell spacing radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Q_COUNT_ID, "Q Count", "Repetitions on q axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_R_COUNT_ID, "R Count", "Repetitions on r axis", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Grid Coordinates", "Hex grid coordinates", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Repeats coordinates on a hexagonal lattice (X/Z) with configurable spacing and orientation";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordsObj = inputValues.get(INPUT_COORDINATES_ID);
        if (!(coordsObj instanceof BlockPosList source)) {
            outputValues.put(OUTPUT_COORDINATES_ID, new BlockPosList());
            return;
        }
        double radius = Math.max(0.25d, getDouble(INPUT_RADIUS_ID, 1.0d));
        int qCount = GenerationLimits.clampGridAxis(getInt(INPUT_Q_COUNT_ID, 4));
        int rCount = GenerationLimits.clampGridAxis(getInt(INPUT_R_COUNT_ID, 4));

        BlockPosList result = new BlockPosList();
        for (int q = -qCount; q <= qCount; q++) {
            for (int r = -rCount; r <= rCount; r++) {
                Vector3d offset = axialToWorld(q, r, radius, orientation);
                for (BlockPos pos : source) {
                    result.add(new BlockPos(
                        (int) Math.round(pos.getX() + offset.x),
                        pos.getY(),
                        (int) Math.round(pos.getZ() + offset.z)
                    ));
                }
            }
        }
        outputValues.put(OUTPUT_COORDINATES_ID, result);
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

    private int getInt(String portId, int fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.intValue() : fallback;
    }
}
