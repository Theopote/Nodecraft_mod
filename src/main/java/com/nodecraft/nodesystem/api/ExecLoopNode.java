package com.nodecraft.nodesystem.api;

/**
 * Exec-flow nodes that drive repeated body execution (e.g. {@code flow.loop.for_each}).
 *
 * <p>The executor calls {@link #prepareExecLoopIteration(int)} before draining each body
 * iteration so per-item data outputs stay in sync with the exec frontier.</p>
 */
public interface ExecLoopNode extends ExecRoutingNode {

    /**
     * Number of {@link #execBodyPortId()} iterations to drain after the node computes.
     */
    int execLoopIterationCount();

    /**
     * Refreshes per-iteration data outputs before the body subtree runs.
     */
    void prepareExecLoopIteration(int iterationIndex);

    String execBodyPortId();

    String execCompletePortId();
}
