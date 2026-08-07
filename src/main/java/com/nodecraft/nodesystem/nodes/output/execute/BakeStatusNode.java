package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.UUID;

/**
 * Monitors an async bake task submitted by {@link ApplyChangesNode} or undo/redo nodes.
 */
@NodeInfo(
    id = "output.execute.bake_status",
    displayName = "Bake Status",
    description = "Polls BakePlacementService for a task ID and reports state, progress, placed, skipped, and rollback-failed counts.",
    category = "output.execute",
    order = 1
)
public class BakeStatusNode extends BaseCustomUINode {

    private static final Logger LOGGER = LoggerFactory.getLogger(BakeStatusNode.class);

    @NodeProperty(displayName = "Show Progress UI", category = "Display", order = 1)
    private boolean showProgressUi = true;

    private volatile boolean found = false;
    private volatile String state = "Idle";
    private volatile float progress = 0.0f;
    private volatile int placedCount = 0;
    private volatile int skippedCount = 0;
    private volatile int rollbackFailedCount = 0;
    private volatile int totalCount = 0;
    private volatile int remainingCount = 0;
    private volatile String statusMessage = "Provide a Task ID";

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String INPUT_TASK_ID_ID = "input_task_id";

    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_STATE_ID = "output_state";
    private static final String OUTPUT_PROGRESS_ID = "output_progress";
    private static final String OUTPUT_PLACED_ID = "output_placed";
    private static final String OUTPUT_SKIPPED_ID = "output_skipped";
    private static final String OUTPUT_ROLLBACK_FAILED_ID = "output_rollback_failed";
    private static final String OUTPUT_TOTAL_ID = "output_total";
    private static final String OUTPUT_REMAINING_ID = "output_remaining";
    private static final String OUTPUT_STATUS_ID = "output_status";

