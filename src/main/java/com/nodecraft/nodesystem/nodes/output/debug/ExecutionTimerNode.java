package com.nodecraft.nodesystem.nodes.output.debug;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/**
 * Execution Timer 节点: 测量连接到此节点的计算分支所花费的时间。
 * 显示时间统计、精度控制、自动重置选项。
 */
@NodeInfo(
    id = "output.debug.execution_timer",
    displayName = "执行计时器",
    description = "测量连接到此节点的计算分支所花费的时间",
    category = "output.debug",
    order = 2
)
public class ExecutionTimerNode extends BaseCustomUINode {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionTimerNode.class);

    @NodeProperty(displayName = "自动重置", category = "计时", order = 1)
    private boolean autoReset = true;

    @NodeProperty(displayName = "显示毫秒", category = "计时", order = 2)
    private boolean showMilliseconds = true;

    @NodeProperty(displayName = "打印到控制台", category = "计时", order = 3)
    private boolean printToConsole = false;

    @NodeProperty(displayName = "精度", category = "计时", order = 4)
    private int precision = 2;

    private long startTime = 0;
    private long endTime = 0;
    private volatile long lastExecutionTime = 0;
    private volatile long totalExecutionTime = 0;
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

    public ExecutionTimerNode() {
        super(UUID.randomUUID(), "output.debug.execution_timer");
        addInputPort(new BasePort(INPUT_START_ID, "Start", "开始计时", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_STOP_ID, "Stop", "停止计时", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_RESET_ID, "Reset", "重置计时器", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_AUTO_RESET_ID, "Auto Reset", "是否在每次开始时自动重置", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_EXECUTION_TIME_ID, "Execution Time", "最近一次执行时间（毫秒）", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_TIME_ID, "Total Time", "累计执行时间（毫秒）", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_AVERAGE_TIME_ID, "Average Time", "平均执行时间（毫秒）", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "执行次数", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FORMATTED_TIME_ID, "Formatted Time", "格式化的时间字符串", NodeDataType.STRING, this));
        resetOutputs();
    }

    @Override
    public String getDescription() { return "测量连接到此节点的计算分支所花费的时间"; }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object startObj = inputValues.get(INPUT_START_ID);
        Object stopObj = inputValues.get(INPUT_STOP_ID);
        Object resetObj = inputValues.get(INPUT_RESET_ID);
        Object autoResetObj = inputValues.get(INPUT_AUTO_RESET_ID);

        boolean ar = this.autoReset;
        if (autoResetObj instanceof Boolean) ar = (Boolean) autoResetObj;

        if (resetObj != null) resetTimer();
        if (startObj != null) {
            if (ar) { startTime = System.currentTimeMillis(); endTime = 0; }
            else if (startTime == 0) startTime = System.currentTimeMillis();
        }
        if (stopObj != null && startTime > 0) {
            endTime = System.currentTimeMillis();
            lastExecutionTime = endTime - startTime;
            totalExecutionTime += lastExecutionTime;
            executionCount++;
            if (printToConsole) {
                LOGGER.info("执行时间: {} ({} avg)",
                    formatDuration(lastExecutionTime),
                    formatDuration(executionCount > 0 ? totalExecutionTime / executionCount : 0));
            }
            startTime = 0;
        }
        updateOutputs();
    }

    @Override
    protected float calculateUIHeight() {
        float h = getMediumPadding();
        h += ImGui.getTextLineHeight();    // 最后执行时间
        h += getSmallPadding();
        h += ImGui.getTextLineHeight();    // 平均/总计
        h += getSmallPadding();
        h += ImGui.getTextLineHeight();    // 执行次数
        h += getMediumPadding();
        h += getSmallPadding();            // 底部安全边距，避免最后一行贴边/裁剪
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

                // === 时间统计显示 ===
                ImGui.pushStyleColor(ImGuiCol.Text, 0xFF44DDAA);
                ImGui.text("⏱ " + formatDuration(lastExecutionTime));
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());

                float avgTime = executionCount > 0 ? (float) totalExecutionTime / executionCount : 0;
                ImGui.pushStyleColor(ImGuiCol.Text, 0xFF888888);
                ImGui.text(String.format("平均: %s | 总计: %s", formatDuration((long) avgTime), formatDuration(totalExecutionTime)));
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());

                ImGui.pushStyleColor(ImGuiCol.Text, 0xFFAAAACC);
                ImGui.text("执行次数: " + executionCount);
                ImGui.popStyleColor();
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                LOGGER.error("ExecutionTimerNode UI渲染失败", e);
            }
            return changed;
        });
    }

    private String formatDuration(long duration) {
        if (duration < 1000 || showMilliseconds) {
            return String.format("%." + precision + "f ms", (float) duration);
        } else {
            return String.format("%." + precision + "f s", duration / 1000.0f);
        }
    }

    private void resetTimer() {
        startTime = 0; endTime = 0; lastExecutionTime = 0;
        totalExecutionTime = 0; executionCount = 0; resetOutputs();
    }

    private void resetOutputs() {
        outputValues.put(OUTPUT_EXECUTION_TIME_ID, 0.0f);
        outputValues.put(OUTPUT_TOTAL_TIME_ID, 0.0f);
        outputValues.put(OUTPUT_AVERAGE_TIME_ID, 0.0f);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_FORMATTED_TIME_ID, "0 ms");
    }

    private void updateOutputs() {
        float et = (float) lastExecutionTime;
        float tt = (float) totalExecutionTime;
        float avg = executionCount > 0 ? tt / executionCount : 0;
        outputValues.put(OUTPUT_EXECUTION_TIME_ID, et);
        outputValues.put(OUTPUT_TOTAL_TIME_ID, tt);
        outputValues.put(OUTPUT_AVERAGE_TIME_ID, avg);
        outputValues.put(OUTPUT_COUNT_ID, executionCount);
        outputValues.put(OUTPUT_FORMATTED_TIME_ID, formatDuration(lastExecutionTime));
    }

    public boolean isAutoReset() { return autoReset; }
    public void setAutoReset(boolean v) { if (this.autoReset != v) { this.autoReset = v; markDirty(); } }
    public boolean isShowMilliseconds() { return showMilliseconds; }
    public void setShowMilliseconds(boolean v) { if (this.showMilliseconds != v) { this.showMilliseconds = v; markDirty(); } }
    public boolean isPrintToConsole() { return printToConsole; }
    public void setPrintToConsole(boolean v) { if (this.printToConsole != v) { this.printToConsole = v; markDirty(); } }
    public int getPrecision() { return precision; }
    public void setPrecision(int v) { v = Math.max(0, Math.min(6, v)); if (this.precision != v) { this.precision = v; markDirty(); } }
    public long getLastExecutionTime() { return lastExecutionTime; }
    public float getAverageExecutionTime() { return executionCount > 0 ? (float) totalExecutionTime / executionCount : 0; }

    @Override
    public @Nullable Object getNodeState() {
        java.util.Map<String, Object> s = new java.util.HashMap<>();
        s.put("autoReset", autoReset); s.put("showMilliseconds", showMilliseconds);
        s.put("printToConsole", printToConsole); s.put("precision", precision);
        s.put("startTime", startTime); s.put("lastExecutionTime", lastExecutionTime);
        s.put("totalExecutionTime", totalExecutionTime); s.put("executionCount", executionCount);
        return s;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.get("autoReset") instanceof Boolean) setAutoReset((Boolean) m.get("autoReset"));
            if (m.get("showMilliseconds") instanceof Boolean) setShowMilliseconds((Boolean) m.get("showMilliseconds"));
            if (m.get("printToConsole") instanceof Boolean) setPrintToConsole((Boolean) m.get("printToConsole"));
            if (m.get("precision") instanceof Number) setPrecision(((Number) m.get("precision")).intValue());
            if (m.get("startTime") instanceof Number) startTime = ((Number) m.get("startTime")).longValue();
            if (m.get("lastExecutionTime") instanceof Number) lastExecutionTime = ((Number) m.get("lastExecutionTime")).longValue();
            if (m.get("totalExecutionTime") instanceof Number) totalExecutionTime = ((Number) m.get("totalExecutionTime")).longValue();
            if (m.get("executionCount") instanceof Number) executionCount = ((Number) m.get("executionCount")).intValue();
            updateOutputs();
        }
    }
}
