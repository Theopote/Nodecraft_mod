package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Merges block placement lists and placement trees into execution-ready outputs.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "output.execute.merge_block_placements",
    displayName = "Merge Block Placements",
    description = "Merges block placement lists and placement trees into execution-ready placements",
    category = "output.execute",
    order = 13
)
public class MergeBlockPlacementsNode extends BaseNode {

    private static final int MIN_INPUT_COUNT = 2;
    private static final int MAX_INPUT_COUNT = 16;

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_PLACEMENTS_TREE_ID = "output_placements_tree";
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_COUNT_ID = "output_count";

    @NodeProperty(
        displayName = "Input Count",
        category = "Settings",
        order = 1,
        description = "Number of placement list inputs to expose."
    )
    private int inputCount = 4;

    @NodeProperty(
        displayName = "Last Wins",
        category = "Merge",
        order = 2,
        description = "When enabled, later entries replace earlier placements at the same position."
    )
    private boolean lastWins = true;

    @NodeProperty(
        displayName = "Merge State Data",
        category = "Merge",
        order = 3,
        description = "When overwriting duplicates, merge state overrides instead of dropping earlier state data."
    )
    private boolean mergeStateData = true;

    public MergeBlockPlacementsNode() {
        super(UUID.randomUUID(), "output.execute.merge_block_placements");
        rebuildInputPorts();
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Merged placement list", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_TREE_ID, "Block Placements Tree", "Merged placements grouped by source branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Merged placement positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block ids aligned with the merged placement list", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of merged placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort("output_duplicate_count", "Duplicate Count", "Number of duplicate positions encountered while merging", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort("output_conflict_count", "Conflict Count", "Number of duplicate positions whose block id or state data differed", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        MergeResult mergeResult = lastWins ? mergeWithOverwrite() : mergeByConcatenation();
        List<BlockPlacementData> merged = mergeResult.placements();
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(merged.size());

        for (BlockPlacementData placement : merged) {
            if (placement.pos() != null) {
                positions.add(placement.pos());
            }
            blockIds.add(placement.blockId());
        }

        outputValues.put(OUTPUT_PLACEMENTS_ID, merged);
        outputValues.put(OUTPUT_PLACEMENTS_TREE_ID, mergeResult.placementsTree());
        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_COUNT_ID, merged.size());
        outputValues.put("output_duplicate_count", mergeResult.duplicateCount());
        outputValues.put("output_conflict_count", mergeResult.conflictCount());
    }

    private MergeResult mergeByConcatenation() {
        List<PlacementEntry> merged = new ArrayList<>();
        int duplicateCount = 0;
        int conflictCount = 0;
        Map<BlockPos, BlockPlacementData> seen = new HashMap<>();

        for (PlacementEntry entry : collectPlacementEntries()) {
            BlockPlacementData placement = entry.placement();
            BlockPlacementData existing = seen.get(placement.pos());
            if (existing != null) {
                duplicateCount++;
                if (isConflict(existing, placement)) {
                    conflictCount++;
                }
            } else {
                seen.put(placement.pos(), placement);
            }
            merged.add(entry);
        }
        return new MergeResult(toPlacements(merged), buildPlacementTree(merged), duplicateCount, conflictCount);
    }

    private MergeResult mergeWithOverwrite() {
        Map<BlockPos, PlacementEntry> merged = new LinkedHashMap<>();
        int duplicateCount = 0;
        int conflictCount = 0;

        for (PlacementEntry entry : collectPlacementEntries()) {
            BlockPlacementData placement = entry.placement();
            PlacementEntry existingEntry = merged.get(placement.pos());
            if (existingEntry != null) {
                duplicateCount++;
                if (isConflict(existingEntry.placement(), placement)) {
                    conflictCount++;
                }
                BlockPlacementData resolved = mergeStateData ? mergePlacements(existingEntry.placement(), placement) : placement;
                merged.put(placement.pos(), new PlacementEntry(resolved, entry.path()));
            } else {
                merged.put(placement.pos(), entry);
            }
        }
        List<PlacementEntry> entries = new ArrayList<>(merged.values());
        return new MergeResult(toPlacements(entries), buildPlacementTree(entries), duplicateCount, conflictCount);
    }

    private List<PlacementEntry> collectPlacementEntries() {
        List<PlacementEntry> entries = new ArrayList<>();
        for (int i = 0; i < inputCount; i++) {
            collectListPlacements(inputValues.get(inputPortId(i)), List.of(i), entries);
            collectTreePlacements(inputValues.get(treeInputPortId(i)), entries);
        }
        return entries;
    }

    private void collectListPlacements(Object value, List<Integer> path, List<PlacementEntry> entries) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof BlockPlacementData placement && isValidPlacement(placement)) {
                entries.add(new PlacementEntry(placement, path));
            }
        }
    }

    private void collectTreePlacements(Object value, List<PlacementEntry> entries) {
        if (!(value instanceof DataTreeData tree) || tree.getBranchCount() == 0) {
            return;
        }
        for (DataTreeData.Branch branch : tree.getBranches()) {
            for (Object item : branch.items()) {
                if (item instanceof BlockPlacementData placement && isValidPlacement(placement)) {
                    entries.add(new PlacementEntry(placement, branch.path()));
                }
            }
        }
    }

    private List<BlockPlacementData> toPlacements(List<PlacementEntry> entries) {
        List<BlockPlacementData> placements = new ArrayList<>(entries.size());
        for (PlacementEntry entry : entries) {
            placements.add(entry.placement());
        }
        return placements;
    }

    private DataTreeData buildPlacementTree(List<PlacementEntry> entries) {
        Map<List<Integer>, List<Object>> byPath = new LinkedHashMap<>();
        for (PlacementEntry entry : entries) {
            byPath.computeIfAbsent(entry.path(), ignored -> new ArrayList<>()).add(entry.placement());
        }
        List<DataTreeData.Branch> branches = new ArrayList<>(byPath.size());
        for (Map.Entry<List<Integer>, List<Object>> entry : byPath.entrySet()) {
            branches.add(new DataTreeData.Branch(entry.getKey(), entry.getValue()));
        }
        return new DataTreeData(branches);
    }

    private void rebuildInputPorts() {
        inputPorts.clear();
        for (int i = 0; i < inputCount; i++) {
            addInputPort(new BasePort(
                inputPortId(i),
                "Placements " + (i + 1),
                "Block placement list " + (i + 1),
                NodeDataType.BLOCK_PLACEMENT_LIST,
                this
            ));
            addInputPort(new BasePort(
                treeInputPortId(i),
                "Placements Tree " + (i + 1),
                "Block placement tree " + (i + 1),
                NodeDataType.DATA_TREE,
                this
            ));
        }
    }

    private String inputPortId(int index) {
        return "input_placements_" + index;
    }

    private String treeInputPortId(int index) {
        return "input_placements_tree_" + index;
    }

    public int getInputCount() {
        return inputCount;
    }

    public void setInputCount(int inputCount) {
        int resolved = Math.max(MIN_INPUT_COUNT, Math.min(MAX_INPUT_COUNT, inputCount));
        if (this.inputCount != resolved) {
            this.inputCount = resolved;
            rebuildInputPorts();
            markDirty();
        }
    }

    public boolean isLastWins() {
        return lastWins;
    }

    public void setLastWins(boolean lastWins) {
        if (this.lastWins != lastWins) {
            this.lastWins = lastWins;
            markDirty();
        }
    }

    public boolean isMergeStateData() {
        return mergeStateData;
    }

    public void setMergeStateData(boolean mergeStateData) {
        if (this.mergeStateData != mergeStateData) {
            this.mergeStateData = mergeStateData;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("inputCount", inputCount);
        state.put("lastWins", lastWins);
        state.put("mergeStateData", mergeStateData);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("inputCount") instanceof Number value) {
            setInputCount(value.intValue());
        }
        if (map.get("lastWins") instanceof Boolean value) {
            setLastWins(value);
        }
        if (map.get("mergeStateData") instanceof Boolean value) {
            setMergeStateData(value);
        }
    }

    private boolean isValidPlacement(BlockPlacementData placement) {
        return placement != null
            && placement.pos() != null
            && placement.blockId() != null
            && !placement.blockId().isBlank();
    }

    private boolean isConflict(BlockPlacementData existing, BlockPlacementData incoming) {
        return !Objects.equals(existing.blockId(), incoming.blockId())
            || !Objects.equals(existing.stateData(), incoming.stateData());
    }

    private BlockPlacementData mergePlacements(BlockPlacementData existing, BlockPlacementData incoming) {
        String blockId = incoming.blockId() != null && !incoming.blockId().isBlank()
            ? incoming.blockId()
            : existing.blockId();

        BlockStateData mergedState = null;
        boolean preserveExistingState = Objects.equals(blockId, existing.blockId());
        if (preserveExistingState && existing.stateData() != null) {
            mergedState = existing.stateData().copy();
        }
        if (incoming.stateData() != null) {
            mergedState = mergedState != null ? mergedState : new BlockStateData();
            mergedState.putAll(incoming.stateData());
        }
        return new BlockPlacementData(incoming.pos(), blockId, mergedState);
    }

    private record PlacementEntry(BlockPlacementData placement, List<Integer> path) {
        private PlacementEntry {
            path = List.copyOf(path);
        }
    }

private record MergeResult(List<BlockPlacementData> placements, DataTreeData placementsTree, int duplicateCount, int conflictCount) {
    }
}
