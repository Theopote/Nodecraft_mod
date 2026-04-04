package com.nodecraft.nodesystem.core;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 基础端口类，实现 IPort 接口。
 *
 * 连接语义：
 * - 输入端口最多只能连接一个上游输出端口
 * - 输出端口可以连接多个下游输入端口
 */
public class BasePort implements IPort {

    private final String id;
    private final String displayName;
    private final String description;
    private final NodeDataType dataType;
    private final INode node;
    private boolean isConnected = false;
    private IPort connectedPort = null;
    private final Set<IPort> connectedPorts = new LinkedHashSet<>();
    private Object value;

    public BasePort(String id, String displayName, String description, NodeDataType dataType, INode node) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.dataType = dataType;
        this.node = node;
        initializeDefaultValue();
    }

    private void initializeDefaultValue() {
        switch (dataType) {
            case INTEGER:
                value = 0;
                break;
            case FLOAT:
                value = 0.0f;
                break;
            case DOUBLE:
                value = 0.0d;
                break;
            case BOOLEAN:
                value = false;
                break;
            case STRING:
                value = "";
                break;
            case VECTOR:
            case BLOCK_POS:
            case BLOCK_LIST:
            case CURVE:
            case COLOR:
            case ANY:
            default:
                value = null;
                break;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public NodeDataType getDataType() {
        return dataType;
    }

    @Override
    public boolean isInput() {
        return !isOutput();
    }

    public boolean isOutput() {
        return id.startsWith("output_");
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public INode getNode() {
        return node;
    }

    @Override
    public void setValue(Object newValue) {
        if (dataType.isCompatible(newValue)) {
            this.value = newValue;
        }
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public boolean connectTo(IPort targetPort) {
        if (targetPort == null || targetPort == this) {
            return false;
        }

        if (isInput() == targetPort.isInput()) {
            return false;
        }

        IPort outputPort = isOutput() ? this : targetPort;
        IPort inputPort = isOutput() ? targetPort : this;

        if (!NodeDataType.isConnectableTo(outputPort.getDataType(), inputPort.getDataType())) {
            return false;
        }

        if (inputPort.isConnected()) {
            inputPort.disconnect();
        }

        if (outputPort instanceof BasePort outputBase && inputPort instanceof BasePort inputBase) {
            outputBase.link(inputBase);
            inputBase.link(outputBase);
            return true;
        }

        return false;
    }

    @Override
    public void disconnect() {
        if (!isConnected || connectedPorts.isEmpty()) {
            return;
        }

        for (IPort port : Set.copyOf(connectedPorts)) {
            if (port instanceof BasePort basePort) {
                unlink(basePort);
                basePort.unlink(this);
            }
        }
    }

    /**
     * Disconnect this port from one specific peer without affecting other links.
     */
    public void disconnectFrom(IPort port) {
        if (!(port instanceof BasePort basePort)) {
            return;
        }

        unlink(basePort);
        basePort.unlink(this);
    }

    private void link(IPort port) {
        connectedPorts.add(port);
        isConnected = true;
        connectedPort = connectedPorts.stream().findFirst().orElse(null);
    }

    private void unlink(IPort port) {
        connectedPorts.remove(port);
        isConnected = !connectedPorts.isEmpty();
        connectedPort = connectedPorts.stream().findFirst().orElse(null);
    }
}
