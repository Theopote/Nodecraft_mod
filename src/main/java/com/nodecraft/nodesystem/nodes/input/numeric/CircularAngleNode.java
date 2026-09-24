package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.type.ImDouble;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.numeric.angle_picker",
    displayName = "Circular Angle Picker",
    description = "通过圆形表盘选择角度（度）。需要弧度时使用 Degrees To Radians。",
    category = "input.numeric",
    order = 5
)
public class CircularAngleNode extends BaseCustomUINode {

    private static final float DIAL_SIZE = 92.0f;
    private static final String OUTPUT_ANGLE_ID = "output_angle";

    @NodeProperty(displayName = "角度", category = "角度", order = 1,
        description = "当前角度，范围为 0 到 360 度")
    private double angle = 0.0;

    @NodeProperty(displayName = "精度", category = "精度", order = 2,
        description = "小数位数，范围 0 到 5")
    private int precision = 1;

    @NodeProperty(displayName = "显示刻度", category = "UI设置", order = 10,
        description = "在圆盘周围显示刻度线")
    private boolean showTicks = true;

    @NodeProperty(displayName = "显示数值输入", category = "UI设置", order = 11,
        description = "在圆盘下方显示数值输入框")
    private boolean showValueInput = true;

    @NodeProperty(displayName = "启用直接绘制", category = "性能", order = 12,
        description = "启用后使用只读直接绘制模式")
    private boolean allowDirectDrawing = false;

