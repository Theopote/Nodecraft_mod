package com.nodecraft.nodesystem.nodes.material.basic_assignment;

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
 * Assigns a repeating palette of block ids to placements, coordinates, or voxelized geometry.
 */
@NodeInfo(
    id = "material.basic_assignment.block_palette",
    displayName = "Block Palette",
    description = "Assigns a repeating palette of block types to resolved positions",
    category = "material.basic_assignment",
    order = 1
)
public class BlockPaletteNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_PALETTE_ID = "input_palette";
    private static final String INPUT_FALLBACK_BLOCK_TYPE_ID = "input_fallback_block_type";
    private static final String INPUT_START_INDEX_ID = "input_start_index";

    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_PALETTE_SIZE_ID = "output_palette_size";

    public BlockPaletteNode() {
        super(UUID.randomUUID(), "material.basic_assignment.block_palette");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to remap through the palette", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Palette", "List of block ids such as [minecraft:stone, minecraft:andesite]", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_FALLBACK_BLOCK_TYPE_ID, "Fallback Block Type", "Used when the palette input is empty", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_START_INDEX_ID, "Start Index", "Palette offset applied to the first resolved block", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block ids aligned with the positions list", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Position and block pairs for baking", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PALETTE_SIZE_ID, "Palette Size", "Number of usable block ids in the palette", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Assigns a repeating palette of block types to resolved positions";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<String> palette = resolvePalette();
        int startIndex = getInputInteger(INPUT_START_INDEX_ID, 0);

        List<BlockPlacementData> placements = resolvePlacements(palette, startIndex);
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(placements.size());

        for (BlockPlacementData placement : placements) {
            if (placement.pos() == null || placement.blockId() == null || placement.blockId().isEmpty()) {
                continue;
            }
            positions.add(placement.pos());
            blockIds.add(placement.blockId());
        }

        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_PALETTE_SIZE_ID, palette.size());
    }

    private List<BlockPlacementData> resolvePlacements(List<String> palette, int startIndex) {
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (placementsObj instanceof List<?> placementList && !placementList.isEmpty()) {
            List<BlockPlacementData> remapped = new ArrayList<>();
            int index = 0;
            for (Object entry : placementList) {
                if (!(entry instanceof BlockPlacementData placement) || placement.pos() == null) {
                    continue;
                }
                String blockId = getPaletteValue(palette, startIndex + index, placement.blockId());
                remapped.add(new BlockPlacementData(placement.pos(), blockId, placement.stateData()));
                index++;
            }
            return remapped;
        }

        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            true
        );

        String fallbackBlock = getInputString(INPUT_FALLBACK_BLOCK_TYPE_ID, "minecraft:stone");
        List<BlockPlacementData> resolved = new ArrayList<>();
        int index = 0;
        for (BlockPos pos : positions) {
            String blockId = getPaletteValue(palette, startIndex + index, fallbackBlock);
            resolved.add(new BlockPlacementData(pos, blockId));
            index++;
        }
        return resolved;
    }

    private List<String> resolvePalette() {
        Object paletteObj = inputValues.get(INPUT_PALETTE_ID);
        List<String> palette = new ArrayList<>();
        if (paletteObj instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String blockId && !blockId.isBlank()) {
                    palette.add(blockId);
                }
            }
        }

        if (palette.isEmpty()) {
            palette.add(getInputString(INPUT_FALLBACK_BLOCK_TYPE_ID, "minecraft:stone"));
        }
        return palette;
    }

    private String getPaletteValue(List<String> palette, int index, String fallback) {
        if (palette.isEmpty()) {
            return fallback;
        }
        String blockId = palette.get(Math.floorMod(index, palette.size()));
        return (blockId == null || blockId.isBlank()) ? fallback : blockId;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private int getInputInteger(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
