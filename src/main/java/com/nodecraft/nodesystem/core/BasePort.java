package com.nodecraft.nodesystem.core;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.PortTypeResolver;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default port implementation.
 *
 * <p>Direction is explicit metadata and is assigned by the owning node when the
 * port is added as an input or output.</p>
 */
public class BasePort implements IPort {

    public enum Direction {
        INPUT,
        OUTPUT,
        UNASSIGNED
    }

    private final String id;
    private final String displayName;
    private final String description;
    private final NodeDataType dataType;
    private final INode node;
    private final boolean allowMultipleIncomingConnections;
    private final boolean required;
    private String listTypeVariable;
    private boolean listElementBinding;
    private Direction direction = Direction.UNASSIGNED;
    private boolean isConnected = false;
    private IPort connectedPort = null;
    private final Set<IPort> connectedPorts = new LinkedHashSet<>();
    private Object value;

    public BasePort(String id, String displayName, String description, NodeDataType dataType, INode node) {
        this(id, displayName, description, dataType, node, false, true);
    }

    public BasePort(String id, String displayName, String description, NodeDataType dataType, INode node,
                    boolean allowMultipleIncomingConnections) {
        this(id, displayName, description, dataType, node, allowMultipleIncomingConnections, true);
    }

    public BasePort(String id, String displayName, String description, NodeDataType dataType, INode node,
                    boolean allowMultipleIncomingConnections, boolean required) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.dataType = dataType;
        this.node = node;
        this.allowMultipleIncomingConnections = allowMultipleIncomingConnections;
        this.required = required;
        initializeDefaultValue();
    }

    /**
     * Binds this list port to a shared type variable on the owning node (List&lt;T&gt; preservation).
     */
    public BasePort bindListType(String variable) {
        this.listTypeVariable = variable;
        this.listElementBinding = false;
        return this;
    }

    /**
     * Binds this port to the element type of a shared list type variable.
     */
    public BasePort bindListElementType(String variable) {
        this.listTypeVariable = variable;
        this.listElementBinding = true;
        return this;
    }

    private void initializeDefaultValue() {
        switch (dataType) {
            case INTEGER -> value = 0;
            case FLOAT -> value = 0.0f;
            case DOUBLE -> value = 0.0d;
            case BOOLEAN -> value = false;
            case STRING -> value = "";
            default -> value = null;
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
    public String getListTypeVariable() {
        return listTypeVariable;
    }

    @Override
    public boolean isListElementBinding() {
        return listElementBinding;
    }

    @Override
    public boolean isInput() {
        return resolveDirection() == Direction.INPUT;
    }

    public boolean isOutput() {
        return resolveDirection() == Direction.OUTPUT;
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public boolean allowsMultipleIncomingConnections() {
        return allowMultipleIncomingConnections;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public INode getNode() {
        return node;
    }

    public Set<IPort> getConnectedPorts() {
        return Collections.unmodifiableSet(connectedPorts);
    }

    @Override
    public void setValue(Object newValue) {
        NodeDataType effective = PortTypeResolver.resolveEffectiveType(this);
        if (effective.isCompatible(newValue)) {
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
            com.nodecraft.core.NodeCraft.LOGGER.debug(
                    "Port connect rejected: target is null or self (sourcePort={}, sourceNode={})",
                    getId(),
                    node != null ? node.getId() : "null"
            );
            return false;
        }

        if (isInput() == targetPort.isInput()) {
            com.nodecraft.core.NodeCraft.LOGGER.debug(
                    "Port connect rejected: same direction (sourcePort={}, sourceIsInput={}, targetPort={}, targetIsInput={})",
                    getId(),
                    isInput(),
                    targetPort.getId(),
                    targetPort.isInput()
            );
            return false;
        }

        IPort outputPort = isOutput() ? this : targetPort;
        IPort inputPort = isOutput() ? targetPort : this;

        if (inputPort.isConnected() && !inputPort.allowsMultipleIncomingConnections()) {
            com.nodecraft.core.NodeCraft.LOGGER.debug(
                    "Port connect rejected: input already connected and does not allow multiple incoming (inputNode={}, inputPort={})",
                    inputPort.getNode() != null ? inputPort.getNode().getId() : "null",
                    inputPort.getId()
            );
            return false;
        }

        if (!PortTypeResolver.isConnectable(outputPort, inputPort)) {
            String reason = PortTypeResolver.connectabilityRejectionReason(outputPort, inputPort);
            com.nodecraft.core.NodeCraft.LOGGER.debug(
                    "Port connect rejected: {} (outputNode={}, outputPort={}, inputNode={}, inputPort={})",
                    reason,
                    outputPort.getNode() != null ? outputPort.getNode().getId() : "null",
                    outputPort.getId(),
                    inputPort.getNode() != null ? inputPort.getNode().getId() : "null",
                    inputPort.getId()
            );
            return false;
        }

        if (outputPort instanceof BasePort outputBase && inputPort instanceof BasePort inputBase) {
            outputBase.link(inputBase);
            inputBase.link(outputBase);
            return true;
        }

        com.nodecraft.core.NodeCraft.LOGGER.debug(
                "Port connect rejected: unsupported port implementation (output={}, input={})",
                outputPort.getClass().getName(),
                inputPort.getClass().getName()
        );
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

    public void disconnectFrom(IPort port) {
        if (port instanceof BasePort basePort) {
            unlink(basePort);
            basePort.unlink(this);
        }
    }

    public void setDirection(Direction direction) {
        if (direction != null) {
            this.direction = direction;
        }
    }

    public Direction getDirection() {
        return direction;
    }

    private Direction resolveDirection() {
        if (direction != Direction.UNASSIGNED) {
            return direction;
        }

        if (node != null) {
            if (node.getInputPorts() != null) {
                for (IPort inputPort : node.getInputPorts()) {
                    if (inputPort == this) {
                        return Direction.INPUT;
                    }
                }
            }
            if (node.getOutputPorts() != null) {
                for (IPort outputPort : node.getOutputPorts()) {
                    if (outputPort == this) {
                        return Direction.OUTPUT;
                    }
                }
            }
        }

        com.nodecraft.core.NodeCraft.LOGGER.warn(
                "Port {} on node {} has no explicit direction metadata. Treating it as input until registered.",
                id,
                node != null ? node.getId() : "null"
        );
        return Direction.INPUT;
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
