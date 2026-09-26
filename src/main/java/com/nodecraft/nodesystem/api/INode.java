package com.nodecraft.nodesystem.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 节点接口，定义了NodeCraft节点系统中节点的基本行为
 */
public interface INode {
    
    /**
     * 获取节点的唯一标识符
     * @return 节点UUID
     */
    UUID getId();
    
    /**
     * 获取节点的类型标识符
     * @return 节点类型ID
     */
    String getTypeId();
    
    /**
     * 获取节点的显示名称
     * @return 显示名称
     */
    String getDisplayName();
    
    /**
     * 获取节点的描述
     * @return 节点描述
     */
    String getDescription();
    
    /**
     * 获取节点的所有输入端口
     * @return 输入端口列表
     */
    List<IPort> getInputPorts();
    
    /**
     * 获取节点的所有输出端口
     * @return 输出端口列表
     */
    List<IPort> getOutputPorts();
    
    /**
     * 获取节点在画布上的X坐标
     * @return X坐标
     */
    double getPositionX();
    
    /**
     * 获取节点在画布上的Y坐标
     * @return Y坐标
     */
    double getPositionY();
    
    /**
     * 设置节点在画布上的位置
     * @param x X坐标
     * @param y Y坐标
     */
    void setPosition(double x, double y);
    
    /**
     * 执行节点的计算逻辑
     * @param inputs 输入数据映射，键为输入端口ID，值为输入数据
     * @return 输出数据映射，键为输出端口ID，值为输出数据
     */
    Map<String, Object> compute(Map<String, Object> inputs);
    
    /**
     * 从输出端口获取数据
     * @param portId 输出端口ID
     * @return 输出数据
     */
    Object getOutput(String portId);

    /**
     * 从输入端口获取当前值（可能为 null；请勿用 null 推断“未连接”）
     * @param portId 输入端口ID
     * @return 输入数据
     */
    Object getInput(String portId);
    
    /**
     * 设置输入端口的值
     * @param portId 输入端口ID
     * @param value 值
     */
    void setInput(String portId, Object value);
    
    /**
     * 获取节点的当前状态，用于序列化和历史记录
     * 返回一个包含节点所有可序列化属性的对象
     * 
     * @return 节点状态对象，通常是Map<String, Object>或自定义状态类
     */
    Object getNodeState();
    
    /**
     * 设置节点的状态，用于反序列化和历史记录恢复
     * 应能够处理由getNodeState()返回的状态对象
     * 
     * @param state 节点状态对象
     */
    void setNodeState(Object state);
} 