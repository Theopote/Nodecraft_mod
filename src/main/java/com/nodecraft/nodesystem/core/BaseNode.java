package com.nodecraft.nodesystem.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;

/**
 * 所有节点的基类，提供常见功能
 */
public abstract class BaseNode implements INode {
    private UUID id;
    private String typeId;
    private double positionX;
    private double positionY;
    private Object nodeState;
    
    protected final List<IPort> inputPorts = new ArrayList<>();
    protected final List<IPort> outputPorts = new ArrayList<>();
    
    protected final Map<String, Object> inputValues = new HashMap<>();
    protected final Map<String, Object> outputValues = new HashMap<>();
    
    public BaseNode(UUID id, String typeId) {
        this.id = id;
        this.typeId = typeId;
        this.positionX = 0;
        this.positionY = 0;
    }
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    public String getTypeId() {
        return typeId;
    }
    
    @Override
    public double getPositionX() {
        return positionX;
    }
    
    @Override
    public double getPositionY() {
        return positionY;
    }
    
    @Override
    public void setPosition(double x, double y) {
        this.positionX = x;
        this.positionY = y;
    }
    
    public String getDisplayName() {
        // 如果typeId包含'.'，则提取最后一部分作为显示名称
        if (typeId != null && typeId.contains(".")) {
            String lastPart = typeId.substring(typeId.lastIndexOf('.') + 1);
            // 将下划线替换为空格，并进行首字母大写等格式化处理
            return formatDisplayName(lastPart);
        }
        return typeId;
    }
    
    /**
     * 格式化显示名称，将下划线替换为空格，并进行首字母大写处理
     */
    private String formatDisplayName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        // 将下划线替换为空格
        String result = name.replace('_', ' ');
        
        // 首字母大写处理
        StringBuilder formatted = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : result.toCharArray()) {
            if (c == ' ') {
                capitalizeNext = true;
                formatted.append(c);
            } else if (capitalizeNext) {
                formatted.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                formatted.append(c);
            }
        }
        
        return formatted.toString();
    }
    
    @Override
    public List<IPort> getInputPorts() {
        return inputPorts;
    }
    
    @Override
    public List<IPort> getOutputPorts() {
        return outputPorts;
    }
    
    @Override
    public Map<String, Object> compute(Map<String, Object> inputs) {
        refreshInputValues(inputs);
        processNode(null); // 调用无参数版本
        syncOutputPorts();
        return new HashMap<>(outputValues);
    }
    
    /**
     * 带有执行上下文的计算方法。
     * @param inputs 输入值 Map
     * @param context 执行上下文
     * @return 输出值 Map
     */
    public Map<String, Object> compute(Map<String, Object> inputs, ExecutionContext context) {
        refreshInputValues(inputs);
        // 执行计算，传递上下文
        processNode(context);
        syncOutputPorts();
        
        // 返回输出
        return new HashMap<>(outputValues);
    }

    private void refreshInputValues(Map<String, Object> inputs) {
        for (IPort port : inputPorts) {
            inputValues.remove(port.getId());
        }
        if (inputs != null) {
            inputValues.putAll(inputs);
        }
    }
    
    /**
     * 节点的具体计算逻辑，由子类实现 (无上下文版本，用于简单节点)。
     * 默认实现调用带有上下文的版本 (传递 null 或空上下文)。
     */
    protected void processNode() {
        // 子类如果只需要这个无参数版本，可以覆盖它。
        // 默认调用带上下文的版本，传递 null。这要求 processNode(context) 能处理 null。
        processNode(null); 
    }

    /**
     * 节点的具体计算逻辑，由子类实现 (带上下文版本)。
     * @param context 执行上下文，可能为 null
     */
    public abstract void processNode(ExecutionContext context);
    
    @Override
    public Object getOutput(String portId) {
        return outputValues.get(portId);
    }
    
    @Override
    public void setInput(String portId, Object value) {
        // 查找端口以确保它存在
        for (IPort port : inputPorts) {
            if (port.getId().equals(portId)) {
                // 检查值类型是否兼容
                if (port.getDataType().isCompatible(value)) {
                    inputValues.put(portId, value);
                    return;
                }
                break;
            }
        }
    }
    
    /**
     * 添加输入端口
     * @param port 要添加的端口
     */
    protected void addInputPort(IPort port) {
        inputPorts.add(port);
    }
    
    /**
     * 添加输出端口
     * @param port 要添加的端口
     */
    protected void addOutputPort(IPort port) {
        outputPorts.add(port);
    }

    /**
     * Keep output port values aligned with the node's computed output map.
     */
    protected void syncOutputPorts() {
        for (IPort port : outputPorts) {
            port.setValue(outputValues.get(port.getId()));
        }
    }

    /**
     * 获取节点的自定义状态
     * @return 节点状态对象
     */
    public Object getNodeState() {
        return nodeState;
    }
    
    /**
     * 设置节点的自定义状态
     * @param state 节点状态对象
     */
    public void setNodeState(Object state) {
        this.nodeState = state;
    }

    /**
     * Marks this node as dirty, indicating its output might have changed
     * and downstream nodes may need recomputation.
     * TODO: Connect this to the graph execution logic.
     */
    public void markDirty() {
        // Placeholder implementation. 
        // Real implementation might involve:
        // - Setting a boolean flag: this.isDirty = true;
        // - Notifying the parent NodeGraph or ExecutionEngine.
        com.nodecraft.core.NodeCraft.LOGGER.debug("Node {} marked dirty.", getId()); 
    }
} 
