package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.material.block_state.BlockStateValidationUtils;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.basic_assignment.weighted_palette",
    displayName = "Weighted Spatial Palette",
    description = "Assigns a spatially coherent weighted material field (value noise + seed). Remaps blockId only; preserves stateData.",
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
        return "Assigns a spatially coherent weighted material field (value noise + seed). Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPaletteData paletteData = BlockPaletteData.requireTyped(inputValues.get(INPUT_PALETTE_ID));
        List<String> palette = new ArrayList<>(paletteData.blockIds());
        int paletteSize = palette.size();
        boolean seedDriven = OptionalPortDrive.isConnected(this, INPUT_SEED_ID) || isInputPresent(INPUT_SEED_ID);
        BasicAssignmentUtils.IndexResult seedResult =
            BasicAssignmentUtils.resolveSeed(inputValues.get(INPUT_SEED_ID), seedDriven);
        if (!seedResult.valid()) {
            emitFail(seedResult.error());
            return;
        }
        int seed = seedResult.index();

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
        if (!weights.isEmpty() && !Double.isFinite(totalWeight)) {
            emitFail("Total weight must be finite");
            return;
        }

        MaterialSourceResolver.SourceResolution source =
            MaterialSourceResolver.resolve(this, SOURCE_PORTS, "");
        if (!source.valid()) {
            emitFail(source.error());
            return;
        }

        switch (source.kind()) {
            case PLACEMENTS_TREE -> writeTreeAssignments(
                source.tree(), palette, weights, seed, paletteSize, totalWeight, true);
            case BLOCKS_TREE -> {
                if (palette.isEmpty()) {
                    emitFail("Palette required for blocks tree input");
                    return;
                }
                writeTreeAssignments(
                    source.tree(), palette, weights, seed, paletteSize, totalWeight, false);
            }
            case PLACEMENTS -> {
                List<BlockPlacementData> placements =
                    mapFlatPlacements(source.placements(), palette, weights, seed);
                if (placements == null) {
                    return;
                }
                emitOk(placements, flatTree(placements), paletteSize, totalWeight);
            }
            case COORDINATES, GEOMETRY -> {
                if (palette.isEmpty()) {
                    emitFail("Palette required for geometry or coordinates input");
                    return;
                }
                List<BlockPlacementData> placements =
                    mapFlatPlacements(source.placements(), palette, weights, seed);
                if (placements == null) {
                    return;
                }
                emitOk(placements, flatTree(placements), paletteSize, totalWeight);
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
            List<Double> weights,
            int seed
    ) {
        List<BlockPlacementData> remapped = new ArrayList<>(sources.size());
        for (BlockPlacementData placement : sources) {
            String blockId = BasicAssignmentUtils.pickWeightedBlockId(
                placement.pos(), seed, palette, weights, placement.blockId());
            BlockPlacementData next = new BlockPlacementData(placement.pos(), blockId, placement.stateData());
            String error = BlockStateValidationUtils.remapIncompatibility(next);
            if (error != null) {
                emitFail(error);
                return null;
            }
            remapped.add(next);
        }
        return remapped;
    }

    private void writeTreeAssignments(
            DataTreeData sourceTree,
            List<String> palette,
            List<Double> weights,
            int seed,
            int paletteSize,
            double totalWeight,
            boolean preserveWhenEmptyPalette
    ) {
        List<BlockPlacementData> placements = new ArrayList<>();
        List<DataTreeData.Branch> placementBranches = new ArrayList<>();

        for (DataTreeData.Branch branch : sourceTree.getBranches()) {
            List<Object> branchPlacements = new ArrayList<>();
            for (Object item : branch.items()) {
                BlockPos pos;
                String preserve;
                BlockPlacementData stateSource = null;
                if (item instanceof BlockPlacementData placement) {
                    pos = placement.pos();
                    preserve = preserveWhenEmptyPalette ? placement.blockId() : null;
                    stateSource = placement;
                } else if (item instanceof BlockPos blockPos) {
                    pos = blockPos;
                    preserve = null;
                } else {
                    continue;
                }
                String blockId = BasicAssignmentUtils.pickWeightedBlockId(
                    pos, seed, palette, weights, preserve);
                BlockPlacementData remapped = stateSource != null
                    ? new BlockPlacementData(pos, blockId, stateSource.stateData())
                    : new BlockPlacementData(pos, blockId);
                String error = BlockStateValidationUtils.remapIncompatibility(remapped);
                if (error != null) {
                    emitFail(error);
                    return;
                }
                placements.add(remapped);
                branchPlacements.add(remapped);
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
