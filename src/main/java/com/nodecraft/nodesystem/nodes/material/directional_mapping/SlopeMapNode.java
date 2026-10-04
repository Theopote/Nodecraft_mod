package com.nodecraft.nodesystem.nodes.material.directional_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
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

    private static final MaterialSourceResolver.SourcePorts SOURCE_PORTS = new MaterialSourceResolver.SourcePorts(
        null,
        null,
        INPUT_PLACEMENTS_ID,
        INPUT_COORDINATES_ID,
        INPUT_GEOMETRY_ID,
        INPUT_BOX_GEOMETRY_ID,
        INPUT_CYLINDER_GEOMETRY_ID,
        INPUT_SPHERE_GEOMETRY_ID,
        INPUT_TORUS_GEOMETRY_ID
    );

    public SlopeMapNode() {
        super(UUID.randomUUID(), "material.directional_mapping.slope_map");
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to remap (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized when no higher-precedence source is driven", NodeDataType.GEOMETRY, this));
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
        MaterialMappingSupport.MappedBlockType flat =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_FLAT_ID), MaterialSourceResolver.isDriven(this, INPUT_FLAT_ID));
        if (!flat.valid()) {
            emitInvalid(flat.error());
            return;
        }
        MaterialMappingSupport.MappedBlockType slope =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_SLOPE_ID), MaterialSourceResolver.isDriven(this, INPUT_SLOPE_ID));
        if (!slope.valid()) {
            emitInvalid(slope.error());
            return;
        }
        MaterialMappingSupport.MappedBlockType steep =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_STEEP_ID), MaterialSourceResolver.isDriven(this, INPUT_STEEP_ID));
        if (!steep.valid()) {
            emitInvalid(steep.error());
            return;
        }

        String fallback = MaterialMappingSupport.firstMappedBlockType(
            flat.blockId(), slope.blockId(), steep.blockId());
        MaterialSourceResolver.SourceResolution source =
            MaterialSourceResolver.resolve(this, SOURCE_PORTS, fallback);
        if (!source.valid()) {
            emitInvalid(source.error());
            return;
        }

        if (source.kind() == MaterialSourceResolver.SourceKind.NONE) {
            emitSuccess(List.of());
            return;
        }

        if ((source.kind() == MaterialSourceResolver.SourceKind.COORDINATES
            || source.kind() == MaterialSourceResolver.SourceKind.GEOMETRY)
            && fallback == null) {
            emitInvalid("Explicit block material required for geometry or coordinates input");
            return;
        }

        List<BlockPlacementData> sources = source.placements();
        if (sources.isEmpty()) {
            emitSuccess(List.of());
            return;
        }

        Map<Long, Integer> topYByColumn = new HashMap<>();
        for (BlockPlacementData placement : sources) {
            BlockPos pos = placement.pos();
            if (pos != null) {
                topYByColumn.merge(columnKey(pos.getX(), pos.getZ()), pos.getY(), Math::max);
            }
        }

        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        for (BlockPlacementData sourcePlacement : sources) {
            BlockPos pos = sourcePlacement.pos();
            int columnTop = 0;
            if (pos != null) {
                columnTop = topYByColumn.getOrDefault(columnKey(pos.getX(), pos.getZ()), pos.getY());
            }
            if (pos != null && pos.getY() != columnTop) {
                placements.add(sourcePlacement);
                continue;
            }

            int grade = 0;
            if (pos != null) {
                grade = maxNeighborGrade(topYByColumn, pos.getX(), pos.getZ(), columnTop);
            }
            String roleMapped = grade <= 0 ? flat.blockId() : (grade == 1 ? slope.blockId() : steep.blockId());
            String blockId = MaterialMappingSupport.resolveMaterialTarget(roleMapped, sourcePlacement.blockId());
            MaterialMappingSupport.RemapResult remap =
                MaterialMappingSupport.remapValidated(sourcePlacement, blockId);
            if (!remap.valid()) {
                emitInvalid(remap.error());
                return;
            }
            placements.add(remap.placement());
        }

        emitSuccess(placements);
    }

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
        long delta = Math.abs((long) columnTop - (long) neighborTop);
        return delta == 0 ? 0 : delta == 1 ? 1 : 2;
    }

    private static long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
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
