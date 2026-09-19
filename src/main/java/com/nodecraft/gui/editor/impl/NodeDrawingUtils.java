package com.nodecraft.gui.editor.impl;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;

/**
 * 节点绘制工具类
 * 提供通用的绘制工具方法，包括网格、选择框等
 */
public class NodeDrawingUtils {

    /**
     * 绘制画布网格
     */
    public static void drawGrid(ImDrawList drawList, ImVec2 canvasTopLeft, float canvasViewWidth, 
                                float canvasViewHeight, ICanvasEditor editor) {
        float canvasZoom = editor.getCanvasZoom();
        float canvasOffsetX = editor.getCanvasOffsetX();
        float canvasOffsetY = editor.getCanvasOffsetY();

        float gridSize = 20.0f * canvasZoom;
        int gridColor = ImGui.getColorU32(ImGuiCol.TextDisabled, 0.1f);

        float startX = canvasTopLeft.x + (canvasOffsetX % gridSize);
        while (startX > canvasTopLeft.x) startX -= gridSize;
        while (startX < canvasTopLeft.x - gridSize) startX += gridSize;

        float startY = canvasTopLeft.y + (canvasOffsetY % gridSize);
        while (startY > canvasTopLeft.y) startY -= gridSize;
        while (startY < canvasTopLeft.y - gridSize) startY += gridSize;

        float canvasRight = canvasTopLeft.x + canvasViewWidth;
        float canvasBottom = canvasTopLeft.y + canvasViewHeight;

        for (float x = startX; x < canvasRight; x += gridSize) {
            if (x >= canvasTopLeft.x && x <= canvasRight) {
                drawList.addLine(x, canvasTopLeft.y, x, canvasBottom, gridColor, 1.0f);
            }
        }
        for (float y = startY; y < canvasBottom; y += gridSize) {
            if (y >= canvasTopLeft.y && y <= canvasBottom) {
                drawList.addLine(canvasTopLeft.x, y, canvasRight, y, gridColor, 1.0f);
            }
        }
    }

    /**
     * 绘制选择框
     */
    public static void drawSelectionBox(ImDrawList drawList, ImVec2 canvasPos, NodePosition boxSelectStart, 
                                        NodePosition boxSelectEnd, ICanvasEditor editor) {
        float canvasZoom = editor.getCanvasZoom();
        float canvasOffsetX = editor.getCanvasOffsetX();
        float canvasOffsetY = editor.getCanvasOffsetY();

        float startX = canvasPos.x + boxSelectStart.x * canvasZoom + canvasOffsetX;
        float startY = canvasPos.y + boxSelectStart.y * canvasZoom + canvasOffsetY;
        float endX = canvasPos.x + boxSelectEnd.x * canvasZoom + canvasOffsetX;
        float endY = canvasPos.y + boxSelectEnd.y * canvasZoom + canvasOffsetY;

        float minX = Math.min(startX, endX);
        float maxX = Math.max(startX, endX);
        float minY = Math.min(startY, endY);
        float maxY = Math.max(startY, endY);

        int fillColor = ImGui.getColorU32(ImGuiCol.NavHighlight, 0.2f);
        int borderColor = ImGui.getColorU32(ImGuiCol.NavHighlight, 0.8f);

        drawList.addRectFilled(minX, minY, maxX, maxY, fillColor, 1.0f);
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 1.0f, 0, 2.0f);
    }

    /**
     * 快速调整亮度
     */
    public static int adjustBrightnessFast(int color, float factor) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        red = Math.min(255, (int)(red * factor));
        green = Math.min(255, (int)(green * factor));
        blue = Math.min(255, (int)(blue * factor));

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /**
     * 快速调整透明度
     */
    public static int adjustAlphaFast(int color, float alpha) {
        int alphaInt = Math.min(Math.max((int)(alpha * 255), 0), 255);
        return (color & 0x00FFFFFF) | (alphaInt << 24);
    }
} 