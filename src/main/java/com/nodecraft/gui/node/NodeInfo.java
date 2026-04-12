package com.nodecraft.gui.node;

import com.nodecraft.nodesystem.api.INode;

import java.util.Objects;

/**
 * Runtime node metadata used by the registry and editor UI.
 */
public class NodeInfo {

    private final String id;
    private final String displayName;
    private final String description;
    private final String categoryId;
    private final int order;
    private final Class<? extends INode> nodeClass;
    private String icon;

    public NodeInfo(String id, String displayName, String description, String categoryId, int order, Class<? extends INode> nodeClass) {
        this.id = Objects.requireNonNull(id, "Node ID cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "Node displayName cannot be null");
        this.description = description != null ? description : "";
        this.categoryId = Objects.requireNonNull(categoryId, "Node categoryId cannot be null");
        this.order = order;
        this.nodeClass = Objects.requireNonNull(nodeClass, "Node class cannot be null for ID: " + id);
    }

    public NodeInfo(String id, String displayName, String description, String categoryId, Class<? extends INode> nodeClass) {
        this(id, displayName, description, categoryId, Integer.MAX_VALUE, nodeClass);
    }

    /**
     * @deprecated Use the constructor that includes the node class.
     */
    @Deprecated
    public NodeInfo(String id, String displayName, String description, String categoryId) {
        this(id, displayName, description, categoryId, Integer.MAX_VALUE, null);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public int getOrder() {
        return order;
    }

    public Class<? extends INode> getNodeClass() {
        return nodeClass;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeInfo nodeInfo = (NodeInfo) o;
        return Objects.equals(id, nodeInfo.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "NodeInfo{" +
            "id='" + id + '\'' +
            ", displayName='" + displayName + '\'' +
            ", categoryId='" + categoryId + '\'' +
            ", order=" + order +
            ", class=" + (nodeClass != null ? nodeClass.getSimpleName() : "null") +
            '}';
    }
}
