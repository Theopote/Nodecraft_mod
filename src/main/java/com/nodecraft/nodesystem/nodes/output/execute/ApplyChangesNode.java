package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.bake.BakeTask;
import com.nodecraft.nodesystem.bake.BakeTaskState;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateResolver;
import com.nodecraft.nodesystem.util.GeometryVoxelizationResult;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.PlacementPreflight;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Submits block placements to the world synchronously or via the async bake queue.
 * <p>This node reports submit/queue status only. Monitor running bake tasks with dedicated
 * downstream nodes such as {@link BakeStatusNode} that read {@link BakePlacementService#getTaskSnapshots()}.</p>
 */
@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "output.execute.apply_changes",
    displayName = "Apply Changes",
    description = "Submits explicit placements, placement trees, or voxelized geometry to the world. Async mode queues a single bake task and returns its task ID.",
    category = "output.execute",
    order = 0
)
public class ApplyChangesNode extends BaseCustomUINode {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplyChangesNode.class);

    @NodeProperty(displayName = "Show Submit Status UI", category = "Execution", order = 1)
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

    @NodeProperty(displayName = "Blocks Per Tick", category = "Performance", order = 8)
    private int blocksPerTick = BakePlacementService.DEFAULT_BLOCKS_PER_TICK;

    @NodeProperty(displayName = "Tick Budget Ms", category = "Performance", order = 9)
    private int tickBudgetMillis = 4;

    private UUID executionId = UUID.randomUUID();
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);
    private final AtomicBoolean applyRequested = new AtomicBoolean(false);
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
    private static final String INPUT_BLOCK_PLACEMENTS_TREE_ID = "input_block_placements_tree";
    private static final String INPUT_NOTIFY_ID = "input_notify";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_OPERATION_COUNT_ID = "output_operation_count";
    private static final String OUTPUT_EXECUTION_TIME_ID = "output_execution_time";
    private static final String OUTPUT_STATUS_ID = "output_status";
    private static final String OUTPUT_TASK_ID = "output_task_id";
    private static final String OUTPUT_IS_ASYNC = "output_is_async";

    public ApplyChangesNode() {
        super(UUID.randomUUID(), "output.execute.apply_changes");
        addInputPort(new BasePort(
            INPUT_TRIGGER_ID,
            "Trigger",
            "EXEC pulse to submit world write (manual Apply also works)",
            NodeDataType.EXEC,
            this,
            false,
            false
        ));
        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "Block coordinates to place", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry to voxelize and place", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry to voxelize and place", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry to voxelize and place", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry to voxelize and place", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Fallback block type for uniform placement", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_BLOCK_PLACEMENTS_ID, "Block Placements", "Per-position block assignments", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_PLACEMENTS_TREE_ID, "Block Placements Tree", "Tree-grouped per-position block assignments", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify On Complete", "Overrides node notification behavior", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether placement succeeded or was queued", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_OPERATION_COUNT_ID, "Operation Count", "Number of blocks placed (sync) or queued (async)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_EXECUTION_TIME_ID, "Execution Time", "Execution time in milliseconds (queueing time for async)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Execution status message", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_TASK_ID, "Task ID", "Bake task UUID for this Apply Changes submit", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_ASYNC, "Is Async", "Whether the operation was queued asynchronously (false when sync awaited completion)", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        int operationCount = 0;
        int executionTime = 0;
        String status = "No operation executed";

        Object triggerObj = inputValues.get(INPUT_TRIGGER_ID);
        Object placementsObj = inputValues.get(INPUT_BLOCK_PLACEMENTS_ID);
        Object placementsTreeObj = inputValues.get(INPUT_BLOCK_PLACEMENTS_TREE_ID);
        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        Object boxGeometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        Object cylinderGeometryObj = inputValues.get(INPUT_CYLINDER_GEOMETRY_ID);
        Object sphereGeometryObj = inputValues.get(INPUT_SPHERE_GEOMETRY_ID);
        Object torusGeometryObj = inputValues.get(INPUT_TORUS_GEOMETRY_ID);
        Object blockTypeObj = inputValues.get(INPUT_BLOCK_TYPE_ID);
        Object notifyObj = inputValues.get(INPUT_NOTIFY_ID);

        boolean notify = notifyObj != null ? coerceBoolean(notifyObj) : notifyOnComplete;
        String blockType = (blockTypeObj instanceof String) ? (String) blockTypeObj : "minecraft:stone";

        boolean manualTrigger = applyRequested.getAndSet(false);
        boolean hasExecPulse = Boolean.TRUE.equals(triggerObj);
        if (!hasExecPulse && !manualTrigger) {
            publishOutputs(success, operationCount, executionTime, status, "", false);
            return;
        }

        if (!isExecuting.compareAndSet(false, true)) {
            publishOutputs(false, 0, 0, "Execution already in progress", "", false);
            return;
        }
        try {
            if (context == null || context.getWorld() == null) {
                publishOutputs(false, 0, 0, "Missing execution context", "", false);
                return;
            }

            PlacementPreflight.Result preflight =
                    PlacementPreflight.preflightSources(placementsObj, placementsTreeObj);
            if (!preflight.valid()) {
                publishOutputs(false, 0, 0, formatPreflightError(preflight.error()), "", false);
                return;
            }
            if (preflight.hasPlacements()) {
                long startTime = System.currentTimeMillis();
                progressPercentage = 0.2f;
                statusMessage = "Applying material placements...";
                ApplyResult applyResult = applyPreflightedPlacements(
                        context, preflight.resolved(), startTime + executionTimeout * 1000L);
                publishApplyResult(applyResult, notify, startTime, preflight.resolved().size());
                return;
            }

            GeometryVoxelizationResult voxelResult = resolveBlocksStrict(
                    blocksObj, geometryObj, boxGeometryObj, cylinderGeometryObj, sphereGeometryObj, torusGeometryObj);
            if (!voxelResult.success()) {
                publishOutputs(false, 0, 0, formatVoxelError(voxelResult), "", false);
                return;
            }
            BlockPosList blocks = voxelResult.blocks();
            if (blocks.isEmpty()) {
                publishOutputs(false, 0, 0, "No blocks or geometry to apply", "", false);
                return;
            }

            BlockState targetState = BlockStateResolver.resolveDefault(blockType);
            if (targetState == null) {
                publishOutputs(false, 0, 0, "Invalid block type: " + blockType, "", false);
                return;
            }

            long startTime = System.currentTimeMillis();
            progressPercentage = 0.2f;
            statusMessage = "Applying blocks...";
            ApplyResult applyResult = applyUniformBlocks(context, blocks, targetState, startTime + executionTimeout * 1000L);
            publishApplyResult(applyResult, notify, startTime, blocks.size());
        } catch (Exception e) {
            status = "Error: " + e.getMessage();
            statusMessage = status;
            LOGGER.error("ApplyChangesNode execution failed", e);
            publishOutputs(success, operationCount, executionTime, status, "", false);
        } finally {
            isExecuting.set(false);
        }
    }

    private void publishOutputs(boolean success, int operationCount, int executionTime, String status, String taskId, boolean isAsync) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_OPERATION_COUNT_ID, operationCount);
        outputValues.put(OUTPUT_EXECUTION_TIME_ID, executionTime);
        outputValues.put(OUTPUT_STATUS_ID, status);
        outputValues.put(OUTPUT_TASK_ID, taskId);
        outputValues.put(OUTPUT_IS_ASYNC, isAsync);
    }

    private boolean coerceBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            if (normalized.isEmpty()) {
                return false;
            }
            return switch (normalized.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "1", "on" -> true;
                default -> false;
            };
        }
        return true;
    }

    private GeometryVoxelizationResult resolveBlocksStrict(Object blocksObj,
                                                           Object geometryObj,
                                                           Object boxGeometryObj,
                                                           Object cylinderGeometryObj,
                                                           Object sphereGeometryObj,
                                                           Object torusGeometryObj) {
        return GeometryVoxelizer.resolveBlocksStrict(
                blocksObj, geometryObj, boxGeometryObj, cylinderGeometryObj, sphereGeometryObj, torusGeometryObj, solidGeometry);
    }

    private ApplyResult applyPreflightedPlacements(ExecutionContext context,
                                                   List<PlacementPreflight.ResolvedPlacement> resolved,
                                                   long deadlineMillis) {
        List<BakeTask.Placement> queuedPlacements = new ArrayList<>(resolved.size());
        for (PlacementPreflight.ResolvedPlacement placement : resolved) {
            queuedPlacements.add(new BakeTask.Placement(placement.pos(), placement.state()));
        }
        return enqueueAndMaybeAwait(context, queuedPlacements, deadlineMillis);
    }

    private ApplyResult applyUniformBlocks(ExecutionContext context, BlockPosList blocks, BlockState targetState, long deadlineMillis) {
        List<BakeTask.Placement> queuedPlacements = new ArrayList<>(blocks.size());
        for (BlockPos pos : blocks) {
            if (pos != null) {
                queuedPlacements.add(new BakeTask.Placement(pos.toImmutable(), targetState));
            }
        }
        if (queuedPlacements.isEmpty()) {
            return ApplyResult.rejected();
        }
        return enqueueAndMaybeAwait(context, queuedPlacements, deadlineMillis);
    }

    /**
     * All Apply Changes writes go through the bake pipeline so undo, placement mode,
     * and profiling share one code path. Sync mode is enqueue + await completion.
     */
    private ApplyResult enqueueAndMaybeAwait(ExecutionContext context,
                                             List<BakeTask.Placement> queuedPlacements,
                                             long deadlineMillis) {
        if (queuedPlacements.isEmpty()) {
            return ApplyResult.rejected();
        }

        BakePlacementService service = BakePlacementService.getInstance();
        UUID taskId = service.enqueuePlacements(
            context.getWorld(),
            queuedPlacements,
            placementMode,
            recordUndo,
            blocksPerTick,
            tickBudgetNanos(),
            BakePlacementService.resolveActorId(context.getPlayer()),
            null
        );

        if (taskId == null) {
            return ApplyResult.rejected();
        }

        if (useAsyncBake) {
            return ApplyResult.queued(queuedPlacements.size(), taskId);
        }

        BakePlacementService.AwaitResult await = context.callOnWorldThread(() ->
            service.awaitTaskTerminalState(taskId, deadlineMillis)
        );
        if (await == null) {
            return ApplyResult.failed(0, taskId, null);
        }

        if (await.completed()) {
            BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(taskId);
            int placed = snapshot != null ? snapshot.placedCount() : queuedPlacements.size();
            return ApplyResult.completed(placed, taskId);
        }

        BakeTaskState terminal = await.state();
        if (terminal != null && terminal.isTerminal() && terminal != BakeTaskState.COMPLETED) {
            BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(taskId);
            int placed = snapshot != null ? snapshot.placedCount() : 0;
            return ApplyResult.fromTerminalState(placed, taskId, terminal);
        }

        if (await.deadlineExceeded()) {
            Boolean aborted = context.callOnWorldThread(() -> {
                service.cancelTask(taskId, BakeTaskState.TIMED_OUT);
                return service.awaitTaskAborted(taskId, deadlineMillis);
            });
            BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(taskId);
            int placed = snapshot != null ? snapshot.placedCount() : 0;
            if (!Boolean.TRUE.equals(aborted)) {
                LOGGER.warn("ApplyChangesNode: timed out waiting for bake rollback of task {}", taskId);
            }
            BakeTaskState finalState = snapshot != null ? snapshot.state() : BakeTaskState.TIMED_OUT;
            return ApplyResult.fromTerminalState(placed, taskId, finalState);
        }

        BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(taskId);
        int placed = snapshot != null ? snapshot.placedCount() : 0;
        BakeTaskState state = snapshot != null ? snapshot.state() : null;
        return ApplyResult.fromTerminalState(placed, taskId, state != null ? state : BakeTaskState.FAILED);
    }

    private void publishApplyResult(ApplyResult applyResult, boolean notify, long startTime, int requestedCount) {
        int executionTime = (int) (System.currentTimeMillis() - startTime);
        String taskId = applyResult.taskId() != null ? applyResult.taskId().toString() : "";
        boolean isAsync = useAsyncBake && applyResult.outcome() == SubmitOutcome.QUEUED;
        String status = formatApplyStatus(applyResult, requestedCount);

        if (applyResult.outcome() == SubmitOutcome.TIMED_OUT) {
            progressPercentage = 0.0f;
            statusMessage = "Timed out";
        } else if (applyResult.outcome() == SubmitOutcome.QUEUED) {
            progressPercentage = 0.0f;
            statusMessage = taskId.isEmpty() ? "Submitted" : "Submitted (Task: " + taskId + ")";
        } else if (applyResult.outcome() == SubmitOutcome.COMPLETED) {
            progressPercentage = 1.0f;
            statusMessage = "Completed";
        } else if (applyResult.outcome() == SubmitOutcome.ROLLBACK_FAILED) {
            progressPercentage = 0.0f;
            statusMessage = "Rollback failed";
        } else {
            progressPercentage = 0.0f;
            statusMessage = applyResult.outcome().displayName();
        }

        if (notify) {
            LOGGER.info("ApplyChangesNode: {}, {}ms", status, executionTime);
        }
        publishOutputs(applyResult.success(), applyResult.operationCount(), executionTime, status, taskId, isAsync);
    }

    private String formatPreflightError(String error) {
        if (PlacementPreflight.ERROR_UNRESOLVABLE_BLOCK.equals(error)
                || (error != null && error.startsWith(PlacementPreflight.ERROR_UNRESOLVABLE_BLOCK))) {
            return "Invalid block placement: " + error;
        }
        if (PlacementPreflight.ERROR_INVALID_ENTRY.equals(error)) {
            return "Invalid block placement entry";
        }
        if (PlacementPreflight.ERROR_INVALID_INPUT.equals(error)) {
            return "Invalid block placement input";
        }
        return error == null || error.isEmpty() ? "Invalid block placements" : error;
    }

    private String formatVoxelError(GeometryVoxelizationResult result) {
        String detail = result.error();
        if (detail == null || detail.isBlank()) {
            return "Geometry voxelization failed (" + result.status() + ")";
        }
        return "Geometry voxelization failed: " + detail;
    }

    private String formatApplyStatus(ApplyResult result, int requestedCount) {
        return switch (result.outcome()) {
            case REJECTED -> "Submit rejected: no bake task was created";
            case QUEUED -> "Submitted " + result.operationCount() + " block placements (task " + result.taskId() + ")";
            case COMPLETED -> "Placed " + result.operationCount()
                    + (requestedCount > 0 ? "/" + requestedCount : "") + " blocks (synchronous)";
            case FAILED -> "Bake task failed after placing " + result.operationCount() + " blocks";
            case TIMED_OUT -> "Timed out after " + executionTimeout + "s; placed " + result.operationCount()
                    + (requestedCount > 0 ? "/" + requestedCount : "") + " blocks";
            case CANCELLED -> "Bake task cancelled after placing " + result.operationCount() + " blocks";
            case ROLLBACK_FAILED -> "Bake rollback failed — world may be inconsistent (placed "
                    + result.operationCount() + " blocks)";
        };
    }

    @Override
    protected float calculateUIHeight() {
        float h = getMediumPadding();
        h += ImGui.getFrameHeight();
        h += getSmallPadding();
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
            try {
                float edgeMargin = l.toPixels(getSmallPadding());
                float progressWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
                float buttonWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
                l.addVerticalSpacing(getMediumPadding());

                boolean disabled = isExecuting.get();
                if (disabled) {
                    ImGui.beginDisabled();
                }
                float baseCursorX = ImGui.getCursorPosX();
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                boolean clicked = ImGui.button("Apply Changes", buttonWidth, ImGui.getFrameHeight());
                if (disabled) {
                    ImGui.endDisabled();
                }
                if (clicked) {
                    requestApply();
                }
                l.addVerticalSpacing(getSmallPadding());

                int statusColor = isExecuting.get()
                    ? 0xFF44AADD
                    : (progressPercentage >= 1.0f ? 0xFF44DD44 : 0xFF888888);
                ImGui.pushStyleColor(ImGuiCol.Text, statusColor);
                ImGui.text(statusMessage);
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());

                if (showProgressBar && (!useAsyncBake || progressPercentage >= 1.0f)) {
                    float progressCursorX = ImGui.getCursorPosX();
                    ImGui.setCursorPosX(progressCursorX + edgeMargin);
                    ImGui.progressBar(progressPercentage, progressWidth, ImGui.getFrameHeight(), String.format("%.0f%%", progressPercentage * 100));
                } else if (showProgressBar && useAsyncBake && !isExecuting.get() && progressPercentage < 1.0f) {
                    float hintCursorX = ImGui.getCursorPosX();
                    ImGui.setCursorPosX(hintCursorX + edgeMargin);
                    ImGui.textDisabled("Monitor bake progress with a Bake Status node");
                }

                l.addVerticalSpacing(getMediumPadding());
                return clicked;
            } catch (Exception e) {
                LOGGER.error("ApplyChangesNode UI render failed", e);
            }
            return false;
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

    public void requestApply() {
        applyRequested.set(true);
        executionId = UUID.randomUUID();
        progressPercentage = 0.0f;
        statusMessage = "Ready";
        markDirty();
    }

    public void resetExecutionId() {
        requestApply();
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

    public int getBlocksPerTick() {
        return blocksPerTick;
    }

    public void setBlocksPerTick(int value) {
        int resolved = Math.max(1, value);
        if (blocksPerTick != resolved) {
            blocksPerTick = resolved;
            markDirty();
        }
    }

    public int getTickBudgetMillis() {
        return tickBudgetMillis;
    }

    public void setTickBudgetMillis(int value) {
        int resolved = Math.max(1, value);
        if (tickBudgetMillis != resolved) {
            tickBudgetMillis = resolved;
            markDirty();
        }
    }

    private long tickBudgetNanos() {
        return Math.max(1L, tickBudgetMillis) * 1_000_000L;
    }

    private enum SubmitOutcome {
        REJECTED,
        QUEUED,
        COMPLETED,
        FAILED,
        TIMED_OUT,
        CANCELLED,
        ROLLBACK_FAILED;

        String displayName() {
            return switch (this) {
                case REJECTED -> "Rejected";
                case QUEUED -> "Queued";
                case COMPLETED -> "Completed";
                case FAILED -> "Failed";
                case TIMED_OUT -> "Timed out";
                case CANCELLED -> "Cancelled";
                case ROLLBACK_FAILED -> "Rollback failed";
            };
        }
    }

    private record ApplyResult(int operationCount, SubmitOutcome outcome, @Nullable UUID taskId) {
        boolean success() {
            return switch (outcome) {
                case QUEUED -> taskId != null;
                case COMPLETED -> true;
                default -> false;
            };
        }

        static ApplyResult rejected() {
            return new ApplyResult(0, SubmitOutcome.REJECTED, null);
        }

        static ApplyResult queued(int count, UUID taskId) {
            return new ApplyResult(count, SubmitOutcome.QUEUED, taskId);
        }

        static ApplyResult completed(int count, UUID taskId) {
            return new ApplyResult(count, SubmitOutcome.COMPLETED, taskId);
        }

        static ApplyResult failed(int count, UUID taskId, @Nullable BakeTaskState ignored) {
            return new ApplyResult(count, SubmitOutcome.FAILED, taskId);
        }

        static ApplyResult fromTerminalState(int count, UUID taskId, @Nullable BakeTaskState state) {
            if (state == null) {
                return new ApplyResult(count, SubmitOutcome.FAILED, taskId);
            }
            SubmitOutcome outcome = switch (state) {
                case COMPLETED -> SubmitOutcome.COMPLETED;
                case TIMED_OUT -> SubmitOutcome.TIMED_OUT;
                case CANCELLED, CANCELLING -> SubmitOutcome.CANCELLED;
                case ROLLBACK_FAILED -> SubmitOutcome.ROLLBACK_FAILED;
                case FAILED, QUEUED, RUNNING, ROLLING_BACK -> SubmitOutcome.FAILED;
            };
            return new ApplyResult(count, outcome, taskId);
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
        state.put("blocksPerTick", blocksPerTick);
        state.put("tickBudgetMillis", tickBudgetMillis);
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
        if ((value = map.get("blocksPerTick")) instanceof Number numberValue) {
            setBlocksPerTick(numberValue.intValue());
        }
        if ((value = map.get("tickBudgetMillis")) instanceof Number numberValue) {
            setTickBudgetMillis(numberValue.intValue());
        }
    }
}
