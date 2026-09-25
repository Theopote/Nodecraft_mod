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

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.basic_assignment.weighted_palette",
    displayName = "Weighted Block Palette",
    description = "Assigns weighted random block types by position + seed via RandomOps. Remaps blockId only; preserves stateData.",
    category = "material.basic_assignment",
    order = 2
)
public class WeightedBlockPaletteNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_PLACEMENTS_TREE_ID = "input_placements_tree";
    private static final String INPUT_BLOCKS_TREE_ID = "input_blocks_tree";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_PALETTE_ID = "input_palette";
    private static final String INPUT_WEIGHTS_ID = "input_weights";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_PLACEMENTS_TREE_ID = "output_placements_tree";
    private static final String OUTPUT_PALETTE_SIZE_ID = "output_palette_size";
    private static final String OUTPUT_TOTAL_WEIGHT_ID = "output_total_weight";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public WeightedBlockPaletteNode() {
        super(UUID.randomUUID(), "material.basic_assignment.weighted_palette");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Incoming placements to remap through weighted palette", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_PLACEMENTS_TREE_ID, "Block Placements Tree", "Incoming placements grouped by branch", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCKS_TREE_ID, "Blocks Tree", "Block positions grouped by branch", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Palette", "Typed block palette", NodeDataType.BLOCK_PALETTE, this));
        addInputPort(new BasePort(INPUT_WEIGHTS_ID, "Weights", "Optional DOUBLE_LIST override aligned with palette", NodeDataType.DOUBLE_LIST, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Integer seed for deterministic selection", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_TREE_ID, "Block Placements Tree", "Placements grouped by source branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_PALETTE_SIZE_ID, "Palette Size", "Palette entry count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_WEIGHT_ID, "Total Weight", "Sum of active weights", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Assigns weighted random block types by position + seed via RandomOps. Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPaletteData paletteData = BlockPaletteData.requireTyped(inputValues.get(INPUT_PALETTE_ID));
        List<String> palette = new ArrayList<>(paletteData.blockIds());
        int paletteSize = palette.size();
        int seed = BasicAssignmentUtils.resolveSeed(inputValues.get(INPUT_SEED_ID));

        List<Double> weights;
        Object weightsObj = inputValues.get(INPUT_WEIGHTS_ID);
        if (weightsObj != null) {
            if (palette.isEmpty()) {
                emitFail("Palette required when Weights override is connected");
                return;
            }
            BasicAssignmentUtils.ParseResult<Double> weightsResult =
                BasicAssignmentUtils.parseDoubleList(weightsObj, "Weights");
            if (!weightsResult.valid()) {
                emitFail(weightsResult.error());
                return;
            }
            weights = weightsResult.values();
            BasicAssignmentUtils.Validation weightsOk =
                BasicAssignmentUtils.validateWeights(weights, paletteSize);
            if (!weightsOk.valid()) {
                emitFail(weightsOk.message());
                return;
            }
        } else {
            weights = new ArrayList<>(paletteData.weights());
            if (!palette.isEmpty()) {
                BasicAssignmentUtils.Validation paletteWeightsOk =
                    BasicAssignmentUtils.validatePaletteWeights(paletteData);
                if (!paletteWeightsOk.valid()) {
                    emitFail(paletteWeightsOk.message());
                    return;
                }
            }
        }

        double totalWeight = BasicAssignmentUtils.totalWeight(weights);

        Object placementsTreeObj = inputValues.get(INPUT_PLACEMENTS_TREE_ID);
        if (placementsTreeObj instanceof DataTreeData placementsTree && placementsTree.getBranchCount() > 0) {
            writePlacementTreeAssignments(placementsTree, palette, weights, seed, paletteSize, totalWeight);
            return;
        }

        Object blocksTreeObj = inputValues.get(INPUT_BLOCKS_TREE_ID);
        if (blocksTreeObj instanceof DataTreeData blocksTree && blocksTree.getBranchCount() > 0) {
            if (palette.isEmpty()) {
                emitFail("Palette required for blocks tree input");
                return;
            }
            writeBlockTreeAssignments(blocksTree, palette, weights, seed, paletteSize, totalWeight);
            return;
        }

        if (BasicAssignmentUtils.hasPlacementSource(inputValues.get(INPUT_PLACEMENTS_ID))) {
            List<BlockPlacementData> placements = mapFlatPlacements(palette, weights, seed);
            DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), new ArrayList<Object>(placements))
            ));
            emitOk(placements, tree, paletteSize, totalWeight);
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
            List<BlockPlacementData> placements = mapGeometryPlacements(palette, weights, seed);
            if (placements.isEmpty()) {
                emitFail("No geometry or coordinates resolved");
                return;
            }
            DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), new ArrayList<Object>(placements))
            ));
            emitOk(placements, tree, paletteSize, totalWeight);
            return;
        }

        emitFail("No placements, coordinates, geometry, or tree input");
    }

    private List<BlockPlacementData> mapFlatPlacements(List<String> palette, List<Double> weights, int seed) {
        List<BlockPlacementData> remapped = new ArrayList<>();
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (!(placementsObj instanceof List<?> placementList)) {
            return remapped;
        }
        for (Object entry : placementList) {
            if (!(entry instanceof BlockPlacementData placement) || placement.pos() == null) {
                continue;
            }
            String blockId = BasicAssignmentUtils.pickWeightedBlockId(
                placement.pos(), seed, palette, weights, placement.blockId());
            remapped.add(new BlockPlacementData(placement.pos(), blockId, placement.stateData()));
        }
        return remapped;
    }

    private List<BlockPlacementData> mapGeometryPlacements(List<String> palette, List<Double> weights, int seed) {
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
        for (BlockPos pos : positions) {
            String blockId = BasicAssignmentUtils.pickWeightedBlockId(pos, seed, palette, weights, null);
            resolved.add(new BlockPlacementData(pos, blockId));
        }
        return resolved;
    }

    private void writePlacementTreeAssignments(
            DataTreeData placementsTree,
            List<String> palette,
            List<Double> weights,
            int seed,
            int paletteSize,
            double totalWeight
    ) {
        List<BlockPlacementData> placements = new ArrayList<>();
        List<DataTreeData.Branch> placementBranches = new ArrayList<>();

        for (DataTreeData.Branch branch : placementsTree.getBranches()) {
            List<Object> branchPlacements = new ArrayList<>();
            for (Object item : branch.items()) {
                if (item instanceof BlockPlacementData placement && placement.pos() != null) {
                    String blockId = BasicAssignmentUtils.pickWeightedBlockId(
                        placement.pos(), seed, palette, weights, placement.blockId());
                    BlockPlacementData remapped = new BlockPlacementData(
                        placement.pos(), blockId, placement.stateData());
                    placements.add(remapped);
                    branchPlacements.add(remapped);
                }
            }
            placementBranches.add(new DataTreeData.Branch(branch.path(), branchPlacements));
        }

        emitOk(placements, new DataTreeData(placementBranches), paletteSize, totalWeight);
    }

    private void writeBlockTreeAssignments(
            DataTreeData blocksTree,
            List<String> palette,
            List<Double> weights,
            int seed,
            int paletteSize,
            double totalWeight
    ) {
        List<BlockPlacementData> placements = new ArrayList<>();
        List<DataTreeData.Branch> placementBranches = new ArrayList<>();

        for (DataTreeData.Branch branch : blocksTree.getBranches()) {
            List<Object> branchPlacements = new ArrayList<>();
            for (Object item : branch.items()) {
                if (item instanceof BlockPos pos) {
                    String blockId = BasicAssignmentUtils.pickWeightedBlockId(
                        pos, seed, palette, weights, null);
                    BlockPlacementData placement = new BlockPlacementData(pos, blockId);
                    placements.add(placement);
                    branchPlacements.add(placement);
                }
            }
            placementBranches.add(new DataTreeData.Branch(branch.path(), branchPlacements));
        }

        emitOk(placements, new DataTreeData(placementBranches), paletteSize, totalWeight);
    }

    private void emitFail(String message) {
        outputValues.putAll(BasicAssignmentUtils.weightedFailResult(message));
    }

    private void emitOk(
            List<BlockPlacementData> placements,
            DataTreeData tree,
            int paletteSize,
            double totalWeight
    ) {
        outputValues.putAll(BasicAssignmentUtils.weightedOkResult(placements, tree, paletteSize, totalWeight));
    }
}
