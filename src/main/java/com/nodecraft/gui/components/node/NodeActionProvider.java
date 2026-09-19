package com.nodecraft.gui.components.node;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import java.util.function.Supplier;

/**
 * Node-specific action chrome rendered in the property panel
 * (assist branch controls, apply-changes trigger, rule hints, …).
 * <p>
 * See {@code docs/architecture/property-panel-breakup.md} (Phase 4).
 */
@FunctionalInterface
public interface NodeActionProvider {

    /**
     * Renders chrome for {@code node} when this provider owns it.
     *
     * @return {@code true} if chrome was drawn (caller may add a separator)
     */
    boolean render(INode node, Supplier<NodeGraph> graphSupplier);
}
