package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.linear.instance_on_points",
    displayName = "Instance on Points",
    description = "Instances a block-placement template at each input point.",
    category = "pattern.linear",
    order = 2
)
public class InstanceOnPointsNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_TEMPLATE_PLACEMENTS_ID = "input_template_placements";
    private static final String INPUT_TEMPLATE_ORIGIN_ID = "input_template_origin";
    private static final String INPUT_ENABLED_ID = "input_enabled";
    private static final String INPUT_MAX_INSTANCES_ID = "input_max_instances";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_ANCHORS_ID = "output_anchors";
    private static final String OUTPUT_INSTANCE_COUNT_ID = "output_instance_count";
    private static final String OUTPUT_PLACEMENT_COUNT_ID = "output_placement_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Max Instances", category = "Safety", order = 1)
    private int maxInstances = 4096;

    public InstanceOnPointsNode() {
        super(UUID.randomUUID(), "pattern.linear.instance_on_points");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Anchor points for instances", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_TEMPLATE_PLACEMENTS_ID, "Template Placements", "Local block-placement template to copy at every point", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_TEMPLATE_ORIGIN_ID, "Template Origin", "Local origin subtracted from template coordinates before placement", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_ENABLED_ID, "Enabled", "Whether to generate instances", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_INSTANCES_ID, "Max Instances", "Safety limit for input anchor count", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Instanced block placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ANCHORS_ID, "Anchors", "Resolved anchor block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INSTANCE_COUNT_ID, "Instance Count", "Number of anchor instances used", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENT_COUNT_ID, "Placement Count", "Number of generated block placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether instancing succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Instances a block-placement template at each input point.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (inputValues.get(INPUT_ENABLED_ID) instanceof Boolean enabled && !enabled) {
            writeOutputs(List.of(), new BlockPosList(), 0, true);
            return;
        }

        List<BlockPos> anchors = resolveAnchors(inputValues.get(INPUT_POINTS_ID));
        if (anchors.isEmpty()) {
            writeOutputs(List.of(), new BlockPosList(), 0, false);
            return;
        }

        BlockPos origin = inputValues.get(INPUT_TEMPLATE_ORIGIN_ID) instanceof BlockPos pos ? pos : BlockPos.ORIGIN;
        List<BlockPlacementData> template = resolveTemplate(origin);
        if (template.isEmpty()) {
            writeOutputs(List.of(), new BlockPosList(), 0, false);
            return;
        }

        int anchorLimit = Math.min(
            GenerationLimits.clampPositiveCount(resolveLimit(inputValues.get(INPUT_MAX_INSTANCES_ID), maxInstances)),
            GenerationLimits.clampRepeatCount(anchors.size(), Math.max(1, template.size()))
        );
        if (anchorLimit <= 0) {
            writeOutputs(List.of(), new BlockPosList(), 0, false);
            return;
        }

        List<BlockPlacementData> placements = new ArrayList<>(anchorLimit * template.size());
        BlockPosList usedAnchors = new BlockPosList();

        for (int anchorIndex = 0; anchorIndex < anchorLimit; anchorIndex++) {
            BlockPos anchor = anchors.get(anchorIndex);
            usedAnchors.add(anchor);
            for (BlockPlacementData templatePlacement : template) {
                BlockPos local = templatePlacement.pos();
                if (local == null || templatePlacement.blockId() == null || templatePlacement.blockId().isBlank()) {
                    continue;
                }
                BlockPos outPos = anchor.add(local.getX(), local.getY(), local.getZ());
                placements.add(new BlockPlacementData(outPos, templatePlacement.blockId(), templatePlacement.stateData()));
            }
        }

        writeOutputs(placements, usedAnchors, usedAnchors.size(), !placements.isEmpty());
    }

    private List<BlockPlacementData> resolveTemplate(BlockPos origin) {
        Object placementsObj = inputValues.get(INPUT_TEMPLATE_PLACEMENTS_ID);
        if (!(placementsObj instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<BlockPlacementData> resolved = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof BlockPlacementData placement && placement.pos() != null) {
                BlockPos pos = placement.pos();
                BlockPos local = pos.add(-origin.getX(), -origin.getY(), -origin.getZ());
                resolved.add(new BlockPlacementData(local, placement.blockId(), placement.stateData()));
            }
        }
        return resolved;
    }

    private List<BlockPos> resolveAnchors(Object value) {
        List<Vector3d> points = SpatialValueResolver.resolvePointList(value);
        List<BlockPos> anchors = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            anchors.add(BlockPos.ofFloored(point.x, point.y, point.z));
        }
        return anchors;
    }

    private int resolveLimit(Object value, int fallback) {
        int resolved = value instanceof Integer i ? i : fallback;
        return Math.max(1, resolved);
    }

    private void writeOutputs(List<BlockPlacementData> placements,
                              BlockPosList anchors,
                              int instanceCount,
                              boolean valid) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_ANCHORS_ID, anchors);
        outputValues.put(OUTPUT_INSTANCE_COUNT_ID, instanceCount);
        outputValues.put(OUTPUT_PLACEMENT_COUNT_ID, placements.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public void setMaxInstances(int maxInstances) {
        this.maxInstances = GenerationLimits.clampPositiveCount(maxInstances);
        markDirty();
    }
}
