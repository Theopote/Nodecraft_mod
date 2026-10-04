package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Assigns a repeating palette of block ids to placements, coordinates, or voxelized geometry.
 * Flat path: per-item cyclic index. Tree path: per-branch cyclic index.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.basic_assignment.block_palette",
    displayName = "Block Palette",
    description = "Assigns palette block types cyclically. Flat: per-item; tree: per-branch. Remaps blockId only; preserves stateData.",
    category = "material.basic_assignment",
    order = 1
)
public class BlockPaletteNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_PLACEMENTS_TREE_ID = "input_placements_tree";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_BLOCKS_TREE_ID = "input_blocks_tree";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_PALETTE_ID = "input_palette";
    private static final String INPUT_START_INDEX_ID = "input_start_index";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_PLACEMENTS_TREE_ID = "output_placements_tree";
    private static final String OUTPUT_PALETTE_SIZE_ID = "output_palette_size";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private static final MaterialSourceResolver.SourcePorts SOURCE_PORTS = new MaterialSourceResolver.SourcePorts(
        INPUT_PLACEMENTS_TREE_ID,
        INPUT_BLOCKS_TREE_ID,
        INPUT_PLACEMENTS_ID,
        INPUT_COORDINATES_ID,
        INPUT_GEOMETRY_ID,
        INPUT_BOX_GEOMETRY_ID,
        INPUT_CYLINDER_GEOMETRY_ID,
        INPUT_SPHERE_GEOMETRY_ID,
        INPUT_TORUS_GEOMETRY_ID
    );

    public BlockPaletteNode() {
        super(UUID.randomUUID(), "material.basic_assignment.block_palette");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Incoming placements to remap through the palette", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_PLACEMENTS_TREE_ID, "Block Placements Tree", "Incoming placements grouped by branch", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCKS_TREE_ID, "Blocks Tree", "Block positions grouped by branch", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Palette", "Typed block palette (BLOCK_PALETTE)", NodeDataType.BLOCK_PALETTE, this));
        addInputPort(new BasePort(INPUT_START_INDEX_ID, "Start Index", "Palette offset (INTEGER only)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_TREE_ID, "Block Placements Tree", "Placements grouped by source branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_PALETTE_SIZE_ID, "Palette Size", "Number of palette entries", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Assigns palette block types cyclically. Flat: per-item; tree: per-branch. Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BasicAssignmentUtils.IndexResult startResult =
            BasicAssignmentUtils.resolveStartIndex(inputValues.get(INPUT_START_INDEX_ID), 0);
        if (!startResult.valid()) {
            emitFail(startResult.error());
            return;
        }
        int startIndex = startResult.index();

        BlockPaletteData paletteData = BlockPaletteData.requireTyped(inputValues.get(INPUT_PALETTE_ID));
        List<String> palette = new ArrayList<>(paletteData.blockIds());
        int paletteSize = palette.size();

        MaterialSourceResolver.SourceResolution source =
            MaterialSourceResolver.resolve(this, SOURCE_PORTS, "");
        if (!source.valid()) {
            emitFail(source.error());
            return;
        }

        switch (source.kind()) {
            case PLACEMENTS_TREE -> writeTreeAssignments(source.tree(), palette, startIndex, paletteSize, true);
            case BLOCKS_TREE -> {
                if (palette.isEmpty()) {
                    emitFail("Palette required for blocks tree input");
                    return;
                }
                writeTreeAssignments(source.tree(), palette, startIndex, paletteSize, false);
            }
            case PLACEMENTS -> {
                List<BlockPlacementData> placements = mapFlatPlacements(source.placements(), palette, startIndex);
                if (placements == null) {
                    return;
                }
                emitOk(placements, flatTree(placements), paletteSize);
            }
            case COORDINATES, GEOMETRY -> {
                if (palette.isEmpty()) {
                    emitFail("Palette required for geometry or coordinates input");
                    return;
                }
                List<BlockPlacementData> placements = mapFlatPlacements(source.placements(), palette, startIndex);
                if (placements == null) {
                    return;
                }
                emitOk(placements, flatTree(placements), paletteSize);
            }
            case NONE -> emitFail("No placements, coordinates, geometry, or tree input");
        }
    }

    private static DataTreeData flatTree(List<BlockPlacementData> placements) {
        return new DataTreeData(List.of(
            new DataTreeData.Branch(List.of(0), new ArrayList<Object>(placements))
        ));
    }

    private @Nullable List<BlockPlacementData> mapFlatPlacements(
            List<BlockPlacementData> sources,
            List<String> palette,
            int startIndex
    ) {
        List<BlockPlacementData> remapped = new ArrayList<>(sources.size());
        int index = 0;
        for (BlockPlacementData placement : sources) {
            String blockId = BasicAssignmentUtils.cyclicPaletteBlockId(
                palette, startIndex + index, placement.blockId());
            MaterialMappingSupport.RemapResult remap =
                MaterialMappingSupport.remapValidated(placement, blockId);
            if (!remap.valid()) {
                emitFail(remap.error());
                return null;
            }
            remapped.add(remap.placement());
            index++;
        }
        return remapped;
    }

    private void writeTreeAssignments(
            DataTreeData sourceTree,
            List<String> palette,
            int startIndex,
            int paletteSize,
            boolean preserveWhenEmptyPalette
    ) {
        List<BlockPlacementData> placements = new ArrayList<>();
        List<DataTreeData.Branch> placementBranches = new ArrayList<>();
        int branchIndex = 0;

        for (DataTreeData.Branch branch : sourceTree.getBranches()) {
            List<Object> branchPlacements = new ArrayList<>();
            if (palette.isEmpty() && preserveWhenEmptyPalette) {
                for (Object item : branch.items()) {
                    if (item instanceof BlockPlacementData sourcePlacement) {
                        MaterialMappingSupport.RemapResult remap =
                            MaterialMappingSupport.remapValidated(sourcePlacement, sourcePlacement.blockId());
                        if (!remap.valid()) {
                            emitFail(remap.error());
                            return;
                        }
                        placements.add(remap.placement());
                        branchPlacements.add(remap.placement());
                    } else {
                        BlockPlacementData placement = toPlacementPreserving(item);
                        if (placement != null) {
                            placements.add(placement);
                            branchPlacements.add(placement);
                        }
                    }
                }
            } else {
                String branchBlockId = BasicAssignmentUtils.cyclicPaletteBlockId(
                    palette, startIndex + branchIndex, null);
                for (Object item : branch.items()) {
                    if (item instanceof BlockPlacementData sourcePlacement) {
                        MaterialMappingSupport.RemapResult remap =
                            MaterialMappingSupport.remapValidated(sourcePlacement, branchBlockId);
                        if (!remap.valid()) {
                            emitFail(remap.error());
                            return;
                        }
                        placements.add(remap.placement());
                        branchPlacements.add(remap.placement());
                    } else {
                        BlockPlacementData placement = toPlacementWithBlockId(item, branchBlockId);
                        if (placement != null) {
                            placements.add(placement);
                            branchPlacements.add(placement);
                        }
                    }
                }
            }
            placementBranches.add(new DataTreeData.Branch(branch.path(), branchPlacements));
            branchIndex++;
        }

        emitOk(placements, new DataTreeData(placementBranches), paletteSize);
    }

    private static @Nullable BlockPlacementData toPlacementPreserving(Object item) {
        if (item instanceof BlockPlacementData placement) {
            return placement;
        }
        if (item instanceof BlockPos pos) {
            return new BlockPlacementData(pos, "");
        }
        return null;
    }

    private static @Nullable BlockPlacementData toPlacementWithBlockId(Object item, String blockId) {
        if (item instanceof BlockPlacementData placement) {
            return new BlockPlacementData(placement.pos(), blockId, placement.stateData());
        }
        if (item instanceof BlockPos pos) {
            return new BlockPlacementData(pos, blockId);
        }
        return null;
    }

    private void emitFail(String message) {
        outputValues.putAll(BasicAssignmentUtils.paletteFailResult(message));
    }

    private void emitOk(List<BlockPlacementData> placements, DataTreeData tree, int paletteSize) {
        outputValues.putAll(BasicAssignmentUtils.paletteOkResult(placements, tree, paletteSize));
    }
}
