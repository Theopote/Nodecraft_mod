package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockListUtils;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PlacementBlockUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Instances a BLOCK_PLACEMENT_LIST template at each BLOCK_LIST anchor (discrete block domain).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.linear.instance_block_placements",
    displayName = "Instance Block Placements",
    description = "Instances a block-placement template at each block anchor.",
    category = "pattern.linear",
    order = 2
)
public class InstanceBlockPlacementsNode extends AbstractPatternLinearNode {

    private static final String INPUT_ANCHORS_ID = "input_anchors";
    private static final String INPUT_TEMPLATE_PLACEMENTS_ID = "input_template_placements";
    private static final String INPUT_TEMPLATE_ORIGIN_ID = "input_template_origin";
    private static final String INPUT_ENABLED_ID = "input_enabled";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_ANCHORS_ID = "output_anchors";
    private static final String OUTPUT_INSTANCE_COUNT_ID = "output_instance_count";
    private static final String OUTPUT_PLACEMENT_COUNT_ID = "output_placement_count";

    public InstanceBlockPlacementsNode() {
        super(UUID.randomUUID(), "pattern.linear.instance_block_placements");

        addInputPort(new BasePort(INPUT_ANCHORS_ID, "Anchors", "Block positions for instances", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_TEMPLATE_PLACEMENTS_ID, "Template Placements", "Local block-placement template to copy at every anchor", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_TEMPLATE_ORIGIN_ID, "Template Origin", "Local origin subtracted from template coordinates before placement", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_ENABLED_ID, "Enabled", "Whether to generate instances", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Instanced block placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ANCHORS_ID, "Anchors", "Resolved anchor block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INSTANCE_COUNT_ID, "Instance Count", "Number of anchor instances used", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENT_COUNT_ID, "Placement Count", "Number of generated block placements", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Instances a block-placement template at each block anchor.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Boolean enabled = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_ENABLED_ID, true);
        if (enabled == null) {
            writeFail("Enabled connected but invalid");
            return;
        }
        if (!enabled) {
            writeSuccess(List.of(), new BlockPosList(), 0);
            return;
        }

        List<BlockPos> anchors = BlockListUtils.resolveStrictBlockList(inputValues.get(INPUT_ANCHORS_ID));
        if (anchors == null) {
            writeFail("Anchors list is missing or invalid");
            return;
        }
        if (anchors.isEmpty()) {
            writeFail("Anchors list is empty");
            return;
        }

        BlockPos origin = OptionalPortDrive.resolveOptionalBlockPos(this, INPUT_TEMPLATE_ORIGIN_ID, BlockPos.ORIGIN);
        if (origin == null) {
            writeFail("Template Origin connected but invalid");
            return;
        }

        List<BlockPlacementData> template = resolveStrictTemplate(origin);
        if (template == null) {
            return;
        }
        if (template.isEmpty()) {
            writeFail("Template Placements list is empty");
            return;
        }

        long workload = (long) anchors.size() * (long) template.size();
        if (workload > GenerationLimits.MAX_LIST_ELEMENTS) {
            writeFail("Instance workload exceeds MAX_LIST_ELEMENTS (anchors × template)");
            return;
        }

        List<BlockPlacementData> placements = new ArrayList<>((int) workload);
        BlockPosList usedAnchors = new BlockPosList();

        for (BlockPos anchor : anchors) {
            usedAnchors.add(anchor);
            for (BlockPlacementData templatePlacement : template) {
                BlockPos local = templatePlacement.pos();
                BlockPos outPos = PlacementBlockUtils.addBlockPos(anchor, local.getX(), local.getY(), local.getZ());
                if (outPos == null) {
                    writeFail("Block position overflow while placing template");
                    return;
                }
                placements.add(new BlockPlacementData(outPos, templatePlacement.blockId(), templatePlacement.stateData()));
            }
        }

        writeSuccess(placements, usedAnchors, usedAnchors.size());
    }

    /**
     * Strict template resolution. Returns null when the node has already been marked invalid.
     */
    private @Nullable List<BlockPlacementData> resolveStrictTemplate(BlockPos origin) {
        Object placementsObj = inputValues.get(INPUT_TEMPLATE_PLACEMENTS_ID);
        if (!(placementsObj instanceof List<?> list)) {
            writeFail("Template Placements list is missing or invalid");
            return null;
        }
        List<BlockPlacementData> resolved = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof BlockPlacementData placement)) {
                writeFail("Template Placements contains a non-placement element");
                return null;
            }
            if (placement.pos() == null) {
                writeFail("Template Placements contains a null position");
                return null;
            }
            if (placement.blockId() == null || placement.blockId().isBlank()) {
                writeFail("Template Placements contains a blank blockId");
                return null;
            }
            BlockPos local = PlacementBlockUtils.subtractBlockPos(placement.pos(), origin);
            if (local == null) {
                writeFail("Block position overflow while applying Template Origin");
                return null;
            }
            resolved.add(new BlockPlacementData(local, placement.blockId(), placement.stateData()));
        }
        return resolved;
    }

    private void writeFail(String error) {
        markInvalid(error);
        outputValues.put(OUTPUT_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_ANCHORS_ID, new BlockPosList());
        putIntOutputs(0, OUTPUT_INSTANCE_COUNT_ID, OUTPUT_PLACEMENT_COUNT_ID);
    }

    private void writeSuccess(List<BlockPlacementData> placements, BlockPosList anchors, int instanceCount) {
        markSuccess();
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_ANCHORS_ID, anchors);
        putIntOutputs(instanceCount, OUTPUT_INSTANCE_COUNT_ID);
        putIntOutputs(placements.size(), OUTPUT_PLACEMENT_COUNT_ID);
    }

    @Override
    public Object getNodeState() {
        return null;
    }

    @Override
    public void setNodeState(Object state) {
        // No persisted properties.
    }
}
