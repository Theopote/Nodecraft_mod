package com.nodecraft.nodesystem.nodes.output.debug;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stopwatch: measures elapsed wall-clock time between explicit Start and Stop pulses.
 */
@NodeInfo(
    effect = NodeEffect.CONTEXT_WRITE,
    id = "output.debug.execution_timer",
    displayName = "Stopwatch",
    description = "Measures elapsed wall-clock time between explicit Start and Stop pulses.",
    category = "output.debug",
    order = 2
)
public class StopwatchNode extends BaseCustomUINode {

    private static final Logger LOGGER = LoggerFactory.getLogger(StopwatchNode.class);

    private static final double NANOS_PER_MS = 1_000_000.0d;

    @NodeProperty(displayName = "Auto Reset", category = "Timing", order = 1)
    private boolean autoReset = true;

    @NodeProperty(displayName = "Show Milliseconds", category = "Timing", order = 2)
    private boolean showMilliseconds = true;

    @NodeProperty(displayName = "Print To Console", category = "Timing", order = 3)
    private boolean printToConsole = false;

    @NodeProperty(displayName = "Precision", category = "Timing", order = 4)
    private int precision = 2;

    private long startNs = 0L;
    private volatile long lastElapsedNs = 0L;
    private volatile long totalElapsedNs = 0L;
    private volatile int executionCount = 0;

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_STOP_ID = "input_stop";
    private static final String INPUT_RESET_ID = "input_reset";
    private static final String INPUT_AUTO_RESET_ID = "input_auto_reset";
    private static final String OUTPUT_EXECUTION_TIME_ID = "output_execution_time";
    private static final String OUTPUT_TOTAL_TIME_ID = "output_total_time";
    private static final String OUTPUT_AVERAGE_TIME_ID = "output_average_time";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_FORMATTED_TIME_ID = "output_formatted_time";

    public StopwatchNode() {
        super(UUID.randomUUID(), "output.debug.execution_timer");
        addInputPort(new BasePort(INPUT_START_ID, "Start", "EXEC pulse to start timing", NodeDataType.EXEC, this, false, false));
        addInputPort(new BasePort(INPUT_STOP_ID, "Stop", "EXEC pulse to stop timing", NodeDataType.EXEC, this, false, false));
        addInputPort(new BasePort(INPUT_RESET_ID, "Reset", "EXEC pulse to reset timing stats", NodeDataType.EXEC, this, false, false));
        addInputPort(new BasePort(INPUT_AUTO_RESET_ID, "Auto Reset", "Whether Start restarts from zero", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_EXECUTION_TIME_ID, "Execution Time", "Last elapsed duration (milliseconds)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_TIME_ID, "Total Time", "Cumulative elapsed duration (milliseconds)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_AVERAGE_TIME_ID, "Average Time", "Average elapsed duration (milliseconds)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of completed Start/Stop cycles", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FORMATTED_TIME_ID, "Formatted Time", "Formatted last elapsed duration", NodeDataType.STRING, this));
        resetOutputs();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Boolean autoResetResolved = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_AUTO_RESET_ID, this.autoReset);
        if (autoResetResolved == null) {
            updateOutputs();
            return;
        }
        boolean ar = autoResetResolved;