    public BakeStatusNode() {
        super(UUID.randomUUID(), "output.execute.bake_status");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional poll trigger", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_TASK_ID_ID, "Task ID", "Bake task UUID from Apply Changes", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the task snapshot was found", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STATE_ID, "State", "BakeTaskState display name (Queued, Running, Completed, Cancelled, Timed Out, Rollback Failed, ...)", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_PROGRESS_ID, "Progress", "Bake progress from 0.0 to 1.0", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_PLACED_ID, "Placed", "Blocks placed so far", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SKIPPED_ID, "Skipped", "Blocks skipped so far", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ROLLBACK_FAILED_ID, "Rollback Failed", "Blocks that failed to restore during abort rollback", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_ID, "Total", "Total blocks in the task", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_ID, "Remaining", "Blocks remaining in the task", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Human-readable bake status", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object triggerObj = inputValues.get(INPUT_TRIGGER_ID);
        Object taskIdObj = inputValues.get(INPUT_TASK_ID_ID);

        if (triggerObj == null && taskIdObj == null) {
            publishIdle("Waiting for Task ID");
            return;
        }

        String taskIdText = normalizeTaskId(taskIdObj);
        if (taskIdText.isEmpty()) {
            publishIdle("Missing Task ID");
            return;
        }

        UUID taskId;
        try {
            taskId = UUID.fromString(taskIdText);
        } catch (IllegalArgumentException e) {
            publishNotFound("Invalid Task ID: " + taskIdText);
            return;
        }

        BakePlacementService.TaskSnapshot snapshot = BakePlacementService.getInstance().getTaskSnapshot(taskId);
        if (snapshot == null) {
            publishNotFound("Task not found: " + taskId);
            return;
        }

        found = true;
        state = snapshot.resolveState();
        progress = (float) snapshot.progress();
        placedCount = snapshot.placedCount();
        skippedCount = snapshot.skippedCount();
        rollbackFailedCount = snapshot.rollbackFailedCount();
        totalCount = snapshot.totalCount();
        remainingCount = snapshot.remainingCount();
        statusMessage = formatStatus(snapshot);

        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_STATE_ID, state);
        outputValues.put(OUTPUT_PROGRESS_ID, progress);
        outputValues.put(OUTPUT_PLACED_ID, placedCount);
        outputValues.put(OUTPUT_SKIPPED_ID, skippedCount);
        outputValues.put(OUTPUT_ROLLBACK_FAILED_ID, rollbackFailedCount);
        outputValues.put(OUTPUT_TOTAL_ID, totalCount);
        outputValues.put(OUTPUT_REMAINING_ID, remainingCount);
        outputValues.put(OUTPUT_STATUS_ID, statusMessage);
    }

    private void publishIdle(String message) {
        found = false;
        state = "Idle";
        progress = 0.0f;
        placedCount = 0;
        skippedCount = 0;
        rollbackFailedCount = 0;
        totalCount = 0;
        remainingCount = 0;
        statusMessage = message;
        publishOutputs();
    }

    private void publishNotFound(String message) {
        found = false;
        state = "Not Found";
        progress = 0.0f;
        placedCount = 0;
        skippedCount = 0;
        rollbackFailedCount = 0;
        totalCount = 0;
        remainingCount = 0;
        statusMessage = message;
        publishOutputs();
    }

    private void publishOutputs() {
        outputValues.put(OUTPUT_FOUND_ID, found);
        outputValues.put(OUTPUT_STATE_ID, state);
        outputValues.put(OUTPUT_PROGRESS_ID, progress);
        outputValues.put(OUTPUT_PLACED_ID, placedCount);
        outputValues.put(OUTPUT_SKIPPED_ID, skippedCount);
        outputValues.put(OUTPUT_ROLLBACK_FAILED_ID, rollbackFailedCount);
        outputValues.put(OUTPUT_TOTAL_ID, totalCount);
        outputValues.put(OUTPUT_REMAINING_ID, remainingCount);
        outputValues.put(OUTPUT_STATUS_ID, statusMessage);
    }

    private static String normalizeTaskId(@Nullable Object taskIdObj) {
        if (taskIdObj == null) {
            return "";
        }
        return taskIdObj.toString().trim();
    }

    private static String formatStatus(BakePlacementService.TaskSnapshot snapshot) {
        if (snapshot.rollbackFailedCount() > 0) {
            return String.format(
                Locale.ROOT,
                "%s: rollback restored %d, failed %d / %d attempted (placed %d, skipped %d)",
                snapshot.resolveState(),
                snapshot.rollbackRestoredCount(),
                snapshot.rollbackFailedCount(),
                snapshot.rollbackAttemptedCount(),
                snapshot.placedCount(),
                snapshot.skippedCount()
            );
        }
        return String.format(
            Locale.ROOT,
            "%s: placed %d, skipped %d, remaining %d / %d (%.0f%%)",
            snapshot.resolveState(),
            snapshot.placedCount(),
            snapshot.skippedCount(),
            snapshot.remainingCount(),
            snapshot.totalCount(),
            snapshot.progress() * 100.0d
        );
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getTextLineHeight();
        height += getSmallPadding();
        if (showProgressUi) {
            height += ImGui.getFrameHeight();
        }
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 196.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            try {
                float edgeMargin = l.toPixels(getSmallPadding());
                float contentWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
                l.addVerticalSpacing(getMediumPadding());

                int statusColor = switch (state) {
                    case "Running", "Cancelling", "Rolling Back" -> 0xFF44AADD;
                    case "Completed" -> 0xFF44DD44;
                    case "Cancelled", "Timed Out", "Failed", "Rollback Failed", "Not Found" -> 0xFFFF6666;
                    case "Queued" -> 0xFFFFCC44;
                    default -> 0xFF888888;
                };
                ImGui.pushStyleColor(ImGuiCol.Text, statusColor);
                ImGui.setCursorPosX(ImGui.getCursorPosX() + edgeMargin);
                ImGui.text(statusMessage);
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());

                if (showProgressUi && found) {
                    ImGui.setCursorPosX(ImGui.getCursorPosX() + edgeMargin);
                    ImGui.progressBar(
                        progress,
                        contentWidth,
                        ImGui.getFrameHeight(),
                        String.format(Locale.ROOT, "%s %.0f%%", state, progress * 100.0f)
                    );
                }

                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                LOGGER.error("BakeStatusNode UI render failed", e);
            }
            return false;
        });
    }

    public boolean isShowProgressUi() {
        return showProgressUi;
    }

    public void setShowProgressUi(boolean value) {
        if (showProgressUi != value) {
            showProgressUi = value;
            markDirty();
        }
    }

    public String getState() {
        return state;
    }

    public float getProgress() {
        return progress;
    }

    @Override
    public @Nullable Object getNodeState() {
        return java.util.Map.of("showProgressUi", showProgressUi);
    }

    @Override
    public void setNodeState(@Nullable Object stateObj) {
        if (stateObj instanceof java.util.Map<?, ?> map
            && map.get("showProgressUi") instanceof Boolean boolValue) {
            setShowProgressUi(boolValue);
        }
    }
}
