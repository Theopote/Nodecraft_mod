package com.nodecraft.nodesystem.api;

/**
 * 节点端口接口，定义了节点输入/输出端口的基本行为
 */
public interface IPort {
    
    /**
     * 获取端口ID
     * @return 端口ID
     */
    String getId();
    
    /**
     * 获取端口显示名称
     * @return 显示名称
     */
    String getDisplayName();
    
    /**
     * 获取端口描述
     * @return 描述
     */
    String getDescription();
    
    /**
     * 获取端口的数据类型
     * @return 数据类型
     */
    NodeDataType getDataType();
    
    /**
     * 判断此端口是否为输入端口
     * @return 如果是输入端口返回true，否则为false
     */
    boolean isInput();
    
    /**
     * 判断端口是否已连接
     * @return 如果已连接返回true，否则为false
     */
    boolean isConnected();
    
    /**
     * 获取拥有此端口的节点
     * @return 节点
     */
    INode getNode();
    
    /**
     * 设置端口的值
     * @param value 值
     */
    void setValue(Object value);
    
    /**
     * 获取端口的值
     * @return 值
     */
    Object getValue();
    
    /**
     * 连接到另一个端口
     * @param port 目标端口
     * @return 如果连接成功返回true，否则为false
     */
    boolean connectTo(IPort port);
    
    /**
     * 断开与其他端口的连接
     */
    void disconnect();

    /**
     * 输入端口是否允许多个上游连接。
     * 默认情况下，输入端口只允许一个连接；输出端口的扇出不受此限制。
     */
    default boolean allowsMultipleIncomingConnections() {
        return false;
    }

    /**
     * 输入端口是否为必填。
     * 默认必填；节点可将某些输入端口标记为可选以避免状态误报。
     */
    default boolean isRequired() {
        return true;
    }
} 
