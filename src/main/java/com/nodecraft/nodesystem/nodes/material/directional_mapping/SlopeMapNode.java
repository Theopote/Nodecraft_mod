package com.nodecraft.nodesystem.nodes.material.directional_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
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
    displayName = "Surface Slope Map",
    description = "Surface material map: assigns flat/slope/steep by 4-neighbor column height grade on column-top voxels only. Remaps blockId only; preserves stateData.",
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
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

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
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when inputs are sufficient and mapping succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Surface material map: assigns flat/slope/steep by 4-neighbor column height grade on column-top voxels only.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String flatMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_FLAT_ID));
        String slopeMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_SLOPE_ID));
        String steepMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_STEEP_ID));

        List<BlockPlacementData> fromPlacements = MaterialMappingSupport.extractPlacements(inputValues.get(INPUT_PLACEMENTS_ID));
        boolean placementSource = !fromPlacements.isEmpty();

        List<BlockPlacementData> sources = placementSource
            ? fromPlacements
            : MaterialMappingSupport.resolveSourcePlacements(
                null,
                inputValues.get(INPUT_COORDINATES_ID),
                inputValues.get(INPUT_GEOMETRY_ID),
                inputValues.get(INPUT_BOX_GEOMETRY_ID),
                inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
                inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
                inputValues.get(INPUT_TORUS_GEOMETRY_ID),
                MaterialMappingSupport.firstMappedBlockType(flatMapped, slopeMapped, steepMapped)
            );

        if (!placementSource && sources.isEmpty() && hasNonPlacementSource()) {
            emitInvalid("Explicit block material required for geometry or coordinates input");
            return;
        }

        if (sources.isEmpty()) {
            emitSuccess(List.of());
            return;
        }

        Map<Long, Integer> topYByColumn = new HashMap<>();
        for (BlockPlacementData placement : sources) {
            BlockPos pos = placement.pos();
            if (pos == null) {
                continue;
            }
            topYByColumn.merge(columnKey(pos.getX(), pos.getZ()), pos.getY(), Math::max);
        }

        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        for (BlockPlacementData source : sources) {
            BlockPos pos = source.pos();
            if (pos == null) {
                continue;
            }
            int columnTop = topYByColumn.getOrDefault(columnKey(pos.getX(), pos.getZ()), pos.getY());
            if (pos.getY() != columnTop) {
                placements.add(source);
                continue;
            }

            int grade = maxNeighborGrade(topYByColumn, pos.getX(), pos.getZ(), columnTop);
            String roleMapped = grade <= 0 ? flatMapped : (grade == 1 ? slopeMapped : steepMapped);
            String blockId = MaterialMappingSupport.resolveMaterialTarget(roleMapped, source.blockId());
            placements.add(MaterialMappingSupport.remapBlockId(source, blockId));
        }

        emitSuccess(placements);
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

    private boolean hasNonPlacementSource() {
        return inputValues.get(INPUT_COORDINATES_ID) != null
            || inputValues.get(INPUT_GEOMETRY_ID) != null
            || inputValues.get(INPUT_BOX_GEOMETRY_ID) != null
            || inputValues.get(INPUT_CYLINDER_GEOMETRY_ID) != null
            || inputValues.get(INPUT_SPHERE_GEOMETRY_ID) != null
            || inputValues.get(INPUT_TORUS_GEOMETRY_ID) != null;
    }

    private void emitInvalid(String message) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, message);
    }

    private void emitSuccess(List<BlockPlacementData> placements) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }
}
