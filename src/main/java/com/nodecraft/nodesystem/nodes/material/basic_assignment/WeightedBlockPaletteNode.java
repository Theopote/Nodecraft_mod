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
    description = "Assigns weighted random block types to flat positions or tree branches.",
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
    private static final String INPUT_FALLBACK_BLOCK_TYPE_ID = "input_fallback_block_type";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_POSITIONS_TREE_ID = "output_positions_tree";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_BLOCK_IDS_TREE_ID = "output_block_ids_tree";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_PLACEMENTS_TREE_ID = "output_placements_tree";
    private static final String OUTPUT_PALETTE_SIZE_ID = "output_palette_size";
    private static final String OUTPUT_TOTAL_WEIGHT_ID = "output_total_weight";

    public WeightedBlockPaletteNode() {
        super(UUID.randomUUID(), "material.basic_assignment.weighted_palette");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to remap through weighted palette", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_PLACEMENTS_TREE_ID, "Block Placements Tree", "Optional incoming placements grouped by branch", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCKS_TREE_ID, "Blocks Tree", "Optional block positions grouped by branch", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Palette", "Typed block palette (weights optional via Weights port)", NodeDataType.BLOCK_PALETTE, this));
        addInputPort(new BasePort(INPUT_WEIGHTS_ID, "Weights", "List of weights aligned with palette entries", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_FALLBACK_BLOCK_TYPE_ID, "Fallback Block Type", "Used when palette is empty", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic random seed", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_TREE_ID, "Positions Tree", "Resolved block positions grouped by source branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block ids aligned with positions", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_TREE_ID, "Block IDs Tree", "Block ids grouped by source branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Position and block pairs for baking", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_TREE_ID, "Block Placements Tree", "Position and block pairs grouped by source branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_PALETTE_SIZE_ID, "Palette Size", "Usable palette entry count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_WEIGHT_ID, "Total Weight", "Sum of usable weights", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Assigns weighted random block types to flat positions or tree branches.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String fallback = getInputString(INPUT_FALLBACK_BLOCK_TYPE_ID, "minecraft:stone");
        int seed = getInputInt(INPUT_SEED_ID, 0);

        List<String> palette = resolvePalette(fallback);
        List<Double> weights = resolveWeights(palette.size());
        double[] cumulative = buildCumulative(weights);
        double totalWeight = cumulative.length == 0 ? 0.0d : cumulative[cumulative.length - 1];

        Object placementsTreeObj = inputValues.get(INPUT_PLACEMENTS_TREE_ID);
        if (placementsTreeObj instanceof DataTreeData placementsTree && placementsTree.getBranchCount() > 0) {
            writePlacementTreeAssignments(placementsTree, palette, cumulative, totalWeight, fallback, seed);
            outputValues.put(OUTPUT_PALETTE_SIZE_ID, palette.size());
            outputValues.put(OUTPUT_TOTAL_WEIGHT_ID, totalWeight);
            return;
        }

        Object blocksTreeObj = inputValues.get(INPUT_BLOCKS_TREE_ID);
        if (blocksTreeObj instanceof DataTreeData blocksTree && blocksTree.getBranchCount() > 0) {
            writeBlockTreeAssignments(blocksTree, palette, cumulative, totalWeight, fallback, seed);
            outputValues.put(OUTPUT_PALETTE_SIZE_ID, palette.size());
            outputValues.put(OUTPUT_TOTAL_WEIGHT_ID, totalWeight);
            return;
        }

        List<BlockPlacementData> base = resolvePlacements(fallback);
        List<BlockPlacementData> placements = new ArrayList<>(base.size());
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(base.size());

        for (int i = 0; i < base.size(); i++) {
            BlockPlacementData placement = base.get(i);
            if (placement.pos() == null) {
                continue;
            }
            String blockId = chooseBlockId(placement.pos(), i, palette, cumulative, totalWeight, fallback, seed);
            placements.add(new BlockPlacementData(placement.pos(), blockId, placement.stateData()));
            positions.add(placement.pos());
            blockIds.add(blockId);
        }

        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_POSITIONS_TREE_ID, new DataTreeData(List.of(new DataTreeData.Branch(List.of(0), new ArrayList<Object>(positions.getPositions())))));
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_BLOCK_IDS_TREE_ID, new DataTreeData(List.of(new DataTreeData.Branch(List.of(0), new ArrayList<Object>(blockIds)))));
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_PLACEMENTS_TREE_ID, new DataTreeData(List.of(new DataTreeData.Branch(List.of(0), new ArrayList<Object>(placements)))));
        outputValues.put(OUTPUT_PALETTE_SIZE_ID, palette.size());
        outputValues.put(OUTPUT_TOTAL_WEIGHT_ID, totalWeight);
    }

    private void writePlacementTreeAssignments(
        DataTreeData placementsTree,
        List<String> palette,
        double[] cumulative,
        double totalWeight,
        String fallback,
        int seed
    ) {
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>();
        List<BlockPlacementData> placements = new ArrayList<>();
        List<DataTreeData.Branch> positionBranches = new ArrayList<>();
        List<DataTreeData.Branch> blockIdBranches = new ArrayList<>();
        List<DataTreeData.Branch> placementBranches = new ArrayList<>();

        int globalIndex = 0;
        for (DataTreeData.Branch branch : placementsTree.getBranches()) {
            List<Object> branchPositions = new ArrayList<>();
            List<Object> branchBlockIds = new ArrayList<>();
            List<Object> branchPlacements = new ArrayList<>();
            for (Object item : branch.items()) {
                if (item instanceof BlockPlacementData placement && placement.pos() != null) {
                    String blockId = chooseBlockId(placement.pos(), globalIndex, palette, cumulative, totalWeight, fallback, seed);
                    BlockPlacementData remapped = new BlockPlacementData(placement.pos(), blockId, placement.stateData());
                    positions.add(remapped.pos());
                    blockIds.add(blockId);
                    placements.add(remapped);
                    branchPositions.add(remapped.pos());
                    branchBlockIds.add(blockId);
                    branchPlacements.add(remapped);
                    globalIndex++;
                }
            }
            positionBranches.add(new DataTreeData.Branch(branch.path(), branchPositions));
            blockIdBranches.add(new DataTreeData.Branch(branch.path(), branchBlockIds));
            placementBranches.add(new DataTreeData.Branch(branch.path(), branchPlacements));
        }

        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_POSITIONS_TREE_ID, new DataTreeData(positionBranches));
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_BLOCK_IDS_TREE_ID, new DataTreeData(blockIdBranches));
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_PLACEMENTS_TREE_ID, new DataTreeData(placementBranches));
    }

    private void writeBlockTreeAssignments(
        DataTreeData blocksTree,
        List<String> palette,
        double[] cumulative,
        double totalWeight,
        String fallback,
        int seed
    ) {
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>();
        List<BlockPlacementData> placements = new ArrayList<>();
        List<DataTreeData.Branch> positionBranches = new ArrayList<>();
        List<DataTreeData.Branch> blockIdBranches = new ArrayList<>();
        List<DataTreeData.Branch> placementBranches = new ArrayList<>();

        int globalIndex = 0;
        for (DataTreeData.Branch branch : blocksTree.getBranches()) {
            List<Object> branchPositions = new ArrayList<>();
            List<Object> branchBlockIds = new ArrayList<>();
            List<Object> branchPlacements = new ArrayList<>();
            for (Object item : branch.items()) {
                if (item instanceof BlockPos pos) {
                    String blockId = chooseBlockId(pos, globalIndex, palette, cumulative, totalWeight, fallback, seed);
                    BlockPlacementData placement = new BlockPlacementData(pos, blockId);
                    positions.add(pos);
                    blockIds.add(blockId);
                    placements.add(placement);
                    branchPositions.add(pos);
                    branchBlockIds.add(blockId);
                    branchPlacements.add(placement);
                    globalIndex++;
                }
            }
            positionBranches.add(new DataTreeData.Branch(branch.path(), branchPositions));
            blockIdBranches.add(new DataTreeData.Branch(branch.path(), branchBlockIds));
            placementBranches.add(new DataTreeData.Branch(branch.path(), branchPlacements));
        }

        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_POSITIONS_TREE_ID, new DataTreeData(positionBranches));
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_BLOCK_IDS_TREE_ID, new DataTreeData(blockIdBranches));
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_PLACEMENTS_TREE_ID, new DataTreeData(placementBranches));
    }

    private List<BlockPlacementData> resolvePlacements(String fallbackBlockId) {
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (placementsObj instanceof List<?> placementList && !placementList.isEmpty()) {
            List<BlockPlacementData> resolved = new ArrayList<>();
            for (Object entry : placementList) {
                if (entry instanceof BlockPlacementData placement && placement.pos() != null) {
                    resolved.add(placement);
                }
            }
            if (!resolved.isEmpty()) {
                return resolved;
            }
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

        List<BlockPlacementData> generated = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            generated.add(new BlockPlacementData(pos, fallbackBlockId));
        }
        return generated;
    }

    private List<String> resolvePalette(String fallback) {
        BlockPaletteData palette = BlockPaletteData.requireTyped(inputValues.get(INPUT_PALETTE_ID))
            .withFallback(fallback);
        return new ArrayList<>(palette.blockIds());
    }

    private List<Double> resolveWeights(int paletteSize) {
        Object weightsObj = inputValues.get(INPUT_WEIGHTS_ID);
        if (weightsObj instanceof List<?> list && !list.isEmpty()) {
            List<Double> weights = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) {
                double w = 1.0d;
                if (i < list.size() && list.get(i) instanceof Number number) {
                    w = Math.max(0.0d, number.doubleValue());
                }
                weights.add(w);
            }
            return normalizeWeights(weights);
        }

        BlockPaletteData palette = BlockPaletteData.requireTyped(inputValues.get(INPUT_PALETTE_ID));
        List<Double> fromPalette = new ArrayList<>(palette.weights());
        while (fromPalette.size() < paletteSize) {
            fromPalette.add(1.0d);
        }
        if (fromPalette.size() > paletteSize) {
            fromPalette = new ArrayList<>(fromPalette.subList(0, paletteSize));
        }
        if (fromPalette.isEmpty()) {
            for (int i = 0; i < paletteSize; i++) {
                fromPalette.add(1.0d);
            }
        }
        return normalizeWeights(fromPalette);
    }

    private List<Double> normalizeWeights(List<Double> weights) {
        boolean allZero = true;
        for (double w : weights) {
            if (w > 0.0d) {
                allZero = false;
                break;
            }
        }
        if (allZero) {
            weights.replaceAll(ignored -> 1.0d);
        }
        return weights;
    }

    private double[] buildCumulative(List<Double> weights) {
        double[] cumulative = new double[weights.size()];
        double sum = 0.0d;
        for (int i = 0; i < weights.size(); i++) {
            sum += Math.max(0.0d, weights.get(i));
            cumulative[i] = sum;
        }
        return cumulative;
    }

    private String chooseBlockId(BlockPos pos, int index, List<String> palette, double[] cumulative, double totalWeight, String fallback, int seed) {
        if (palette.isEmpty() || cumulative.length == 0 || totalWeight <= 0.0d) {
            return fallback;
        }
        double random01 = deterministicRandom01(pos, index, seed);
        double threshold = random01 * totalWeight;
        for (int i = 0; i < cumulative.length; i++) {
            if (threshold <= cumulative[i]) {
                String id = palette.get(i);
                return id == null || id.isBlank() ? fallback : id;
            }
        }
        String id = palette.getLast();
        return id == null || id.isBlank() ? fallback : id;
    }

    private double deterministicRandom01(BlockPos pos, int index, int seed) {
        long hash = 1469598103934665603L;
        hash = mix(hash, pos.getX());
        hash = mix(hash, pos.getY());
        hash = mix(hash, pos.getZ());
        hash = mix(hash, index);
        hash = mix(hash, seed);
        return (double) (hash & 0x7fffffffL) / (double) 0x7fffffffL;
    }

    private long mix(long current, int value) {
        long mixed = current ^ value;
        return mixed * 1099511628211L;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
