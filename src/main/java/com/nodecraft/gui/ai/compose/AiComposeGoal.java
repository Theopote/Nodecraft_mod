package com.nodecraft.gui.ai.compose;

/**
 * Composer termination intent. Prefer this over raw {@code WORLD_APPLY}/{@code APPLY} capabilities.
 */
public enum AiComposeGoal {
    /** Default Preview-first terminal (geometry or blocks). */
    PREVIEW,
    /** Allow world-write apply nodes when user explicitly asked to write/bake into world. */
    WORLD_OUTPUT,
    /** Complete missing capabilities without forcing a new preview sink. */
    CAPABILITY_SET
}
