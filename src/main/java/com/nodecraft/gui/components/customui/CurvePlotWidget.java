package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.nodes.math.scalar_math.GraphMapperNode;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;

/**
 * Normalized curve preview and Bezier handle editor for {@link GraphMapperNode}.
 */
public final class CurvePlotWidget {

    public enum BezierHandle {
        NONE,
        P1,
        P2
    }

    /** Per-node drag capture state — survives fast mouse movement off the handle hit radius. */
    public static final class InteractionState {
        private BezierHandle capturedHandle = BezierHandle.NONE;

        public BezierHandle capturedHandle() {
            return capturedHandle;
        }

        void clearCapture() {
            capturedHandle = BezierHandle.NONE;
        }
    }

    private static final int GRID_COLOR = 0x40303030;
    private static final int CURVE_COLOR = 0xFF4FC3F7;
    private static final int HANDLE_COLOR = 0xFFFFB74D;
    private static final int HANDLE_LINE = 0x80FFB74D;
    private static final int BORDER_COLOR = 0xFF505050;
    /** Logical units — scaled by canvas zoom at render time. */
    public static final float LOGICAL_HEIGHT = 96.0f;
    private static final float HANDLE_HIT_RADIUS = 8.0f;
    private static final float HANDLE_DRAW_RADIUS = 5.0f;

    private CurvePlotWidget() {
    }

    public static boolean render(
            GraphMapperNode node,
            InteractionState interaction,
            float width,
            float plotHeight,
            float zoom,
            BezierDragCallbacks callbacks
    ) {
        if (node == null || interaction == null) {
            return false;
        }

        ImVec2 start = ImGui.getCursorScreenPos();
        float x0 = start.x;
        float y0 = start.y;
        float x1 = x0 + width;
        float y1 = y0 + plotHeight;

        ImGui.invisibleButton("##curve_plot", width, plotHeight);
        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();
        ImDrawList drawList = ImGui.getWindowDrawList();

        drawGrid(drawList, x0, y0, x1, y1);
        drawCurve(drawList, node, x0, y0, x1, y1);

        boolean changed = false;
        if (node.getCurveType() == GraphMapperNode.CurveType.BEZIER) {
            changed = dragBezierHandles(
                node, interaction, drawList, x0, y0, x1, y1, hovered || active, zoom, callbacks);
        } else if (!ImGui.isMouseDown(0)) {
            interaction.clearCapture();
        }

        drawList.addRect(x0, y0, x1, y1, BORDER_COLOR, 0.0f, 0, 1.0f);
        return changed;
    }

    public interface BezierDragCallbacks {
        void onBezierDragStarted(BezierHandle handle);

        void onBezierDragged(BezierHandle handle, double x, double y);

        void onBezierDragEnded(BezierHandle handle);
    }

    /** Logical height for {@code calculateUIHeight()} — constant across zoom levels. */
    public static float preferredHeight() {
        return LOGICAL_HEIGHT;
    }

    private static void drawGrid(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        drawList.addRectFilled(x0, y0, x1, y1, ImGui.getColorU32(ImGuiCol.FrameBg));
        for (int i = 1; i < 4; i++) {
            float t = i / 4.0f;
            float gx = x0 + (x1 - x0) * t;
            float gy = y1 - (y1 - y0) * t;
            drawList.addLine(gx, y0, gx, y1, GRID_COLOR, 1.0f);
            drawList.addLine(x0, gy, x1, gy, GRID_COLOR, 1.0f);
        }
        drawList.addLine(x0, y1, x1, y0, GRID_COLOR, 1.0f);
    }

    private static void drawCurve(ImDrawList drawList, GraphMapperNode node, float x0, float y0, float x1, float y1) {
        float w = x1 - x0;
        float h = y1 - y0;
        float prevX = x0;
        float prevY = y1 - clamp01Y(node.sampleNormalizedCurve(0.0d)) * h;
        int segments = Math.max(24, (int) (w / 4.0f));
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / (double) segments;
            float px = x0 + (float) t * w;
            float py = y1 - clamp01Y(node.sampleNormalizedCurve(t)) * h;
            drawList.addLine(prevX, prevY, px, py, CURVE_COLOR, 2.0f);
            prevX = px;
            prevY = py;
        }
    }

    private static boolean dragBezierHandles(
            GraphMapperNode node,
            InteractionState interaction,
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            boolean interactive,
            float zoom,
            @Nullable BezierDragCallbacks callbacks
    ) {
        float hitRadius = CustomUIMetricsSafe.scaleLogical(HANDLE_HIT_RADIUS, zoom);
        float handleRadius = CustomUIMetricsSafe.scaleLogical(HANDLE_DRAW_RADIUS, zoom);
        float w = x1 - x0;
        float h = y1 - y0;
        float p0x = x0;
        float p0y = y1;
        float p3x = x1;
        float p3y = y0;
        float p1x = x0 + (float) node.getBezierX1() * w;
        float p1y = y1 - (float) node.getBezierY1() * h;
        float p2x = x0 + (float) node.getBezierX2() * w;
        float p2y = y1 - (float) node.getBezierY2() * h;

        drawList.addLine(p0x, p0y, p1x, p1y, HANDLE_LINE, 1.0f);
        drawList.addLine(p3x, p3y, p2x, p2y, HANDLE_LINE, 1.0f);
        drawHandle(drawList, p1x, p1y, handleRadius);
        drawHandle(drawList, p2x, p2y, handleRadius);

        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        boolean mouseDown = ImGui.isMouseDown(0);

        if (interactive && ImGui.isItemClicked(0) && interaction.capturedHandle == BezierHandle.NONE) {
            if (distance(mx, my, p1x, p1y) <= hitRadius) {
                interaction.capturedHandle = BezierHandle.P1;
                if (callbacks != null) {
                    callbacks.onBezierDragStarted(BezierHandle.P1);
                }
            } else if (distance(mx, my, p2x, p2y) <= hitRadius) {
                interaction.capturedHandle = BezierHandle.P2;
                if (callbacks != null) {
                    callbacks.onBezierDragStarted(BezierHandle.P2);
                }
            }
        }

        if (!mouseDown) {
            if (interaction.capturedHandle != BezierHandle.NONE) {
                BezierHandle ended = interaction.capturedHandle;
                interaction.clearCapture();
                if (callbacks != null) {
                    callbacks.onBezierDragEnded(ended);
                }
            }
            return false;
        }

        if (interaction.capturedHandle == BezierHandle.NONE) {
            return false;
        }

        double nx = clamp01((mx - x0) / Math.max(1.0f, w));
        double ny = clamp01((y1 - my) / Math.max(1.0f, h));
        if (callbacks != null) {
            callbacks.onBezierDragged(interaction.capturedHandle, nx, ny);
        }
        return true;
    }

    private static void drawHandle(ImDrawList drawList, float x, float y, float radius) {
        drawList.addCircleFilled(x, y, radius, HANDLE_COLOR);
        drawList.addCircle(x, y, radius, BORDER_COLOR, 12, 1.0f);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static float clamp01Y(double mapped) {
        if (!Double.isFinite(mapped)) {
            return 0.0f;
        }
        return (float) Math.max(0.0d, Math.min(1.0d, mapped));
    }
}
