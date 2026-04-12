package com.nodecraft.nodesystem.core;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Common base class for node implementations.
 */
public abstract class BaseNode implements INode {

    public interface DirtyListener {
        void onNodeDirty(BaseNode node, long dirtyVersion);
    }

    private static final List<DirtyListener> DIRTY_LISTENERS = new CopyOnWriteArrayList<>();

    private final UUID id;
    private final String typeId;
    private double positionX;
    private double positionY;
    private Object nodeState;
    private transient volatile boolean dirty = true;
    private transient volatile long dirtyVersion = 1L;

    protected final List<IPort> inputPorts = new ArrayList<>();
    protected final List<IPort> outputPorts = new ArrayList<>();

    protected final Map<String, Object> inputValues = new HashMap<>();
    protected final Map<String, Object> outputValues = new HashMap<>();

    public BaseNode(UUID id, String typeId) {
        this.id = id;
        this.typeId = typeId;
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
        if (typeId != null && typeId.contains(".")) {
            return formatDisplayName(typeId.substring(typeId.lastIndexOf('.') + 1));
        }
        return typeId;
    }

    private String formatDisplayName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        String result = name.replace('_', ' ');
        StringBuilder formatted = new StringBuilder(result.length());
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
        processNode();
        syncOutputPorts();
        clearDirty();
        return new HashMap<>(outputValues);
    }

    public Map<String, Object> compute(Map<String, Object> inputs, ExecutionContext context) {
        refreshInputValues(inputs);
        processNode(context);
        syncOutputPorts();
        clearDirty();
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
     * Context-free execution entry point.
     *
     * <p>Nodes that do not need world/player context can override this method.
     * The default behavior delegates to the context-aware variant with
     * {@code null}.</p>
     */
    protected void processNode() {
        processNode(null);
    }

    /**
     * Context-aware execution entry point.
     *
     * <p>World-aware nodes should implement this method and handle a nullable
     * context defensively when they also support editor-side preview execution.</p>
     */
    public abstract void processNode(ExecutionContext context);

    @Override
    public Object getOutput(String portId) {
        return outputValues.get(portId);
    }

    @Override
    public void setInput(String portId, Object value) {
        for (IPort port : inputPorts) {
            if (!port.getId().equals(portId)) {
                continue;
            }
            if (port.getDataType().isCompatible(value)) {
                inputValues.put(portId, value);
            }
            return;
        }
    }

    protected void addInputPort(IPort port) {
        inputPorts.add(port);
    }

    protected void addOutputPort(IPort port) {
        outputPorts.add(port);
    }

    protected void syncOutputPorts() {
        for (IPort port : outputPorts) {
            port.setValue(outputValues.get(port.getId()));
        }
    }

    public Object getNodeState() {
        return nodeState;
    }

    public void setNodeState(Object state) {
        this.nodeState = state;
    }

    public boolean isDirty() {
        return dirty;
    }

    public long getDirtyVersion() {
        return dirtyVersion;
    }

    protected void clearDirty() {
        dirty = false;
    }

    public static void addDirtyListener(DirtyListener listener) {
        if (listener != null && !DIRTY_LISTENERS.contains(listener)) {
            DIRTY_LISTENERS.add(listener);
        }
    }

    public static void removeDirtyListener(DirtyListener listener) {
        if (listener != null) {
            DIRTY_LISTENERS.remove(listener);
        }
    }

    /**
     * Marks this node dirty and notifies graph-level listeners.
     */
    public void markDirty() {
        dirty = true;
        long newDirtyVersion = ++dirtyVersion;
        com.nodecraft.core.NodeCraft.LOGGER.debug("Node {} marked dirty (version {}).", getId(), newDirtyVersion);
        for (DirtyListener listener : DIRTY_LISTENERS) {
            listener.onNodeDirty(this, newDirtyVersion);
        }
    }
}
