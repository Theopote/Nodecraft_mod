package com.nodecraft.nodesystem.preview.gizmo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GizmoPortConstraintsTest {

    @Test
    void resolveEffectiveMode_disablesMoveWhenTranslationPortWired() {
        GizmoPortConstraints constraints = new GizmoPortConstraints(false, true, true);
        assertEquals("none", constraints.resolveEffectiveMode("move"));
        assertEquals("all", constraints.resolveEffectiveMode("all"));
    }
}
