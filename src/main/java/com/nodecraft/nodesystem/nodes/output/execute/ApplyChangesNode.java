package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.TrackedPreviewPlacementService;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies voxelized geometry or explicit placements to the world.
 */
@NodeInfo(
    id = "output.execute.apply_changes",
    displayName = "Apply Changes",
    description = "Applies explicit placements or voxelized geometry to the world.",
    category = "output.execute",
    order = 0
)
public class ApplyChangesNode extends BaseCustomUINode {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplyChangesNode.class);

    @NodeProperty(displayName = "Show Progress Bar", category = "Execution", order = 1)
    private boolean showProgressBar = true;

    @NodeProperty(displayName = "Notify On Complete", category = "Execution", order = 2)
    private boolean notifyOnComplete = true;

    @NodeProperty(displayName = "Execution Timeout", category = "Execution", order = 3)
    private int executionTimeout = 30;

    @NodeProperty(displayName = "Placement Mode", category = "Placement", order = 4)
    private PlacementMode placementMode = PlacementMode.OVERWRITE;

    @NodeProperty(displayName = "Async Placement", category = "Placement", order = 5)
    private boolean useAsyncBake = true;

    @NodeProperty(displayName = "Record Undo", category = "Placement", order = 6)
    private boolean recordUndo = true;

    @NodeProperty(displayName = "Solid Geometry", category = "Geometry", order = 7)
    private boolean solidGeometry = true;

    private UUID executionId = UUID.randomUUID();
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);
    private volatile float progressPercentage = 0.0f;
    private volatile String statusMessage = "Idle";

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_BLOCK_PLACEMENTS_ID = "input_block_placements";
    private static final String INPUT_PREVIEW_IDS_ID = "input_preview_ids";
    private static final String INPUT_PREVIEW_TRACKING_ID = "input_preview_tracking_id";
    private static final String INPUT_NOTIFY_ID = "input_notify";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_OPERATION_COUNT_ID = "output_operation_count";
    private static final String OUTPUT_EXECUTION_TIME_ID = "output_execution_time";
    private static final String OUTPUT_STATUS_ID = "output_status";

    public ApplyChangesNode() {
        super(UUID.randomUUID(), "output.execute.apply_changes");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Execution trigger", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "Block coordinates to place", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry to voxelize and place", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry to voxelize and place", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry to voxelize and place", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry to voxelize and place", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Fallback block type for uniform placement", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_BLOCK_PLACEMENTS_ID, "Block Placements", "Per-position block assignments", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_PREVIEW_IDS_ID, "Preview IDs (Deprecated)", "Deprecated compatibility input for tracked-world preview commits", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_PREVIEW_TRACKING_ID, "Preview Tracking ID (Deprecated)", "Deprecated compatibility input for tracked-world preview commits", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify On Complete", "Overrides node notification behavior", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether placement succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_OPERATION_COUNT_ID, "Operation Count", "Number of queued or placed blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_EXECUTION_TIME_ID, "Execution Time", "Execution time in milliseconds", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Execution status message", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Applies explicit placements or voxelized geometry to the world.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        int operationCount = 0;
        int executionTime = 0;
        String status = "No operation executed";

        Object triggerObj = inputValues.get(INPUT_TRIGGER_ID);
        Object placementsObj = inputValues.get(INPUT_BLOCK_PLACEMENTS_ID);
        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        Object boxGeometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        Object cylinderGeometryObj = inputValues.get(INPUT_CYLINDER_GEOMETRY_ID);
        Object sphereGeometryObj = inputValues.get(INPUT_SPHERE_GEOMETRY_ID);
        Object torusGeometryObj = inputValues.get(INPUT_TORUS_GEOMETRY_ID);
        Object blockTypeObj = inputValues.get(INPUT_BLOCK_TYPE_ID);
        Object previewTrackingIdObj = inputValues.get(INPUT_PREVIEW_TRACKING_ID);
        Object notifyObj = inputValues.get(INPUT_NOTIFY_ID);

        boolean notify = (notifyObj instanceof Boolean) ? (Boolean) notifyObj : notifyOnComplete;
        String blockType = (blockTypeObj instanceof String) ? (String) blockTypeObj : "minecraft:stone";

        if (triggerObj == null) {
            publishOutputs(success, operationCount, executionTime, status);
            return;
        }

        if (!isExecuting.compareAndSet(false, true)) {
            publishOutputs(false, 0, 0, "Execution already in progress");
            return;
        }
        try {
            if (context == null || context.getWorld() == null) {
                publishOutputs(false, 0, 0, "Missing execution context");
                return;
            }

            List<String> previewTrackingIds = resolvePreviewTrackingIds(previewTrackingIdObj, inputValues.get(INPUT_PREVIEW_IDS_ID));
            if (!previewTrackingIds.isEmpty()) {
                long startTime = System.currentTimeMillis();
                int commitCount = 0;
                for (String previewTrackingId : previewTrackingIds) {
                    if (TrackedPreviewPlacementService.getInstance().commitTrackedPreview(context.getWorld(), previewTrackingId)) {
                        commitCount++;
                    }
                }
                success = commitCount > 0;
                operationCount = commitCount;
                executionTime = (int) (System.currentTimeMillis() - startTime);
                status = success
                        ? "Committed " + commitCount + " tracked preview(s)"
                        : "No tracked previews were committed";
                publishOutputs(success, operationCount, executionTime, status);
                return;
            }

            List<BlockPlacementData> placements = resolvePlacements(placementsObj);
            if (!placements.isEmpty()) {
                long startTime = System.currentTimeMillis();
                progressPercentage = 0.2f;
                statusMessage = "Applying material placements...";
                operationCount = applyPlacementList(context, placements);
                success = true;
                executionTime = (int) (System.currentTimeMillis() - startTime);
                status = useAsyncBake
                    ? "Queued " + operationCount + " block placements"
                    : "Placed " + operationCount + " blocks";
                progressPercentage = 1.0f;
                statusMessage = "Completed";
                if (notify) {
                    LOGGER.info("ApplyChangesNode: {}, {}ms", status, executionTime);
                }
                publishOutputs(success, operationCount, executionTime, status);
                return;
            }

            BlockPosList blocks = resolveBlocks(blocksObj, geometryObj, boxGeometryObj, cylinderGeometryObj, sphereGeometryObj, torusGeometryObj);
            if (blocks.isEmpty()) {
                publishOutputs(false, 0, 0, "No blocks or geometry to apply");
                return;
            }

            BlockState targetState = resolveBlockState(blockType);
            if (targetState == null) {
                publishOutputs(false, 0, 0, "Invalid block type: " + blockType);
                return;
            }

            long startTime = System.currentTimeMillis();
            progressPercentage = 0.2f;
            statusMessage = "Applying blocks...";
            operationCount = applyUniformBlocks(context, blocks, targetState);
            success = true;
            executionTime = (int) (System.currentTimeMillis() - startTime);
            status = useAsyncBake
                ? "Queued " + operationCount + " blocks"
                : "Placed " + operationCount + "/" + blocks.size() + " blocks";
            progressPercentage = 1.0f;
            statusMessage = "Completed";
            if (notify) {
                LOGGER.info("ApplyChangesNode: {}, {}ms", status, executionTime);
            }

            publishOutputs(success, operationCount, executionTime, status);
        } catch (Exception e) {
            status = "Error: " + e.getMessage();
            statusMessage = status;
            LOGGER.error("ApplyChangesNode execution failed", e);
            publishOutputs(success, operationCount, executionTime, status);
        } finally {
            isExecuting.set(false);
        }
    }

    private void publishOutputs(boolean success, int operationCount, int executionTime, String status) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_OPERATION_COUNT_ID, operationCount);
        outputValues.put(OUTPUT_EXECUTION_TIME_ID, executionTime);
        outputValues.put(OUTPUT_STATUS_ID, status);
    }

    private List<BlockPlacementData> resolvePlacements(Object placementsObj) {
        List<BlockPlacementData> placements = new ArrayList<>();
        if (!(placementsObj instanceof List<?> placementList) || placementList.isEmpty()) {
            return placements;
        }

        for (Object entry : placementList) {
            if (entry instanceof BlockPlacementData placement
                && placement.pos() != null
                && placement.blockId() != null
                && !placement.blockId().isEmpty()) {
                placements.add(placement);
            }
        }
        return placements;
    }

    private List<String> resolvePreviewTrackingIds(Object previewTrackingIdObj, Object previewIdsObj) {
        List<String> ids = new ArrayList<>();

        if (previewTrackingIdObj instanceof String previewTrackingId && !previewTrackingId.isBlank()) {
            ids.add(previewTrackingId);
        }

        if (previewIdsObj instanceof List<?> previewIdList) {
            for (Object entry : previewIdList) {
                if (entry instanceof String previewId && !previewId.isBlank()) {
                    ids.add(previewId);
                }
            }
        }

        return ids;
    }

    private BlockPosList resolveBlocks(Object blocksObj, Object geometryObj, Object boxGeometryObj, Object cylinderGeometryObj, Object sphereGeometryObj, Object torusGeometryObj) {
        return GeometryVoxelizer.resolveBlocks(blocksObj, geometryObj, boxGeometryObj, cylinderGeometryObj, sphereGeometryObj, torusGeometryObj, solidGeometry);
    }

    private int applyPlacementList(ExecutionContext context, List<BlockPlacementData> placements) {
        Map<String, List<BlockPlacementData>> byBlockId = new LinkedHashMap<>();
        for (BlockPlacementData placement : placements) {
            byBlockId.computeIfAbsent(placement.blockId(), ignored -> new ArrayList<>()).add(placement);
        }

        int count = 0;
        for (Map.Entry<String, List<BlockPlacementData>> entry : byBlockId.entrySet()) {
            BlockState defaultState = resolveBlockState(entry.getKey());
            if (defaultState == null) {
                continue;
            }

            List<BlockPlacementData> placementBatch = entry.getValue();
            boolean hasStateOverrides = placementBatch.stream().anyMatch(placement -> placement.stateData() != null && !placement.stateData().isEmpty());

            if (useAsyncBake && !hasStateOverrides) {
                List<BlockPos> positions = new ArrayList<>(placementBatch.size());
                for (BlockPlacementData placement : placementBatch) {
                    positions.add(placement.pos().toImmutable());
                }
                BakePlacementService.getInstance().enqueue(
                    context.getWorld(),
                    new ArrayList<>(positions),
                    defaultState,
                    placementMode,
                    recordUndo,
                    1000,
                    null
                );
                count += positions.size();
            } else {
                for (BlockPlacementData placement : placementBatch) {
                    BlockPos pos = placement.pos();
                    if (placementMode == PlacementMode.INCREMENTAL && !context.getWorld().isAir(pos)) {
                        continue;
                    }
                    BlockState state = applyBlockStateData(defaultState, placement.stateData());
                    if (context.getWorld().setBlockState(pos, state, Block.NOTIFY_ALL)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private BlockState applyBlockStateData(BlockState baseState, @Nullable BlockStateData stateData) {
        if (baseState == null || stateData == null || stateData.isEmpty()) {
            return baseState;
        }

        BlockState resolved = baseState;
        for (Map.Entry<String, String> entry : stateData.entrySet()) {
            Property<?> property = resolved.getProperties().stream()
                .filter(candidate -> candidate.getName().equals(entry.getKey()))
                .findFirst()
                .orElse(null);
            if (property == null) {
                continue;
            }
            resolved = applyPropertyValue(resolved, property, entry.getValue());
        }
        return resolved;
    }

    private <T extends Comparable<T>> BlockState applyPropertyValue(BlockState state, Property<T> property, String rawValue) {
        return property.parse(rawValue)
            .map(parsed -> state.with(property, parsed))
            .orElse(state);
    }

    private int applyUniformBlocks(ExecutionContext context, BlockPosList blocks, BlockState targetState) {
        if (useAsyncBake) {
            BakePlacementService.getInstance().enqueue(
                context.getWorld(),
                new ArrayList<>(blocks.getPositions()),
                targetState,
                placementMode,
                recordUndo,
                1000,
                null
            );
            return blocks.size();
        }

        int count = 0;
        for (BlockPos pos : blocks) {
            if (placementMode == PlacementMode.INCREMENTAL && !context.getWorld().isAir(pos)) {
                continue;
            }
            if (context.getWorld().setBlockState(pos.toImmutable(), targetState, Block.NOTIFY_ALL)) {
                count++;
            }
        }
        return count;
    }

    private BlockState resolveBlockState(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }
        try {
            var block = Registries.BLOCK.get(Identifier.of(blockId));
            return block != null ? block.getDefaultState() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected float calculateUIHeight() {
        float h = getMediumPadding();
        h += ImGui.getTextLineHeight();
        h += getSmallPadding();
        if (showProgressBar) {
            h += ImGui.getFrameHeight();
        }
        h += getMediumPadding();
        return h;
    }

    @Override
    protected float calculateMinUIWidth() {
        float statusWidth = ImGui.calcTextSize("Applying material placements...").x;
        return Math.max(176.0f, statusWidth + 20.0f);
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            try {
                float edgeMargin = l.toPixels(getSmallPadding());
                float progressWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
                l.addVerticalSpacing(getMediumPadding());

                int statusColor = isExecuting.get() ? 0xFF44AADD : (progressPercentage >= 1.0f ? 0xFF44DD44 : 0xFF888888);
                ImGui.pushStyleColor(ImGuiCol.Text, statusColor);
                ImGui.text(statusMessage);
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());

                if (showProgressBar) {
                    float baseCursorX = ImGui.getCursorPosX();
                    ImGui.setCursorPosX(baseCursorX + edgeMargin);
                    ImGui.progressBar(progressPercentage, progressWidth, ImGui.getFrameHeight(), String.format("%.0f%%", progressPercentage * 100));
                }

                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                LOGGER.error("ApplyChangesNode UI render failed", e);
            }
            return changed;
        });
    }

    public boolean isShowProgressBar() {
        return showProgressBar;
    }

    public void setShowProgressBar(boolean value) {
        if (showProgressBar != value) {
            showProgressBar = value;
            markDirty();
        }
    }

    public boolean isNotifyOnComplete() {
        return notifyOnComplete;
    }

    public void setNotifyOnComplete(boolean value) {
        if (notifyOnComplete != value) {
            notifyOnComplete = value;
            markDirty();
        }
    }

    public int getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(int value) {
        value = Math.max(5, value);
        if (executionTimeout != value) {
            executionTimeout = value;
            markDirty();
        }
    }

    public float getProgressPercentage() {
        return progressPercentage;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public boolean isExecuting() {
        return isExecuting.get();
    }

    public void resetExecutionId() {
        executionId = UUID.randomUUID();
        markDirty();
    }

    public PlacementMode getPlacementMode() {
        return placementMode;
    }

    public void setPlacementMode(PlacementMode value) {
        if (placementMode != value) {
            placementMode = value;
            markDirty();
        }
    }

    public boolean isUseAsyncBake() {
        return useAsyncBake;
    }

    public void setUseAsyncBake(boolean value) {
        if (useAsyncBake != value) {
            useAsyncBake = value;
            markDirty();
        }
    }

    public boolean isRecordUndo() {
        return recordUndo;
    }

    public void setRecordUndo(boolean value) {
        if (recordUndo != value) {
            recordUndo = value;
            markDirty();
        }
    }

    public boolean isSolidGeometry() {
        return solidGeometry;
    }

    public void setSolidGeometry(boolean value) {
        if (solidGeometry != value) {
            solidGeometry = value;
            markDirty();
        }
    }

    @Override
    public @Nullable Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("showProgressBar", showProgressBar);
        state.put("notifyOnComplete", notifyOnComplete);
        state.put("executionTimeout", executionTimeout);
        state.put("executionId", executionId.toString());
        state.put("placementMode", placementMode.name());
        state.put("useAsyncBake", useAsyncBake);
        state.put("recordUndo", recordUndo);
        state.put("solidGeometry", solidGeometry);
        return state;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        Object value;
        if ((value = map.get("showProgressBar")) instanceof Boolean boolValue) {
            setShowProgressBar(boolValue);
        }
        if ((value = map.get("notifyOnComplete")) instanceof Boolean boolValue) {
            setNotifyOnComplete(boolValue);
        }
        if ((value = map.get("executionTimeout")) instanceof Number numberValue) {
            setExecutionTimeout(numberValue.intValue());
        }
        if ((value = map.get("executionId")) instanceof String idValue) {
            try {
                executionId = UUID.fromString(idValue);
            } catch (IllegalArgumentException e) {
                resetExecutionId();
            }
        }
        if ((value = map.get("placementMode")) instanceof String modeValue) {
            try {
                setPlacementMode(PlacementMode.valueOf(modeValue));
            } catch (Exception ignored) {
            }
        }
        if ((value = map.get("useAsyncBake")) instanceof Boolean boolValue) {
            setUseAsyncBake(boolValue);
        }
        if ((value = map.get("recordUndo")) instanceof Boolean boolValue) {
            setRecordUndo(boolValue);
        }
        if ((value = map.get("solidGeometry")) instanceof Boolean boolValue) {
            setSolidGeometry(boolValue);
        }
    }
}
