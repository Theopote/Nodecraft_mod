package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.api.IPort;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Group 节点：将选中的节点打包成一个可视化组
 * 这是一个特殊的节点，主要作用于编辑器UI层面，不参与数据流的处理
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "utilities.organization.group",
    displayName = "Group",
    description = "将选中的节点打包成一个可视化组",
    category = "utilities.organization"
)
public class GroupNode extends BaseNode {
    
    // --- 节点属性 ---
    private String groupName = "Group"; // 组名称
    private String groupColor = "#3498db"; // 组颜色
    private boolean isCollapsed = false; // 是否折叠
    private boolean isLocked = false; // 是否锁定（防止移动/编辑）
    private List<UUID> containedNodeIds = new ArrayList<>(); // 包含的节点ID列表
    private String description = "将选中的节点打包成一个可视化组";
    
    // --- 尺寸属性 ---
    private double width = 300.0;
    private double height = 200.0;
    
    // --- 可选输入输出端口 ID ---
    private static final String INPUT_SIGNAL_ID = "input_signal";
    private static final String OUTPUT_SIGNAL_ID = "output_signal";
    
    /**
     * 构造一个新的组节点
     */
    public GroupNode() {
        super(UUID.randomUUID(), "utilities.organization.group");
        
        // 可选的输入输出端口（默认不添加，可通过UI添加）
        // 这些端口主要用于在组折叠时保持连接关系
    }
    
    /**
     * 通过ID查找输入端口
     * @param portId 端口ID
     * @return 找到的端口，如果未找到则返回null
     */
    protected IPort getInputPort(String portId) {
        for (IPort port : inputPorts) {
            if (port.getId().equals(portId)) {
                return port;
            }
        }
        return null;
    }
    
    /**
     * 通过ID查找输出端口
     * @param portId 端口ID
     * @return 找到的端口，如果未找到则返回null
     */
    protected IPort getOutputPort(String portId) {
        for (IPort port : outputPorts) {
            if (port.getId().equals(portId)) {
                return port;
            }
        }
        return null;
    }
    
    /**
     * 移除指定ID的输入端口
     * @param portId 要移除的端口ID
     */
    protected void removeInputPort(String portId) {
        inputPorts.removeIf(port -> port.getId().equals(portId));
    }
    
    /**
     * 移除指定ID的输出端口
     * @param portId 要移除的端口ID
     */
    protected void removeOutputPort(String portId) {
        outputPorts.removeIf(port -> port.getId().equals(portId));
    }
    
    /**
     * 添加输入端口
     */
    public void addInputSignalPort() {
        if (getInputPort(INPUT_SIGNAL_ID) == null) {
            addInputPort(new BasePort(INPUT_SIGNAL_ID, "Input", 
                    "组输入信号", NodeDataType.ANY, this));
            markDirty();
        }
    }
    
    /**
     * 添加输出端口
     */
    public void addOutputSignalPort() {
        if (getOutputPort(OUTPUT_SIGNAL_ID) == null) {
            addOutputPort(new BasePort(OUTPUT_SIGNAL_ID, "Output", 
                    "组输出信号", NodeDataType.ANY, this));
            markDirty();
        }
    }
    
    /**
     * 移除输入端口
     */
    public void removeInputSignalPort() {
        removeInputPort(INPUT_SIGNAL_ID);
        markDirty();
    }
    
    /**
     * 移除输出端口
     */
    public void removeOutputSignalPort() {
        removeOutputPort(OUTPUT_SIGNAL_ID);
        markDirty();
    }
    
    /**
     * 节点的计算逻辑
     * 对于Group节点，核心逻辑非常简单：只是传递输入到输出
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 如果有输入和输出端口，则直接传递值
        if (getInputPort(INPUT_SIGNAL_ID) != null && getOutputPort(OUTPUT_SIGNAL_ID) != null) {
            Object inputSignal = inputValues.get(INPUT_SIGNAL_ID);
            outputValues.put(OUTPUT_SIGNAL_ID, inputSignal);
        }
        
        // Group节点主要作用于UI层面，不执行实际的数据处理
    }
    
    /**
     * 添加一个节点到组中
     * @param nodeId 要添加的节点ID
     */
    public void addNode(UUID nodeId) {
        if (!containedNodeIds.contains(nodeId)) {
            containedNodeIds.add(nodeId);
            markDirty();
        }
    }
    
