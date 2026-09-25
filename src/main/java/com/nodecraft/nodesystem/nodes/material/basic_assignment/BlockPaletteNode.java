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
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
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

        Object placementsTreeObj = inputValues.get(INPUT_PLACEMENTS_TREE_ID);
        if (placementsTreeObj instanceof DataTreeData placementsTree && placementsTree.getBranchCount() > 0) {
            writePlacementTreeAssignments(placementsTree, palette, startIndex, paletteSize);
            return;
        }

        Object blocksTreeObj = inputValues.get(INPUT_BLOCKS_TREE_ID);
        if (blocksTreeObj instanceof DataTreeData blocksTree && blocksTree.getBranchCount() > 0) {
            if (palette.isEmpty()) {
                emitFail("Palette required for blocks tree input");
                return;
            }
            writeBlockTreeAssignments(blocksTree, palette, startIndex, paletteSize);
            return;
        }

        if (BasicAssignmentUtils.hasPlacementSource(inputValues.get(INPUT_PLACEMENTS_ID))) {
            List<BlockPlacementData> placements = mapFlatPlacements(palette, startIndex);
            DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), new ArrayList<Object>(placements))
            ));
            emitOk(placements, tree, paletteSize);
            return;
        }

        if (BasicAssignmentUtils.hasNonPlacementSource(
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID)
        )) {
            if (palette.isEmpty()) {
                emitFail("Palette required for geometry or coordinates input");
                return;
            }
            List<BlockPlacementData> placements = mapGeometryPlacements(palette, startIndex);
            if (placements.isEmpty()) {
                emitFail("No geometry or coordinates resolved");
                return;
            }
            DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), new ArrayList<Object>(placements))
            ));
            emitOk(placements, tree, paletteSize);
            return;
        }

        emitFail("No placements, coordinates, geometry, or tree input");
    }

    private List<BlockPlacementData> mapFlatPlacements(List<String> palette, int startIndex) {
        List<BlockPlacementData> remapped = new ArrayList<>();
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (!(placementsObj instanceof List<?> placementList)) {
            return remapped;
        }
        int index = 0;
        for (Object entry : placementList) {
            if (!(entry instanceof BlockPlacementData placement) || placement.pos() == null) {
                continue;
            }
            String blockId = BasicAssignmentUtils.cyclicPaletteBlockId(
                palette, startIndex + index, placement.blockId());
            remapped.add(new BlockPlacementData(placement.pos(), blockId, placement.stateData()));
            index++;
        }
        return remapped;
    }

    private List<BlockPlacementData> mapGeometryPlacements(List<String> palette, int startIndex) {
        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            true
        );
        List<BlockPlacementData> resolved = new ArrayList<>();
        int index = 0;
        for (BlockPos pos : positions) {
            String blockId = BasicAssignmentUtils.cyclicPaletteBlockId(palette, startIndex + index, null);
            resolved.add(new BlockPlacementData(pos, blockId));
            index++;
        }
        return resolved;
    }

    private void writePlacementTreeAssignments(
            DataTreeData placementsTree,
            List<String> palette,
            int startIndex,
            int paletteSize
    ) {
        List<BlockPlacementData> placements = new ArrayList<>();
        List<DataTreeData.Branch> placementBranches = new ArrayList<>();
        int branchIndex = 0;

        for (DataTreeData.Branch branch : placementsTree.getBranches()) {
            List<Object> branchPlacements = new ArrayList<>();
            if (palette.isEmpty()) {
                for (Object item : branch.items()) {
                    if (item instanceof BlockPlacementData placement && placement.pos() != null) {
                        placements.add(placement);
                        branchPlacements.add(placement);
                    }
                }
            } else {
                String branchBlockId = BasicAssignmentUtils.cyclicPaletteBlockId(
                    palette, startIndex + branchIndex, null);
                for (Object item : branch.items()) {
                    if (item instanceof BlockPlacementData placement && placement.pos() != null) {
                        BlockPlacementData remapped = new BlockPlacementData(
                            placement.pos(), branchBlockId, placement.stateData());
                        placements.add(remapped);
                        branchPlacements.add(remapped);
                    }
                }
            }
            placementBranches.add(new DataTreeData.Branch(branch.path(), branchPlacements));
            branchIndex++;
        }

        emitOk(placements, new DataTreeData(placementBranches), paletteSize);
    }

    private void writeBlockTreeAssignments(
            DataTreeData blocksTree,
            List<String> palette,
            int startIndex,
            int paletteSize
    ) {
        List<BlockPlacementData> placements = new ArrayList<>();
        List<DataTreeData.Branch> placementBranches = new ArrayList<>();
        int branchIndex = 0;

        for (DataTreeData.Branch branch : blocksTree.getBranches()) {
            String branchBlockId = BasicAssignmentUtils.cyclicPaletteBlockId(
                palette, startIndex + branchIndex, null);
            List<Object> branchPlacements = new ArrayList<>();
            for (Object item : branch.items()) {
                if (item instanceof BlockPos pos) {
                    BlockPlacementData placement = new BlockPlacementData(pos, branchBlockId);
                    placements.add(placement);
                    branchPlacements.add(placement);
                }
            }
            placementBranches.add(new DataTreeData.Branch(branch.path(), branchPlacements));
            branchIndex++;
        }

        emitOk(placements, new DataTreeData(placementBranches), paletteSize);
    }

    private void emitFail(String message) {
        outputValues.putAll(BasicAssignmentUtils.paletteFailResult(message));
    }

    private void emitOk(List<BlockPlacementData> placements, DataTreeData tree, int paletteSize) {
        outputValues.putAll(BasicAssignmentUtils.paletteOkResult(placements, tree, paletteSize));
    }
}