    public CircularAngleNode() {
        super(UUID.randomUUID(), "input.numeric.angle_picker");
        IPort angleOutput = new BasePort(OUTPUT_ANGLE_ID, "Angle", "当前角度（度）", NodeDataType.DOUBLE, this);
        addOutputPort(angleOutput);
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "通过圆形表盘选择角度（度）。需要弧度时使用 Degrees To Radians。";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    public boolean supportsDirectDrawing() {
        return allowDirectDrawing;
    }

    @Override
    public boolean renderCustomUIDirect(ImDrawList drawList, float screenX, float screenY,
                                        float width, float height, float zoom) {
        float size = Math.min(width - 20.0f * zoom, DIAL_SIZE * zoom);
        size = Math.max(size, 40.0f * zoom);
        float centerX = screenX + width * 0.5f;
        float centerY = screenY + height * 0.5f - 6.0f * zoom;
        drawDial(drawList, centerX, centerY, size * 0.5f, zoom);

        int textColor = ImGui.colorConvertFloat4ToU32(0.82f, 0.82f, 0.82f, 1.0f);
        int subTextColor = ImGui.colorConvertFloat4ToU32(0.60f, 0.60f, 0.60f, 1.0f);
        String angleText = getAngleText();
        ImVec2 angleSize = ImGui.calcTextSize(angleText);
        drawList.addText(centerX - angleSize.x * 0.5f, screenY + height - 18.0f * zoom, textColor, angleText);
        String hint = "直接绘制模式为只读预览";
        ImVec2 hintSize = ImGui.calcTextSize(hint);
        drawList.addText(centerX - hintSize.x * 0.5f, screenY + 6.0f * zoom, subTextColor, hint);
        return false;
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += DIAL_SIZE;
        height += getMediumPadding();
        height += ImGui.getTextLineHeight();
        height += getSmallPadding();
        if (showValueInput) {
            height += ImGui.getFrameHeight();
            height += getMediumPadding();
        }
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 180.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float availableWidth = l.getAvailableContentWidth(width);

            l.addVerticalSpacing(getMediumPadding());

            float dialSizePx = Math.min(l.toPixels(DIAL_SIZE), availableWidth);
            dialSizePx = Math.max(dialSizePx, l.toPixels(56.0f));
            setCenterX(availableWidth, dialSizePx);

            ImVec2 dialStart = ImGui.getCursorScreenPos();
            ImGui.invisibleButton("##angle_dial", dialSizePx, dialSizePx);
            boolean hovered = ImGui.isItemHovered();
            boolean active = ImGui.isItemActive();

            float centerX = dialStart.x + dialSizePx * 0.5f;
            float centerY = dialStart.y + dialSizePx * 0.5f;
            float radius = dialSizePx * 0.5f - Math.max(2.0f, 2.0f * zoom);

            if ((hovered || active) && ImGui.isMouseDown(0)) {
                ImVec2 mouse = ImGui.getMousePos();
                double mouseAngle = Math.toDegrees(Math.atan2(mouse.y - centerY, mouse.x - centerX)) + 90.0;
                if (mouseAngle < 0.0) {
                    mouseAngle += 360.0;
                }
                setAngle(mouseAngle);
                changed = true;
            }

            drawDial(ImGui.getWindowDrawList(), centerX, centerY, radius, zoom);
            l.addVerticalSpacing(getMediumPadding());

            String angleText = getAngleText();
            float angleTextWidth = ImGui.calcTextSize(angleText).x;
            setCenterX(availableWidth, angleTextWidth);
            ImGui.pushStyleColor(ImGuiCol.Text, 0.82f, 0.82f, 0.82f, 1.0f);
            ImGui.text(angleText);
            ImGui.popStyleColor();
            l.addVerticalSpacing(getSmallPadding());

            if (showValueInput) {
                float inputWidth = Math.min(l.toPixels(140.0f), availableWidth - l.toPixels(8.0f));
                setCenterX(availableWidth, inputWidth);
                l.setItemWidth(inputWidth / Math.max(zoom, 0.001f));
                ImDouble angleInput = new ImDouble(angle);
                String format = "%." + getSafePrecision() + "f°";
                if (ImGui.inputDouble("##angle_input", angleInput, 0.0, 0.0, format)) {
                    setAngle(angleInput.get());
                    changed = true;
                }
                l.popItemWidth();
                l.addVerticalSpacing(getMediumPadding());
            }

            return changed;
        });
    }

    private void drawDial(ImDrawList drawList, float centerX, float centerY, float radius, float zoom) {
        int bgColor = ImGui.colorConvertFloat4ToU32(0.16f, 0.16f, 0.16f, 1.0f);
        int borderColor = ImGui.colorConvertFloat4ToU32(0.32f, 0.32f, 0.32f, 1.0f);
        int tickColor = ImGui.colorConvertFloat4ToU32(0.50f, 0.50f, 0.50f, 1.0f);
        int indicatorColor = ImGui.colorConvertFloat4ToU32(0.40f, 0.70f, 1.0f, 1.0f);
        int centerColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f);

        drawList.addCircleFilled(centerX, centerY, radius, bgColor, 48);
        drawList.addCircle(centerX, centerY, radius, borderColor, 48, Math.max(1.5f * zoom, 1.0f));

        if (showTicks) {
            for (int degree = 0; degree < 360; degree += 15) {
                boolean major = degree % 45 == 0;
                float outerRadius = radius - 2.0f * zoom;
                float innerRadius = outerRadius - (major ? 8.0f : 4.0f) * zoom;
                double radians = Math.toRadians(degree - 90.0);
                float x1 = centerX + (float) (Math.cos(radians) * innerRadius);
                float y1 = centerY + (float) (Math.sin(radians) * innerRadius);
                float x2 = centerX + (float) (Math.cos(radians) * outerRadius);
                float y2 = centerY + (float) (Math.sin(radians) * outerRadius);
                drawList.addLine(x1, y1, x2, y2, tickColor, major ? 2.0f * zoom : 1.0f * zoom);
            }
        }

        double radians = Math.toRadians(angle - 90.0);
        float indicatorLength = radius - 12.0f * zoom;
        float endX = centerX + (float) (Math.cos(radians) * indicatorLength);
        float endY = centerY + (float) (Math.sin(radians) * indicatorLength);
        drawList.addLine(centerX, centerY, endX, endY, indicatorColor, Math.max(2.0f * zoom, 1.0f));
        drawList.addCircleFilled(centerX, centerY, Math.max(3.0f * zoom, 2.0f), centerColor);
    }

    private int getSafePrecision() {
        return Math.max(0, Math.min(5, precision));
    }

    private String getAngleText() {
        String format = "%." + getSafePrecision() + "f°";
        return String.format(format, angle);
    }

    public void setAngle(double newAngle) {
        double normalized = newAngle % 360.0;
        if (normalized < 0.0) {
            normalized += 360.0;
        }
        double multiplier = Math.pow(10.0, getSafePrecision());
        normalized = Math.round(normalized * multiplier) / multiplier;

        if (Double.compare(this.angle, normalized) != 0) {
            this.angle = normalized;
            updateOutput();
            markDirty();
        }
    }

    private void updateOutput() {
        outputValues.put(OUTPUT_ANGLE_ID, angle);
        syncOutputPorts();
    }

    public double getAngle() {
        return angle;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        int normalized = Math.max(0, Math.min(5, precision));
        if (this.precision != normalized) {
            this.precision = normalized;
            setAngle(angle);
            invalidateCache();
            markDirty();
        }
    }

    public boolean isShowTicks() {
        return showTicks;
    }

    public void setShowTicks(boolean showTicks) {
        if (this.showTicks != showTicks) {
            this.showTicks = showTicks;
            markDirty();
        }
    }

    public boolean isShowValueInput() {
        return showValueInput;
    }

    public void setShowValueInput(boolean showValueInput) {
        if (this.showValueInput != showValueInput) {
            this.showValueInput = showValueInput;
            invalidateCache();
            markDirty();
        }
    }

    public boolean isAllowDirectDrawing() {
        return allowDirectDrawing;
    }

    public void setAllowDirectDrawing(boolean allowDirectDrawing) {
        if (this.allowDirectDrawing != allowDirectDrawing) {
            this.allowDirectDrawing = allowDirectDrawing;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("angle", angle);
        state.put("precision", precision);
        state.put("showTicks", showTicks);
        state.put("showValueInput", showValueInput);
        state.put("allowDirectDrawing", allowDirectDrawing);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("precision") instanceof Number value) {
                this.precision = Math.max(0, Math.min(5, value.intValue()));
            }
            if (map.get("showTicks") instanceof Boolean value) {
                this.showTicks = value;
            }
            if (map.get("showValueInput") instanceof Boolean value) {
                this.showValueInput = value;
            }
            if (map.get("allowDirectDrawing") instanceof Boolean value) {
                this.allowDirectDrawing = value;
            }

            Object angleValue = map.get("angle");
            if (angleValue instanceof Number number) {
                this.angle = number.doubleValue();
            } else if (angleValue instanceof String text) {
                try {
                    this.angle = Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                }
            }

            setAngle(angle);
            invalidateCache();
            markDirty();
        } else if (state instanceof Number number) {
            setAngle(number.doubleValue());
        }
    }
}
