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

    public TopSideBottomMapNode() {
        super(UUID.randomUUID(), "material.directional_mapping.top_side_bottom_map");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to remap (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized first when placements/coordinates are empty", NodeDataType.GEOMETRY, this));
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
        String topMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_TOP_ID));
        String sideMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_SIDE_ID));
        String bottomMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_BOTTOM_ID));

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
                MaterialMappingSupport.firstMappedBlockType(topMapped, sideMapped, bottomMapped)
            );

        if (!placementSource && sources.isEmpty() && hasNonPlacementSource()) {
            emitInvalid("Explicit block material required for geometry or coordinates input");
            return;
        }

        if (sources.isEmpty()) {
            emitSuccess(List.of());
            return;
        }

        Map<Long, Integer> minYByColumn = new HashMap<>();
        Map<Long, Integer> maxYByColumn = new HashMap<>();
        for (BlockPlacementData placement : sources) {
            BlockPos pos = placement.pos();
            if (pos == null) {
                continue;
            }
            long key = columnKey(pos.getX(), pos.getZ());
            minYByColumn.merge(key, pos.getY(), Math::min);
            maxYByColumn.merge(key, pos.getY(), Math::max);
        }

        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        for (BlockPlacementData source : sources) {
            BlockPos pos = source.pos();
            if (pos == null) {
                continue;
            }
            long key = columnKey(pos.getX(), pos.getZ());
            int minY = minYByColumn.getOrDefault(key, pos.getY());
            int maxY = maxYByColumn.getOrDefault(key, pos.getY());

            String roleMapped;
            if (pos.getY() == maxY) {
                roleMapped = topMapped;
            } else if (pos.getY() == minY) {
                roleMapped = bottomMapped;
            } else {
                roleMapped = sideMapped;
            }

            String blockId = MaterialMappingSupport.resolveMaterialTarget(roleMapped, source.blockId());
            placements.add(MaterialMappingSupport.remapBlockId(source, blockId));
        }

        emitSuccess(placements);
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

    private long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
