package com.nodecraft.nodesystem.nodes.inputs.sources;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 列表创建节点，将多个输入项打包成一个List。
 * 提供+/-按钮来动态调整输入端口数量。
 */
@NodeInfo(
    id = "inputs.sources.create_list",
    displayName = "创建列表",
    description = "将多个输入项打包成一个列表",
    category = "inputs.sources"
)
public class CreateListNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "输入数量", category = "设置", order = 1,
                  description = "列表输入端口数量")
    private int inputCount = 3;
    
    @NodeProperty(displayName = "允许不同类型", category = "设置", order = 2,
                  description = "是否允许不同类型的输入")
    private boolean allowDifferentTypes = true;
    
    private static final String OUTPUT_LIST_ID = "output_list";
    
    public CreateListNode() {
        super(UUID.randomUUID(), "inputs.sources.create_list");
        rebuildInputPorts();
        
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "List", 
                "The resulting list containing all input items", NodeDataType.LIST, this));
    }
    
    @Override
    public String getDescription() { return "将多个输入项打包成一个列表"; }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Object> resultList = new ArrayList<>();
        for (int i = 0; i < inputCount; i++) {
            Object value = inputValues.get("input_" + i);
            if (value != null) resultList.add(value);
        }
        outputValues.put(OUTPUT_LIST_ID, resultList);
    }
    
    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getTextLineHeight(); // 标题
        height += getSmallPadding();
        height += ImGui.getFrameHeight(); // +/- 按钮行
        height += getSmallPadding();
        height += ImGui.getTextLineHeight(); // 信息行
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 160f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            try {
                float availableWidth = width - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
                l.addVerticalSpacing(getMediumPadding());
                
                // === 输入数量显示 ===
                ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.8f, 1.0f, 1.0f);
                ImGui.text("输入端口: " + inputCount);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === +/- 按钮 ===
                float buttonWidth = 40f;
                
                // 减号按钮
                boolean canRemove = inputCount > 1;
                if (!canRemove) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
                }
                if (ImGui.button(" - ##remove", buttonWidth, 0) && canRemove) {
                    inputCount--;
                    rebuildInputPorts();
                    markDirty();
                    changed = true;
                }
                if (!canRemove) {
                    ImGui.popStyleColor(2);
                }
                
                ImGui.sameLine();
                
                // 加号按钮
                boolean canAdd = inputCount < 20;
                if (!canAdd) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
                }
                if (ImGui.button(" + ##add", buttonWidth, 0) && canAdd) {
                    inputCount++;
                    rebuildInputPorts();
                    markDirty();
                    changed = true;
                }
                if (!canAdd) {
                    ImGui.popStyleColor(2);
                }
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 类型信息 ===
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                ImGui.text(allowDifferentTypes ? "混合类型" : "同类型");
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("CreateListNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }
    
    private void rebuildInputPorts() {
        inputPorts.clear();
        for (int i = 0; i < inputCount; i++) {
            String portId = "input_" + i;
            IPort inputPort = new BasePort(portId, "Item " + (i + 1), 
                    "Item to add to the list", NodeDataType.ANY, this);
            addInputPort(inputPort);
        }
        invalidateCache();
    }
    
    public int getInputCount() { return inputCount; }
    
    public void setInputCount(int count) {
        if (count > 0 && count != inputCount) {
            inputCount = count;
            rebuildInputPorts();
            markDirty();
        }
    }
    
    public boolean isAllowDifferentTypes() { return allowDifferentTypes; }
    public void setAllowDifferentTypes(boolean allow) { this.allowDifferentTypes = allow; }
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("inputCount", getInputCount());
        state.put("allowDifferentTypes", isAllowDifferentTypes());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.containsKey("allowDifferentTypes")) {
                Object v = m.get("allowDifferentTypes");
                if (v instanceof Boolean) setAllowDifferentTypes((Boolean) v);
            }
            if (m.containsKey("inputCount")) {
                Object v = m.get("inputCount");
                if (v instanceof Integer) setInputCount((Integer) v);
            }
        }
    }
}