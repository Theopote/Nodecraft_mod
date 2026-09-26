package com.nodecraft.nodesystem.api;

/**
 * Declared execution capability of a node — used to enforce preview-mode side-effect policy.
 * <p>
 * See {@code docs/contracts/preview-side-effects.md}.
 */
public enum NodeEffect {

    /** No external side effects; pure data transformation. */
    PURE(true),

    /** Reads world or player context without mutation. */
    WORLD_READ(true),

    /** Writes to tracked preview services only (not permanent world state). */
    PREVIEW_WRITE(true),

    /** Mutates Minecraft world state (blocks, entities, commands, etc.). */
    WORLD_WRITE(false),

    /** Reads or writes files on disk. */
    FILE_IO(false),

    /** Network I/O (reserved for future nodes). */
    NETWORK(false),

    /** UI, chat, or other editor-facing effects. */
    UI_EFFECT(false),

    /** Reads execution or subgraph call-frame context without mutation (Graph Input, Get Variable). */
    CONTEXT_READ(true),

    /** Writes execution or subgraph call-frame context (Graph Output, Set Variable). */
    CONTEXT_WRITE(true),

    /** Composite shell node; child side-effect policy enforced by nested executor. */
    COMPOSITE(true),

    /** Reserved for non-runtime metadata helpers. */
    EDITOR_ONLY(true),

    /**
     * Annotation placeholder — resolved via {@link com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver}.
     */
    UNSPECIFIED(true);

    private final boolean allowedInPreview;

    NodeEffect(boolean allowedInPreview) {
        this.allowedInPreview = allowedInPreview;
    }

    public boolean isAllowedInPreview() {
        return allowedInPreview;
    }
}