        if (Boolean.TRUE.equals(inputValues.get(INPUT_RESET_ID))) {
            resetTimer();
        }
        if (Boolean.TRUE.equals(inputValues.get(INPUT_START_ID))) {
            if (ar || startNs == 0L) {
                startNs = System.nanoTime();
            }
        }
        if (Boolean.TRUE.equals(inputValues.get(INPUT_STOP_ID)) && startNs > 0L) {
            long endNs = System.nanoTime();
            lastElapsedNs = Math.max(0L, endNs - startNs);
            totalElapsedNs += lastElapsedNs;
            executionCount++;
            if (printToConsole) {
                LOGGER.info("Stopwatch: {} ({} avg)",
                    formatDuration(lastElapsedNs),
                    formatDuration(executionCount > 0 ? totalElapsedNs / executionCount : 0L));
            }
            startNs = 0L;
        }
        updateOutputs();
    }

    @Override
    protected float calculateUIHeight() {
        float h = getMediumPadding();
        h += ImGui.getTextLineHeight();
        h += getSmallPadding();
        h += ImGui.getTextLineHeight();
        h += getSmallPadding();
        h += ImGui.getTextLineHeight();
        h += getMediumPadding();
        h += getSmallPadding();
        return h;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 168f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            try {
                l.addVerticalSpacing(getMediumPadding());

                ImGui.pushStyleColor(ImGuiCol.Text, 0xFF44DDAA);
                ImGui.text(formatDuration(lastElapsedNs));
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());

                long avgNs = executionCount > 0 ? totalElapsedNs / executionCount : 0L;
                ImGui.pushStyleColor(ImGuiCol.Text, 0xFF888888);
                ImGui.text(String.format(Locale.ROOT, "Avg: %s | Total: %s",
                    formatDuration(avgNs), formatDuration(totalElapsedNs)));
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());

                ImGui.pushStyleColor(ImGuiCol.Text, 0xFFAAAACC);
                ImGui.text("Count: " + executionCount);
                ImGui.popStyleColor();
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                LOGGER.error("StopwatchNode UI render failed", e);
            }
            return changed;
        });
    }

    private String formatDuration(long durationNs) {
        double ms = durationNs / NANOS_PER_MS;
        String format = "%." + precision + "f";
        if (ms < 1000.0d || showMilliseconds) {
            return String.format(Locale.ROOT, format + " ms", ms);
        }
        return String.format(Locale.ROOT, format + " s", ms / 1000.0d);
    }

    private void resetTimer() {
        startNs = 0L;
        lastElapsedNs = 0L;
        totalElapsedNs = 0L;
        executionCount = 0;
        resetOutputs();
    }

    private void resetOutputs() {
        outputValues.put(OUTPUT_EXECUTION_TIME_ID, 0.0d);
        outputValues.put(OUTPUT_TOTAL_TIME_ID, 0.0d);
        outputValues.put(OUTPUT_AVERAGE_TIME_ID, 0.0d);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_FORMATTED_TIME_ID, "0 ms");
    }

    private void updateOutputs() {
        double et = lastElapsedNs / NANOS_PER_MS;
        double tt = totalElapsedNs / NANOS_PER_MS;
        double avg = executionCount > 0 ? tt / executionCount : 0.0d;
        outputValues.put(OUTPUT_EXECUTION_TIME_ID, et);
        outputValues.put(OUTPUT_TOTAL_TIME_ID, tt);
        outputValues.put(OUTPUT_AVERAGE_TIME_ID, avg);
        outputValues.put(OUTPUT_COUNT_ID, executionCount);
        outputValues.put(OUTPUT_FORMATTED_TIME_ID, formatDuration(lastElapsedNs));
    }

    public boolean isAutoReset() {
        return autoReset;
    }

    public void setAutoReset(boolean v) {
        if (this.autoReset != v) {
            this.autoReset = v;
            markDirty();
        }
    }

    public boolean isShowMilliseconds() {
        return showMilliseconds;
    }

    public void setShowMilliseconds(boolean v) {
        if (this.showMilliseconds != v) {
            this.showMilliseconds = v;
            markDirty();
        }
    }

    public boolean isPrintToConsole() {
        return printToConsole;
    }

    public void setPrintToConsole(boolean v) {
        if (this.printToConsole != v) {
            this.printToConsole = v;
            markDirty();
        }
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int v) {
        v = Math.max(0, Math.min(6, v));
        if (this.precision != v) {
            this.precision = v;
            markDirty();
        }
    }

    public double getLastExecutionTimeMs() {
        return lastElapsedNs / NANOS_PER_MS;
    }

    @Override
    public @Nullable Object getNodeState() {
        Map<String, Object> s = new HashMap<>();
        s.put("autoReset", autoReset);
        s.put("showMilliseconds", showMilliseconds);
        s.put("printToConsole", printToConsole);
        s.put("precision", precision);
        return s;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Map<?, ?> m) {
            if (m.get("autoReset") instanceof Boolean b) {
                setAutoReset(b);
            }
            if (m.get("showMilliseconds") instanceof Boolean b) {
                setShowMilliseconds(b);
            }
            if (m.get("printToConsole") instanceof Boolean b) {
                setPrintToConsole(b);
            }
            if (m.get("precision") instanceof Number n) {
                setPrecision(n.intValue());
            }
        }
        resetTimer();
    }
}
