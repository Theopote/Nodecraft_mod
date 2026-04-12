package com.nodecraft.nodesystem.nodes.material.surface_aging;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies a deterministic aged-material replacement over placements or voxelized geometry.
 */
@NodeInfo(
    id = "material.surface_aging.weathering",
    displayName = "Weathering",
    description = "Replaces part of a block set with an aged material using a deterministic weathering ratio",
    category = "material.surface_aging"
)
public class WeatheringNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BASE_BLOCK_ID = "input_base_block";
    private static final String INPUT_AGED_BLOCK_ID = "input_aged_block";
    private static final String INPUT_AMOUNT_ID = "input_amount";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";

    public WeatheringNode() {
        super(UUID.randomUUID(), "material.surface_aging.weathering");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to weather", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BASE_BLOCK_ID, "Base Block", "Fallback base block when generating placements", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_AGED_BLOCK_ID, "Aged Block", "Block type used for weathered cells", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_AMOUNT_ID, "Amount", "Weathering ratio from 0.0 to 1.0", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Seed used for deterministic weathering variation", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with the positions list", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Weathered placements for baking", NodeDataType.BLOCK_PLACEMENT_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Replaces part of a block set with an aged material using a deterministic weathering ratio";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<BlockPlacementData> sourcePlacements = resolvePlacements();
        String agedBlockId = getInputString(INPUT_AGED_BLOCK_ID, "minecraft:mossy_cobblestone");
        double amount = clamp01(getInputDouble(INPUT_AMOUNT_ID, 0.2d));
        int seed = getInputInt(INPUT_SEED_ID, 0);

        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>();
        List<BlockPlacementData> placements = new ArrayList<>();

        for (BlockPlacementData placement : sourcePlacements) {
            BlockPos pos = placement.pos();
            if (pos == null || placement.blockId() == null || placement.blockId().isEmpty()) {
                continue;
            }

            String resolvedBlockId = shouldWeather(pos, seed, amount) ? agedBlockId : placement.blockId();
            BlockPlacementData resolvedPlacement = new BlockPlacementData(pos, resolvedBlockId, placement.stateData());

            positions.add(pos);
            blockIds.add(resolvedBlockId);
            placements.add(resolvedPlacement);
        }

        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
    }

    private List<BlockPlacementData> resolvePlacements() {
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (placementsObj instanceof List<?> placementList && !placementList.isEmpty()) {
            List<BlockPlacementData> resolved = new ArrayList<>();
            for (Object entry : placementList) {
                if (entry instanceof BlockPlacementData placement && placement.pos() != null && placement.blockId() != null) {
                    resolved.add(placement);
                }
            }
            return resolved;
        }

        String baseBlockId = getInputString(INPUT_BASE_BLOCK_ID, "minecraft:stone_bricks");
        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            true
        );

        List<BlockPlacementData> generated = new ArrayList<>();
        for (BlockPos pos : positions) {
            generated.add(new BlockPlacementData(pos, baseBlockId));
        }
        return generated;
    }

    private boolean shouldWeather(BlockPos pos, int seed, double amount) {
        if (amount <= 0.0d) {
            return false;
        }
        if (amount >= 1.0d) {
            return true;
        }

        long hash = 1469598103934665603L;
        hash = mix(hash, pos.getX());
        hash = mix(hash, pos.getY());
        hash = mix(hash, pos.getZ());
        hash = mix(hash, seed);
        double normalized = (double) (hash & 0x7fffffffL) / (double) 0x7fffffffL;
        return normalized < amount;
    }

    private long mix(long current, int value) {
        long mixed = current ^ value;
        return mixed * 1099511628211L;
    }

    private double clamp01(double value) {
        if (value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isEmpty()) ? text : fallback;
    }
}
