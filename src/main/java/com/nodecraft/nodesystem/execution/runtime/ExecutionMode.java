package com.nodecraft.nodesystem.execution.runtime;

/**
 * How a graph execution session should behave.
 */
public enum ExecutionMode {
    /** Interactive editor auto-preview: incremental, skip permanent side effects. */
    PREVIEW,
    /** Explicit user Run from the editor. */
    MANUAL,
    /** Non-UI / test / future dedicated-server runs. */
    HEADLESS
}