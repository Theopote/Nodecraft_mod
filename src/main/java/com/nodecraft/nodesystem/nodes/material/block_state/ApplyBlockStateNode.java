package com.nodecraft.nodesystem.nodes.material.block_state;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Merges block-state overrides into existing placements without changing block ids or positions.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.block_state.apply_block_state",
    displayName = "Apply Block State",
    description = "Merges block-state overrides into existing block placements",
    category = "material.block_state",
    order = 2
)
public class ApplyBlockStateNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_BLOCK_STATE_ID = "input_block_state";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ApplyBlockStateNode() {
        super(UUID.randomUUID(), "material.block_state.apply_block_state");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Incoming placements to enrich with state overrides", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_STATE_ID, "Block State", "State property overrides merged into each placement", NodeDataType.BLOCK_STATE_DATA, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Placements with merged block-state overrides", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when placements and state inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Merges block-state overrides into existing block placements";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        MaterialMappingSupport.PlacementListResult parsed =
            MaterialMappingSupport.parsePlacementsStrict(inputValues.get(INPUT_PLACEMENTS_ID));
        if (!parsed.valid()) {
            emitFail(parsed.error());
            return;
        }

        BlockStateData override = inputValues.get(INPUT_BLOCK_STATE_ID) instanceof BlockStateData inputState
            ? inputState.copy()
            : null;
        if (override != null) {
            BlockStateValidationUtils.stripIdentityKeys(override);
        }

        List<BlockPlacementData> resolved = new ArrayList<>(parsed.placements().size());
        for (BlockPlacementData placement : parsed.placements()) {
            BlockStateData merged = override != null
                ? BlockStateValidationUtils.mergeStateData(placement.stateData(), override)
                : placement.stateData();
            resolved.add(new BlockPlacementData(placement.pos(), placement.blockId(), merged));
        }

        emitOk(resolved);
    }

    private void emitFail(String error) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private void emitOk(List<BlockPlacementData> placements) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }
}
