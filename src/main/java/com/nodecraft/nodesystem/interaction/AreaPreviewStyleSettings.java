package com.nodecraft.nodesystem.interaction;

import com.nodecraft.nodesystem.preview.PreviewOptions;

/**
 * 区域框选在世界中的预览样式（视图菜单与区域选择 handler 共用）。
 */
public final class AreaPreviewStyleSettings {

    private volatile boolean showFill = true;
    private volatile boolean showOutline = true;
    private volatile boolean enablePulse = false;
    private volatile float lineWidth = 2.6f;
    private volatile float opacity = 0.38f;
    private volatile float outlineR = 1.0f;
    private volatile float outlineG = 0.82f;
    private volatile float outlineB = 0.27f;
    private volatile float fillR = 1.0f;
    private volatile float fillG = 0.72f;
    private volatile float fillB = 0.22f;

    public boolean isShowFill() {
        return showFill;
    }

    public void setShowFill(boolean value) {
        this.showFill = value;
        if (!this.showFill && !this.showOutline) {
            this.showOutline = true;
        }
    }

    public boolean isShowOutline() {
        return showOutline;
    }

    public void setShowOutline(boolean value) {
        this.showOutline = value;
        if (!this.showFill && !this.showOutline) {
            this.showFill = true;
        }
    }

    public boolean isEnablePulse() {
        return enablePulse;
    }

    public void setEnablePulse(boolean enablePulse) {
        this.enablePulse = enablePulse;
    }

    public float getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(float lineWidth) {
        this.lineWidth = Math.max(0.5f, Math.min(8.0f, lineWidth));
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.05f, Math.min(1.0f, opacity));
    }

    public float[] getOutlineColor() {
        return new float[] { outlineR, outlineG, outlineB };
    }

    public void setOutlineColor(float r, float g, float b) {
        this.outlineR = Math.max(0.0f, Math.min(1.0f, r));
        this.outlineG = Math.max(0.0f, Math.min(1.0f, g));
        this.outlineB = Math.max(0.0f, Math.min(1.0f, b));
    }

    public float[] getFillColor() {
        return new float[] { fillR, fillG, fillB };
    }

    public void setFillColor(float r, float g, float b) {
        this.fillR = Math.max(0.0f, Math.min(1.0f, r));
        this.fillG = Math.max(0.0f, Math.min(1.0f, g));
        this.fillB = Math.max(0.0f, Math.min(1.0f, b));
    }

    public void resetToDefaults() {
        this.showFill = true;
        this.showOutline = true;
        this.enablePulse = false;
        this.lineWidth = 2.6f;
        this.opacity = 0.38f;
        this.outlineR = 1.0f;
        this.outlineG = 0.82f;
        this.outlineB = 0.27f;
        this.fillR = 1.0f;
        this.fillG = 0.72f;
        this.fillB = 0.22f;
    }

    /**
     * 用于 {@link com.nodecraft.nodesystem.preview.PreviewManager#showRegionBox} / 更新区域预览。
     */
    public PreviewOptions createRegionBoxPreviewOptions() {
        PreviewOptions options = new PreviewOptions()
            .setColor(outlineR, outlineG, outlineB)
            .setTintColor(fillR, fillG, fillB)
            .setOpacity(opacity)
            .setLineWidth(lineWidth)
            .setShowFill(showFill)
            .setShowOutline(showOutline || !showFill);
        if (enablePulse) {
            options.enablePulse();
        }
        return options;
    }
}
