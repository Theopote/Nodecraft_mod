package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.preview.protocol.PreviewKind;
import com.nodecraft.nodesystem.preview.protocol.PreviewStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "Smooth Curves" option travels through {@link PreviewStyle}, which encodes it inside
 * {@code textureMode} as {@code curve_smooth} / {@code curve_linear}. These tests pin the
 * round-trip so the flag can never silently degrade into a no-op again.
 */
class PreviewStyleCurveSmoothRoundTripTest {

    @Test
    void smoothEnabled_roundTripsAsSmooth() {
        PreviewOptions options = new PreviewOptions();
        options.smoothCurves = true;

        PreviewStyle style = PreviewStyle.from(options, PreviewKind.CURVES);
        assertEquals("curve_smooth", style.textureMode());

        PreviewOptions restored = style.toPreviewOptions(PreviewKind.CURVES);
        assertTrue(Boolean.TRUE.equals(restored.smoothCurves),
            "Smooth flag must survive the PreviewStyle round-trip");
    }

    @Test
    void smoothDisabled_roundTripsAsLinear() {
        PreviewOptions options = new PreviewOptions();
        options.smoothCurves = false;

        PreviewStyle style = PreviewStyle.from(options, PreviewKind.CURVES);
        assertEquals("curve_linear", style.textureMode());

        PreviewOptions restored = style.toPreviewOptions(PreviewKind.CURVES);
        assertFalse(Boolean.TRUE.equals(restored.smoothCurves),
            "Linear flag must survive the PreviewStyle round-trip");
    }
}
