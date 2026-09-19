package com.nodecraft.nodesystem.api;

/**
 * Optional capability for nodes that can reset their editable properties from the property panel.
 * <p>
 * Replaces reflective {@code getMethod("resetProperties")} probes in the UI layer.
 */
public interface ResettableNode {

    /**
     * Restores this node's editable properties to a clean baseline.
     */
    void resetProperties();
}