    /**
     * 从组中移除一个节点
     * @param nodeId 要移除的节点ID
     */
    public void removeNode(UUID nodeId) {
        if (containedNodeIds.remove(nodeId)) {
            markDirty();
        }
    }
    
    /**
     * 获取组中包含的所有节点ID
     * @return 节点ID列表
     */
    public List<UUID> getContainedNodeIds() {
        return new ArrayList<>(containedNodeIds);
    }
    
    /**
     * 清空组中的所有节点
     */
    public void clearNodes() {
        containedNodeIds.clear();
        markDirty();
    }
    
    /**
     * 检查指定节点是否在组中
     * @param nodeId 要检查的节点ID
     * @return 如果节点在组中返回true，否则返回false
     */
    public boolean containsNode(UUID nodeId) {
        return containedNodeIds.contains(nodeId);
    }
    
    // --- Getters/Setters for Properties ---
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
        markDirty();
    }
    
    public String getGroupColor() {
        return groupColor;
    }
    
    public void setGroupColor(String groupColor) {
        this.groupColor = groupColor;
        markDirty();
    }
    
    public boolean isCollapsed() {
        return isCollapsed;
    }
    
    public void setCollapsed(boolean collapsed) {
        this.isCollapsed = collapsed;
        markDirty();
    }
    
    public boolean isLocked() {
        return isLocked;
    }
    
    public void setLocked(boolean locked) {
        this.isLocked = locked;
        markDirty();
    }
    
    public double getWidth() {
        return width;
    }
    
    public void setWidth(double width) {
        this.width = Math.max(100, width); // 最小宽度为100
        markDirty();
    }
    
    public double getHeight() {
        return height;
    }
    
    public void setHeight(double height) {
        this.height = Math.max(100, height); // 最小高度为100
        markDirty();
    }
    
    /**
     * 调整大小以包含所有节点
     * 此方法在实际应用中需要通过Editor实现，因为需要知道每个节点的位置和大小
     */
    public void resizeToFitNodes() {
        // 实现需要编辑器支持
        // 编辑器会计算所有包含节点的边界框，然后调用setWidth和setHeight
    }
    
    /**
     * 保存节点状态
     */
    @Override
    public @Nullable Object getNodeState() {
        Object[] state = new Object[8];
        state[0] = groupName;
        state[1] = groupColor;
        state[2] = isCollapsed;
        state[3] = isLocked;
        state[4] = width;
        state[5] = height;
        state[6] = containedNodeIds.toArray(new UUID[0]);
        state[7] = new boolean[] {
            getInputPort(INPUT_SIGNAL_ID) != null,
            getOutputPort(OUTPUT_SIGNAL_ID) != null
        };
        return state;
    }
    
    /**
     * 恢复节点状态
     */
    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Object[]) {
            Object[] objState = (Object[]) state;
            if (objState.length >= 8) {
                if (objState[0] instanceof String) {
                    groupName = (String) objState[0];
                }
                if (objState[1] instanceof String) {
                    groupColor = (String) objState[1];
                }
                if (objState[2] instanceof Boolean) {
                    isCollapsed = (Boolean) objState[2];
                }
                if (objState[3] instanceof Boolean) {
                    isLocked = (Boolean) objState[3];
                }
                if (objState[4] instanceof Number) {
                    width = ((Number) objState[4]).doubleValue();
                }
                if (objState[5] instanceof Number) {
                    height = ((Number) objState[5]).doubleValue();
                }
                
                // 恢复包含的节点ID
                containedNodeIds.clear();
                if (objState[6] instanceof UUID[]) {
                    UUID[] nodeIds = (UUID[]) objState[6];
                    for (UUID id : nodeIds) {
                        containedNodeIds.add(id);
                    }
                }
                
                // 恢复端口状态
                if (objState[7] instanceof boolean[]) {
                    boolean[] portStates = (boolean[]) objState[7];
                    if (portStates.length >= 2) {
                        if (portStates[0] && getInputPort(INPUT_SIGNAL_ID) == null) {
                            addInputSignalPort();
                        } else if (!portStates[0] && getInputPort(INPUT_SIGNAL_ID) != null) {
                            removeInputSignalPort();
                        }
                        
                        if (portStates[1] && getOutputPort(OUTPUT_SIGNAL_ID) == null) {
                            addOutputSignalPort();
                        } else if (!portStates[1] && getOutputPort(OUTPUT_SIGNAL_ID) != null) {
                            removeOutputSignalPort();
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getDescription() {
        return this.description;
    }
} 