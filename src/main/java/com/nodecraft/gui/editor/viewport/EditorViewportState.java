package com.nodecraft.gui.editor.viewport;

/**
 * Canvas viewport state: zoom, pan offset, and grid visibility.
 * <p>
 * Single owner for these fields inside the editor stack. See
 * {@code docs/architecture/imgui-node-editor-breakup.md} (Phase J).
 */
public final class EditorViewportState {

    public static final float DEFAULT_ZOOM = 1.0f;
    /** Matches {@code CanvasComponent.CanvasConstants.MIN_ZOOM}. */
    public static final float MIN_ZOOM = 0.2f;
    /** Matches {@code CanvasComponent.CanvasConstants.MAX_ZOOM}. */
    public static final float MAX_ZOOM = 3.0f;

    private float zoom = DEFAULT_ZOOM;
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;
    private boolean showGrid = true;

    public float getZoom() {
        return zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = clampZoom(zoom);
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public void setOffset(float offsetX, float offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public void setView(float zoom, float offsetX, float offsetY) {
        this.zoom = clampZoom(zoom);
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    /**
     * Resets zoom/offset to defaults without changing grid visibility.
     */
    public void resetView() {
        this.zoom = DEFAULT_ZOOM;
        this.offsetX = 0.0f;
        this.offsetY = 0.0f;
    }

    public float screenToWorldX(float screenX, float canvasOriginX) {
        return (screenX - canvasOriginX - offsetX) / zoom;
    }

    public float screenToWorldY(float screenY, float canvasOriginY) {
        return (screenY - canvasOriginY - offsetY) / zoom;
    }

    public float worldToScreenX(float worldX, float canvasOriginX) {
        return canvasOriginX + worldX * zoom + offsetX;
    }

    public float worldToScreenY(float worldY, float canvasOriginY) {
        return canvasOriginY + worldY * zoom + offsetY;
    }

    private static float clampZoom(float zoom) {
        if (Float.isNaN(zoom) || Float.isInfinite(zoom)) {
            return DEFAULT_ZOOM;
        }
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }
}
