package com.nodecraft.nodesystem.nodes.material.directional_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.directional_mapping.slope_map",
    displayName = "Slope Map",
    description = "Voxel material map: assigns flat/slope/steep by 4-neighbor column height grade. Remaps blockId only; preserves stateData.",
    category = "material.directional_mapping",
    order = 1
)
public class SlopeMapNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_FLAT_ID = "input_flat";
    private static final String INPUT_SLOPE_ID = "input_slope";
    private static final String INPUT_STEEP_ID = "input_steep";
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";

    public SlopeMapNode() {
        super(UUID.randomUUID(), "material.directional_mapping.slope_map");
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to remap (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized first when placements/coordinates are empty", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Legacy box geometry (voxelized first)", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Legacy cylinder geometry (voxelized first)", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Legacy sphere geometry (voxelized first)", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Legacy torus geometry (voxelized first)", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_FLAT_ID, "Flat", "Material for low slope", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SLOPE_ID, "Slope", "Material for medium slope", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_STEEP_ID, "Steep", "Material for steep slope", NodeDataType.BLOCK_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with the positions list", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Voxel material map: assigns flat/slope/steep by 4-neighbor column height grade. Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String flat = getInputString(INPUT_FLAT_ID, "minecraft:grass_block");
        String slope = getInputString(INPUT_SLOPE_ID, "minecraft:dirt");
        String steep = getInputString(INPUT_STEEP_ID, "minecraft:stone");

        List<BlockPlacementData> sources = MaterialMappingSupport.resolveSourcePlacements(
            inputValues.get(INPUT_PLACEMENTS_ID),
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            flat
        );

        Map<Long, Integer> topYByColumn = new HashMap<>();
        for (BlockPlacementData placement : sources) {
            BlockPos pos = placement.pos();
            if (pos == null) {
                continue;
            }
            topYByColumn.merge(columnKey(pos.getX(), pos.getZ()), pos.getY(), Math::max);
        }

        BlockPosList out = new BlockPosList();
        List<String> ids = new ArrayList<>(sources.size());
        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        for (BlockPlacementData source : sources) {
            BlockPos pos = source.pos();
            if (pos == null) {
                continue;
            }
            int columnTop = topYByColumn.getOrDefault(columnKey(pos.getX(), pos.getZ()), pos.getY());
            int grade = maxNeighborGrade(topYByColumn, pos.getX(), pos.getZ(), columnTop);
            String id = grade <= 0 ? flat : (grade == 1 ? slope : steep);
            out.add(pos);
            ids.add(id);
            placements.add(MaterialMappingSupport.remapBlockId(source, id));
        }
        outputValues.put(OUTPUT_POSITIONS_ID, out);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, ids);
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
    }

    /**
     * Unbiased grade: max absolute height delta against present +X/-X/+Z/-Z column tops.
     */
    private static int maxNeighborGrade(Map<Long, Integer> topYByColumn, int x, int z, int columnTop) {
        int grade = 0;
        grade = Math.max(grade, neighborDelta(topYByColumn, x + 1, z, columnTop));
        grade = Math.max(grade, neighborDelta(topYByColumn, x - 1, z, columnTop));
        grade = Math.max(grade, neighborDelta(topYByColumn, x, z + 1, columnTop));
        grade = Math.max(grade, neighborDelta(topYByColumn, x, z - 1, columnTop));
        return grade;
    }

    private static int neighborDelta(Map<Long, Integer> topYByColumn, int x, int z, int columnTop) {
        Integer neighborTop = topYByColumn.get(columnKey(x, z));
        if (neighborTop == null) {
            return 0;
        }
        return Math.abs(columnTop - neighborTop);
    }

    private static long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }
}
