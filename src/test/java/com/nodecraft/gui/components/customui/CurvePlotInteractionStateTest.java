package com.nodecraft.gui.components.customui;

import com.nodecraft.gui.components.customui.CurvePlotWidget.BezierHandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurvePlotInteractionStateTest {

    @Test
    void interactionStateStartsWithNoCapture() {
        CurvePlotWidget.InteractionState state = new CurvePlotWidget.InteractionState();
        assertEquals(BezierHandle.NONE, state.capturedHandle());
    }

    @Test
    void clearCaptureResetsHandle() {
        CurvePlotWidget.InteractionState state = new CurvePlotWidget.InteractionState();
        state.clearCapture();
        assertEquals(BezierHandle.NONE, state.capturedHandle());
    }
}
