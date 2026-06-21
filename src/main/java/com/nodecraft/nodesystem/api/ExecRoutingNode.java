package com.nodecraft.nodesystem.api;

import java.util.List;
import java.util.Set;

/**
 * Nodes that selectively fire exec output ports after {@link INode#compute}.
 */
public interface ExecRoutingNode extends INode {

    /**
     * Exec output port ids that should enqueue downstream nodes this step.
     * Use {@link java.util.LinkedHashSet} when port order matters.
     */
    Set<String> getActiveExecOutputPortIds();

    /**
     * When true, each active exec port's downstream subtree fully drains before the next port fires.
     */
    default boolean drainExecPortsSequentially() {
        return false;
    }
}
