package com.nodecraft.nodesystem.nodes.visualization.debugging;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 值监视器：面板/“小电视”风格，仅需将任意节点的输出连到输入，即可在面板上查看该输出的数据和类型。
 */
@NodeInfo(
    id = "visualization.debugging.value_monitor",
    displayName = "数据预览",
    description = "将任意输出连到输入，在面板上查看该输出的数据和类型",
    category = "visualization.debugging"
)
public class ValueMonitorNode extends BaseCustomUINode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_VALUE_ID = "output_value";

    /** 供面板显示用，processNode 中写入 */
    private volatile String displayContent = "";
    private volatile String typeLabel = "—";

    public ValueMonitorNode() {
        super(UUID.randomUUID(), "visualization.debugging.value_monitor");
        addInputPort(new BasePort(INPUT_VALUE_ID, "输入", "连接任意节点的输出，在此查看数据", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "输出", "原样传递输入值", NodeDataType.ANY, this));
    }

    @Override
    public String getDescription() {
        return "将任意输出连到输入，在面板上查看该输出的数据和类型";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object value = inputValues.get(INPUT_VALUE_ID);
        typeLabel = getDataTypeLabel(value);
        displayContent = formatContent(value);
        outputValues.put(OUTPUT_VALUE_ID, value);
    }

    @Override
    protected float calculateUIHeight() {
        float h = getMediumPadding();
        h += ImGui.getTextLineHeight() * 4;       // 内容区高度（4行文本高度，和PanelNode一致）
        h += getMediumPadding();
        return h;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 188f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            try {
                float edgeMargin = ZoomHelper.applyZoom(getMediumPadding(), zoom);
                float aw = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
                float totalHeightPixels = l.toPixelsExact(height);
                float topBottomPadding = ZoomHelper.applyZoom(getSmallPadding(), zoom);
                float screenH = Math.max(ImGui.getTextLineHeight() * 2.0f, totalHeightPixels - topBottomPadding * 2.0f);
                float baseCursorX = ImGui.getCursorPosX();
                ImGui.setCursorPosX(baseCursorX + edgeMargin);

                ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.08f, 0.08f, 0.10f, 0.98f);
                ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, ZoomHelper.applyZoom(1.2f, zoom));
                ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, ZoomHelper.applyZoom(4f, zoom));
                ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, ZoomHelper.applyZoom(4f, zoom), ZoomHelper.applyZoom(4f, zoom));
                boolean childOpen = ImGui.beginChild(
                    "##value_monitor_screen",
                    aw,
                    screenH,
                    true,
                    ImGuiWindowFlags.AlwaysUseWindowPadding
                        | ImGuiWindowFlags.NoScrollbar
                        | ImGuiWindowFlags.NoScrollWithMouse
                        | ImGuiWindowFlags.NoMove
                        | ImGuiWindowFlags.NoNav
                        | ImGuiWindowFlags.NoNavFocus
                );

                if (childOpen) {
                    float contentW = ImGui.getContentRegionAvailX();
                    // 类型标签（小字、灰）
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFF666666);
                    ImGui.text("[" + typeLabel + "]");
                    ImGui.popStyleColor();
                    ImGui.sameLine(0, ZoomHelper.applyZoom(6, zoom));
                    ImGui.setCursorPosY(ImGui.getCursorPosY() - ZoomHelper.applyZoom(2, zoom));

                    // 主内容：自动换行，突出显示
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFFCCDDEE);
                    String text = displayContent.isEmpty() ? "（未连接或无数据）" : displayContent;
                    ImGui.setNextItemWidth(contentW);
                    ImGui.textWrapped(text);
                    ImGui.popStyleColor();
                }
                ImGui.endChild();
                ImGui.popStyleVar(3);
                ImGui.popStyleColor();
            } catch (Exception e) {
                System.err.println("ValueMonitorNode UI 渲染失败: " + e.getMessage());
            }
            return false;
        });
    }

    private String getDataTypeLabel(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof BlockPosList) return "坐标列表";
        if (obj instanceof Iterable) return "列表";
        if (obj instanceof Object[]) return "数组";
        if (obj instanceof String) return "字符串";
        if (obj instanceof Integer) return "整数";
        if (obj instanceof Long) return "长整数";
        if (obj instanceof Float) return "浮点";
        if (obj instanceof Double) return "双精度";
        if (obj instanceof Boolean) return "布尔";
        if (obj instanceof Number) return "数值";
        return obj.getClass().getSimpleName();
    }

    private String formatContent(Object value) {
        switch (value) {
            case null -> {
                return "null";
            }
            case BlockPosList list -> {
                int n = list.size();
                if (n == 0) return "空列表 (0 个坐标)";
                StringBuilder sb = new StringBuilder();
                sb.append("共 ").append(n).append(" 个坐标");
                int shown = 0;
                for (BlockPos p : list) {
                    if (shown >= 3) {
                        sb.append("\n  ...");
                        break;
                    }
                    sb.append("\n  ").append(p.getX()).append(", ").append(p.getY()).append(", ").append(p.getZ());
                    shown++;
                }
                return sb.toString();
            }
            case Iterable it when !(value instanceof String) -> {
                int count = 0;
                StringBuilder sb = new StringBuilder();
                for (Object item : it) {
                    if (count >= 8) {
                        sb.append("\n  ...");
                        break;
                    }
                    if (count > 0) sb.append("\n  ");
                    sb.append(item instanceof String ? "\"" + item + "\"" : String.valueOf(item));
                    count++;
                }
                if (count == 0) return "[]";
                return sb.toString();
            }
            case Object[] arr -> {
                StringBuilder sb = new StringBuilder();
                int show = Math.min(8, arr.length);
                for (int i = 0; i < show; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr[i] instanceof String ? "\"" + arr[i] + "\"" : String.valueOf(arr[i]));
                }
                if (arr.length > show) sb.append(" ...");
                return sb.toString();
            }
            case String s -> {
                if (s.length() > 200) return "\"" + s.substring(0, 197) + "...\"";
                return "\"" + s + "\"";
            }
            case BlockPos p -> {
                return p.getX() + ", " + p.getY() + ", " + p.getZ();
            }
            default -> {
            }
        }
        return value.toString();
    }

    @Override
    public @Nullable Object getNodeState() {
        return new java.util.HashMap<String, Object>();
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        // 无持久选项，忽略
    }
}
