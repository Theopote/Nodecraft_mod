package com.nodecraft.gui.layout;

import imgui.ImGui;

/**
 * RAII wrapper for {@link ImGui#beginChild} / {@link ImGui#endChild}.
 * <p>
 * Dear ImGui requires {@code EndChild()} for every {@code BeginChild()},
 * even when {@code BeginChild()} returns false (clipped/collapsed).
 * Tracked depth lets callers unwind leaked children before {@link ImGui#end()}.
 */
public final class ImGuiChildScope implements AutoCloseable {

    private static int trackedDepth = 0;

    private final boolean open;
    private boolean closed;

    public ImGuiChildScope(String id, float width, float height, boolean border, int flags) {
        // Always pair with endChild — return value only gates content submission.
        open = ImGui.beginChild(id, width, height, border, flags);
        trackedDepth++;
    }

    public static int trackedDepth() {
        return trackedDepth;
    }

    /**
     * Closes all tracked child windows still open on the stack.
     * Call before {@link ImGui#end()} on the parent window as a last-resort safety net.
     */
    public static void unwindAll() {
        while (trackedDepth > 0) {
            ImGui.endChild();
            trackedDepth--;
        }
    }

    public static void unwindTo(int targetDepth) {
        while (trackedDepth > targetDepth) {
            ImGui.endChild();
            trackedDepth--;
        }
    }

    /** {@code true} if content may be submitted; always call {@link #close()} either way. */
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            ImGui.endChild();
            trackedDepth = Math.max(0, trackedDepth - 1);
        }
    }
}
