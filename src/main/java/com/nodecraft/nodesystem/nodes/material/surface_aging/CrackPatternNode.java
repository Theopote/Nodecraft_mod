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

@NodeInfo(
    id = "material.surface_aging.crack_pattern",
    displayName = "Crack Pattern",
    description = "Adds deterministic crack lines by replacing sparse diagonal bands.",
    category = "material.surface_aging",
    order = 2
)
public class CrackPatternNode extends BaseNode {

    @Override
    public String getDescription() {
        return "Adds deterministic crack lines by replacing sparse diagonal bands.";
    }
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BASE_ID = "input_base";
    private static final String INPUT_CRACK_ID = "input_crack";
    private static final String INPUT_INTERVAL_ID = "input_interval";
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";

    public CrackPatternNode() {
        super(UUID.randomUUID(), "material.surface_aging.crack_pattern");
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BASE_ID, "Base Block", "Base material", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_CRACK_ID, "Crack Block", "Crack material", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_INTERVAL_ID, "Interval", "Spacing between crack lines", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with the positions list", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Position and block pairs for baking", NodeDataType.BLOCK_PLACEMENT_LIST, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            inputValues.get(INPUT_COORDINATES_ID), inputValues.get(INPUT_GEOMETRY_ID), inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID), inputValues.get(INPUT_SPHERE_GEOMETRY_ID), inputValues.get(INPUT_TORUS_GEOMETRY_ID), true
        );
        String base = getInputString(INPUT_BASE_ID, "minecraft:stone_bricks");
        String crack = getInputString(INPUT_CRACK_ID, "minecraft:cracked_stone_bricks");
        int interval = Math.max(2, getInputInt(INPUT_INTERVAL_ID, 7));

        BlockPosList out = new BlockPosList();
        List<String> ids = new ArrayList<>();
        List<BlockPlacementData> placements = new ArrayList<>();
        for (BlockPos pos : positions) {
            boolean isCrack = Math.floorMod(pos.getX() + pos.getZ() * 2 + pos.getY(), interval) == 0;
            String id = isCrack ? crack : base;
            out.add(pos);
            ids.add(id);
            placements.add(new BlockPlacementData(pos, id));
        }
        outputValues.put(OUTPUT_POSITIONS_ID, out);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, ids);
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }
}

