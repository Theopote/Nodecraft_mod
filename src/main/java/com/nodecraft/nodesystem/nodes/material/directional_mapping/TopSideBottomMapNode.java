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

/**
 * Column height material map (highest / lowest / middle per X/Z column) — not surface-normal classification.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.directional_mapping.top_side_bottom_map",
    displayName = "Column Layer Map",
    description = "Column stratification map: highest / lowest / middle blocks per X/Z column. Remaps blockId only; preserves stateData.",
    category = "material.directional_mapping",
    order = 0
)
public class TopSideBottomMapNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_TOP_ID = "input_top";
    private static final String INPUT_SIDE_ID = "input_side";
    private static final String INPUT_BOTTOM_ID = "input_bottom";

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

    public TopSideBottomMapNode() {
        super(UUID.randomUUID(), "material.directional_mapping.top_side_bottom_map");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to remap (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized when no higher-precedence source is driven", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Legacy box geometry (voxelized first)", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Legacy cylinder geometry (voxelized first)", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Legacy sphere geometry (voxelized first)", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Legacy torus geometry (voxelized first)", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_TOP_ID, "Top", "Block used for the highest block in each X/Z column", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SIDE_ID, "Side", "Block used for interior column blocks", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_BOTTOM_ID, "Bottom", "Block used for the lowest block in each X/Z column", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when inputs are sufficient and mapping succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Column stratification map: highest / lowest / middle blocks per X/Z column. Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        MaterialMappingSupport.MappedBlockType top =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_TOP_ID), MaterialSourceResolver.isDriven(this, INPUT_TOP_ID));
        if (!top.valid()) {
            emitInvalid(top.error());
            return;
        }
        MaterialMappingSupport.MappedBlockType side =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_SIDE_ID), MaterialSourceResolver.isDriven(this, INPUT_SIDE_ID));
        if (!side.valid()) {
            emitInvalid(side.error());
            return;
        }
        MaterialMappingSupport.MappedBlockType bottom =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_BOTTOM_ID), MaterialSourceResolver.isDriven(this, INPUT_BOTTOM_ID));
        if (!bottom.valid()) {
            emitInvalid(bottom.error());
            return;
        }

        String fallback = MaterialMappingSupport.firstMappedBlockType(
            top.blockId(), side.blockId(), bottom.blockId());
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

        Map<Long, Integer> minYByColumn = new HashMap<>();
        Map<Long, Integer> maxYByColumn = new HashMap<>();
        for (BlockPlacementData placement : sources) {
            BlockPos pos = placement.pos();
            long key = columnKey(pos.getX(), pos.getZ());
            minYByColumn.merge(key, pos.getY(), Math::min);
            maxYByColumn.merge(key, pos.getY(), Math::max);
        }

        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        for (BlockPlacementData sourcePlacement : sources) {
            BlockPos pos = sourcePlacement.pos();
            long key = columnKey(pos.getX(), pos.getZ());
            int minY = minYByColumn.getOrDefault(key, pos.getY());
            int maxY = maxYByColumn.getOrDefault(key, pos.getY());

            String roleMapped;
            if (pos.getY() == maxY) {
                roleMapped = top.blockId();
            } else if (pos.getY() == minY) {
                roleMapped = bottom.blockId();
            } else {
                roleMapped = side.blockId();
            }

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

    private long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
