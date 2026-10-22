package com.nodecraft.gui.components.customui;

import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;

/**
 * Safe access to ImGui layout metrics. Native ImGui calls without a current context can SIGSEGV the JVM
 * (e.g. server-side GameTest, registry init, unit tests). Use these helpers in {@code calculateUIHeight}
 * and shared widget sizing — never call {@link ImGui#getFrameHeight()} / {@link ImGui#getTextLineHeight()} directly.
 */
public final class CustomUIMetricsSafe {

    /**
     * Conservative frame height when ImGui context is unavailable.
     * Matches typical in-game compact custom UI controls so row/button layout is not underestimated.
     */
    public static final float FALLBACK_FRAME_HEIGHT = 22.0f;
    /** Typical default text line height when ImGui context is unavailable. */
    public static final float FALLBACK_TEXT_LINE_HEIGHT = 16.0f;
    /** Typical ImGui item spacing when context is unavailable. */
    public static final float FALLBACK_ITEM_SPACING_Y = 4.0f;

    /**
     * Set by {@link com.nodecraft.gui.editor.integration.ImGuiRenderer} after {@link ImGui#createContext()}.
     * Cleared on renderer shutdown. Never call native ImGui metric APIs unless this flag is true
     * and the caller is on the client render thread.
     */
    private static volatile boolean editorContextBound;

    private CustomUIMetricsSafe() {
    }

    /** Called once the editor ImGui context is created on the client render thread. */
    public static void bindEditorContext() {
        editorContextBound = true;
    }

    /** Called when the editor ImGui context is disposed — prevents stale bound state. */
    public static void unbindEditorContext() {
        editorContextBound = false;
    }

    /**
     * @return true when running on the Minecraft client render thread
     */
    public static boolean isOnRenderThread() {
        try {
            RenderSystem.assertOnRenderThread();
            return true;
        } catch (IllegalStateException | NullPointerException ignored) {
            return false;
        }
    }

    /**
     * @return true when native ImGui metrics may be queried on this thread
     */
    public static boolean hasImGuiContext() {
        if (!editorContextBound || !isOnRenderThread()) {
            return false;
        }
        try {
            return ImGui.getCurrentContext() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static float frameHeight() {
        if (!hasImGuiContext()) {
            return FALLBACK_FRAME_HEIGHT;
        }
        try {
            return ImGui.getFrameHeight();
        } catch (Throwable ignored) {
            return FALLBACK_FRAME_HEIGHT;
        }
    }

    public static float textLineHeight() {
        if (!hasImGuiContext()) {
            return FALLBACK_TEXT_LINE_HEIGHT;
        }
        try {
            return ImGui.getTextLineHeight();
        } catch (Throwable ignored) {
            return FALLBACK_TEXT_LINE_HEIGHT;
        }
    }

    /**
     * Baseline frame height in logical units for {@code calculateUIHeight()} — stable across canvas zoom.
     * Do not use live {@link #frameHeight()} for layout sizing; CustomUIRenderer scales style at render time.
     */
    public static float logicalFrameHeight() {
        return FALLBACK_FRAME_HEIGHT;
    }

    /**
     * Baseline text line height in logical units for {@code calculateUIHeight()}.
     */
    public static float logicalTextLineHeight() {
        return FALLBACK_TEXT_LINE_HEIGHT;
    }

    /** Converts a logical dimension to screen pixels for the current canvas zoom. */
    public static float scaleLogical(float logicalSize, float zoom) {
        return logicalSize * Math.max(0.001f, zoom);
    }

    /**
     * Frame height for {@code calculateUIHeight()} on the client — uses live ImGui metrics when safe,
     * never underestimates {@link #FALLBACK_FRAME_HEIGHT}. Matches default editor style sizing so the
     * reserved node body is not shorter than rendered widgets.
     */
    public static float layoutFrameHeight() {
        if (!hasImGuiContext()) {
            return FALLBACK_FRAME_HEIGHT;
        }
        return Math.max(FALLBACK_FRAME_HEIGHT, frameHeight());
    }

    /**
     * Text line height for {@code calculateUIHeight()} on the client.
     */
    public static float layoutTextLineHeight() {
        if (!hasImGuiContext()) {
            return FALLBACK_TEXT_LINE_HEIGHT;
        }
        return Math.max(FALLBACK_TEXT_LINE_HEIGHT, textLineHeight());
    }

    /** Vertical gap ImGui inserts after each widget row in {@code calculateUIHeight()}. */
    public static float layoutItemSpacingY() {
        if (!hasImGuiContext()) {
            return FALLBACK_ITEM_SPACING_Y;
        }
        try {
            return Math.max(FALLBACK_ITEM_SPACING_Y, ImGui.getStyle().getItemSpacingY());
        } catch (Throwable ignored) {
            return FALLBACK_ITEM_SPACING_Y;
        }
    }
}
