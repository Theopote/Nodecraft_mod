package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;

/**
 * Frame-scoped editor graph binding for custom UI widgets that need the active {@link NodeGraph}
 * (e.g. Manual Trigger → {@code GraphManualExecution.submit}).
 *
 * <p>Bound once per canvas frame from {@link com.nodecraft.gui.editor.impl.CustomUIRenderer};
 * cleared at end of frame. Never holds a stale graph across frames.
 */
public final class CustomUIEditorContext {

    private static volatile @Nullable NodeGraph currentGraph;

    private CustomUIEditorContext() {
    }

    public static void bind(@Nullable NodeGraph graph) {
        currentGraph = graph;
    }

    public static void unbind() {
        currentGraph = null;
    }

    public static @Nullable NodeGraph currentGraph() {
        return currentGraph;
    }
}
