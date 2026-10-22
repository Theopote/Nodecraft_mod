package com.nodecraft.gui.editor.impl;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.session.NodeDisplayMode;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;

/**
 * In-game layout contract helper (Phase 3).
 *
 * <p>Call only with a live ImGui context (editor open). Logs real
 * {@link ICustomUINode#getCustomUIHeight()} / {@link ICustomUINode#getMinRequiredUIWidth()}
 * — never treat headless placeholders as pass criteria.
 *
 * <p>Enable with {@code -Dnodecraft.customui.layoutDiagnostics=true} then open the graph editor
 * (hook from {@link ImGuiNodeRenderer#renderNodesDirect} when dumping is desired).
 */
public final class CustomUIInGameLayoutDiagnostics {

    private static long lastDumpMillis;
    private static final long DUMP_INTERVAL_MS = 2000L;

    private CustomUIInGameLayoutDiagnostics() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("nodecraft.customui.layoutDiagnostics");
    }

    /**
     * Dump real custom-UI metrics for every custom-UI node on the graph.
     * Throttled to once per {@link #DUMP_INTERVAL_MS} while enabled.
     * No-ops when diagnostics are disabled or headless metrics mode is on.
     */
    public static void dumpGraphCustomUILayout(NodeGraph graph, float zoom, NodeDisplayMode displayMode) {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDumpMillis < DUMP_INTERVAL_MS) {
            return;
        }
        lastDumpMillis = now;
        if (Boolean.getBoolean("nodecraft.customui.headlessMetrics")) {
            NodeCraft.LOGGER.warn(
                    "[CustomUI Layout] Skipping dump: headlessMetrics=true (placeholders only)");
            return;
        }
        if (graph == null) {
            return;
        }

        try {
            // Prove ImGui context exists before reading node metrics that call ImGui.getFrameHeight().
            imgui.ImGui.getFrameHeight();
        } catch (Throwable t) {
            NodeCraft.LOGGER.warn("[CustomUI Layout] ImGui context unavailable: {}", t.getMessage());
            return;
        }

        NodeCraft.LOGGER.info(
                "[CustomUI Layout] dump zoom={} mode={} nodes={}",
                zoom, displayMode, graph.getNodes().size());

        for (INode node : graph.getNodes()) {
            if (!(node instanceof ICustomUINode custom) || !custom.hasCustomUI()) {
                continue;
            }
            float height = custom.getCustomUIHeight();
            float width = custom.getMinRequiredUIWidth();
            NodeVisualLayout.CustomUIMetrics metrics = NodeVisualLayout.metricsFromNode(node);
            float scaledH = height * zoom;
            float scaledW = width * zoom;
            NodeCraft.LOGGER.info(
                    "[CustomUI Layout] type={} enabled={} uiH={} minW={} scaledH={} scaledW={} metricsH={}",
                    node.getTypeId(),
                    metrics.enabled(),
                    height,
                    width,
                    scaledH,
                    scaledW,
                    metrics.height());
        }
    }
}
