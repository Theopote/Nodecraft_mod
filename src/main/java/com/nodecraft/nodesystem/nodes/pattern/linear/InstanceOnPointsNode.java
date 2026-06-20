package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "pattern.linear.instance_on_points",
    displayName = "Instance on Points",
    description = "Instances a block or block-placement template at each input point.",
    category = "pattern.linear",
    order = 5
)
public class InstanceOnPointsNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_TEMPLATE_PLACEMENTS_ID = "input_template_placements";
    private static final String INPUT_TEMPLATE_COORDINATES_ID = "input_template_coordinates";
    private static final String INPUT_TEMPLATE_ORIGIN_ID = "input_template_origin";
    private static final String INPUT_BLOCK_INFO_ID = "input_block_info";
    private static final String INPUT_ENABLED_ID = "input_enabled";
    private static final String INPUT_MAX_INSTANCES_ID = "input_max_instances";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_ANCHORS_ID = "output_anchors";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_INSTANCE_COUNT_ID = "output_instance_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Default Block", category = "Instances", order = 1)
    private String defaultBlockId = "minecraft:stone";

    @NodeProperty(displayName = "Max Instances", category = "Safety", order = 2)
    private int maxInstances = 4096;

    public InstanceOnPointsNode() {
        super(UUID.randomUUID(), "pattern.linear.instance_on_points");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Anchor points or block positions for instances", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_TEMPLATE_PLACEMENTS_ID, "Template Placements", "Local block-placement template to copy at every point", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_TEMPLATE_COORDINATES_ID, "Template Coordinates", "Local block-coordinate template used when placements are absent", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_TEMPLATE_ORIGIN_ID, "Template Origin", "Local origin subtracted from template coordinates before placement", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", "Fallback block id for coordinate templates or single-block instances", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_ENABLED_ID, "Enabled", "Whether to generate instances", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_INSTANCES_ID, "Max Instances", "Safety limit for input anchor count", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Instanced block placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Instanced block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block ids aligned with positions", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ANCHORS_ID, "Anchors", "Resolved anchor block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated block placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_INSTANCE_COUNT_ID, "Instance Count", "Number of anchor instances used", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether instancing succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (inputValues.get(INPUT_ENABLED_ID) instanceof Boolean enabled && !enabled) {
            writeOutputs(List.of(), new BlockPosList(), List.of(), new BlockPosList(), 0, true);
            return;
        }

        List<BlockPos> anchors = resolveAnchors(inputValues.get(INPUT_POINTS_ID));
        int limit = resolveLimit(inputValues.get(INPUT_MAX_INSTANCES_ID), maxInstances);
        if (anchors.isEmpty() || anchors.size() > limit) {
            writeOutputs(List.of(), new BlockPosList(), List.of(), new BlockPosList(anchors), 0, false);
            return;
        }

        BlockPos origin = inputValues.get(INPUT_TEMPLATE_ORIGIN_ID) instanceof BlockPos pos ? pos : BlockPos.ORIGIN;
        String fallbackBlockId = resolveBlockId(inputValues.get(INPUT_BLOCK_INFO_ID), defaultBlockId);
        List<BlockPlacementData> template = resolveTemplate(origin, fallbackBlockId);
        if (template.isEmpty()) {
            template = List.of(new BlockPlacementData(BlockPos.ORIGIN, fallbackBlockId));
        }

        List<BlockPlacementData> placements = new ArrayList<>(anchors.size() * template.size());
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(anchors.size() * template.size());

        for (BlockPos anchor : anchors) {
            for (BlockPlacementData templatePlacement : template) {
                BlockPos local = templatePlacement.pos();
                if (local == null) {
                    continue;
                }
                BlockPos outPos = anchor.add(local.getX(), local.getY(), local.getZ());
                String blockId = resolveBlockId(templatePlacement.blockId(), fallbackBlockId);
                placements.add(new BlockPlacementData(outPos, blockId, templatePlacement.stateData()));
                positions.add(outPos);
                blockIds.add(blockId);
            }
        }

        writeOutputs(placements, positions, blockIds, new BlockPosList(anchors), anchors.size(), true);
    }

    private List<BlockPlacementData> resolveTemplate(BlockPos origin, String fallbackBlockId) {
        Object placementsObj = inputValues.get(INPUT_TEMPLATE_PLACEMENTS_ID);
        if (placementsObj instanceof List<?> list && !list.isEmpty()) {
            List<BlockPlacementData> resolved = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof BlockPlacementData placement && placement.pos() != null) {
                    BlockPos pos = placement.pos();
                    BlockPos local = pos.add(-origin.getX(), -origin.getY(), -origin.getZ());
                    resolved.add(new BlockPlacementData(local, resolveBlockId(placement.blockId(), fallbackBlockId), placement.stateData()));
                }
            }
            return resolved;
        }

        Object coordinatesObj = inputValues.get(INPUT_TEMPLATE_COORDINATES_ID);
        if (coordinatesObj instanceof BlockPosList coordinates && !coordinates.isEmpty()) {
            List<BlockPlacementData> resolved = new ArrayList<>();
            for (BlockPos pos : coordinates) {
                BlockPos local = pos.add(-origin.getX(), -origin.getY(), -origin.getZ());
                resolved.add(new BlockPlacementData(local, fallbackBlockId));
            }
            return resolved;
        }
        return List.of();
    }

    private List<BlockPos> resolveAnchors(Object value) {
        List<BlockPos> anchors = new ArrayList<>();
        if (value instanceof BlockPosList blockPosList) {
            anchors.addAll(blockPosList.getPositions());
        } else if (value instanceof List<?> list) {
            for (Object entry : list) {
                BlockPos pos = resolveBlockPos(entry);
                if (pos != null) {
                    anchors.add(pos);
                }
            }
        } else {
            BlockPos pos = resolveBlockPos(value);
            if (pos != null) {
                anchors.add(pos);
            }
        }
        return anchors;
    }

    private BlockPos resolveBlockPos(Object value) {
        if (value instanceof BlockPos pos) {
            return pos.toImmutable();
        }
        if (value instanceof Vector3d vector) {
            return BlockPos.ofFloored(vector.x, vector.y, vector.z);
        }
        if (value instanceof PointData point) {
            Vector3d vector = point.getPosition();
            return BlockPos.ofFloored(vector.x, vector.y, vector.z);
        }
        return null;
    }

    private String resolveBlockId(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text.contains(":") ? text : "minecraft:" + text;
        }
        return fallback;
    }

    private int resolveLimit(Object value, int fallback) {
        int resolved = value instanceof Number number ? number.intValue() : fallback;
        return Math.max(1, resolved);
    }

    private void writeOutputs(List<BlockPlacementData> placements,
                              BlockPosList positions,
                              List<String> blockIds,
                              BlockPosList anchors,
                              int instanceCount,
                              boolean valid) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_ANCHORS_ID, anchors);
        outputValues.put(OUTPUT_COUNT_ID, placements.size());
        outputValues.put(OUTPUT_INSTANCE_COUNT_ID, instanceCount);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    public String getDefaultBlockId() {
        return defaultBlockId;
    }

    public void setDefaultBlockId(String defaultBlockId) {
        this.defaultBlockId = resolveBlockId(defaultBlockId, "minecraft:stone");
        markDirty();
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public void setMaxInstances(int maxInstances) {
        this.maxInstances = Math.max(1, maxInstances);
        markDirty();
    }
}
