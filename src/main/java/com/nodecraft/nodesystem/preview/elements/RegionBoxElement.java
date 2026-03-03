package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 区域框预览元素
 * 用于显示区域的边界框
 */
public class RegionBoxElement extends AbstractPreviewElement {
    
    private List<BoundingBox> regions = new ArrayList<>();
    private Vector3f color = new Vector3f(0.2f, 0.7f, 1.0f); // 默认天蓝色
    private float lineWidth = 1.5f;
    private boolean showFill = false;
    private boolean enablePulse = false;
    
    public RegionBoxElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 15; // 中等优先级
        
        // 从选项中读取设置
        if (options.color != null) {
            this.color = new Vector3f(options.color);
        }
        if (options.lineWidth != null) {
            this.lineWidth = options.lineWidth;
        }
        if (options.showFill != null) {
            this.showFill = options.showFill;
        }
        if (options.pulseAnimation != null) {
            this.enablePulse = options.pulseAnimation;
        }
    }
    
    @Override
    protected void processData(Object data) {
        regions.clear();
        
        if (data instanceof List<?>) {
            List<?> list = (List<?>) data;
            for (Object item : list) {
                processDataItem(item);
            }
        } else {
            processDataItem(data);
        }
    }
    
    private void processDataItem(Object item) {
        if (item instanceof BoundingBox) {
            regions.add((BoundingBox) item);
        } else if (item instanceof Object[]) {
            // 假设是 [minPos, maxPos] 数组
            Object[] array = (Object[]) item;
            if (array.length >= 2) {
                Vec3d min = extractPosition(array[0]);
                Vec3d max = extractPosition(array[1]);
                if (min != null && max != null) {
                    regions.add(new BoundingBox(min, max));
                }
            }
        }
        // TODO: 添加对 Region, Box 等类型的支持
    }
    
    private Vec3d extractPosition(Object obj) {
        if (obj instanceof Vec3d) {
            return (Vec3d) obj;
        }
        // TODO: 添加对其他位置类型的支持
        return null;
    }
    
    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        if (regions.isEmpty()) {
            return;
        }
        
        // 计算最终透明度
        float finalOpacity = opacity * globalOpacity;
        if (enablePulse) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.003));
            finalOpacity *= (0.3f + 0.7f * pulse);
        }
        
        if (finalOpacity <= 0.01f) {
            return;
        }
        
        // TODO: 实现实际的渲染逻辑
        // 这里将调用 Minecraft 的渲染 API 来绘制区域框
        // Rendering region boxes
    }
    
    @Override
    public boolean shouldRender(Camera camera) {
        if (regions.isEmpty()) {
            return false;
        }
        
        // 检查是否过期
        if (isExpired()) {
            return false;
        }
        
        // 检查距离
        Vec3d cameraPos = camera.getBlockPos().toCenterPos();
        float maxDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        
        for (BoundingBox region : regions) {
            Vec3d center = region.getCenter();
            if (cameraPos.distanceTo(center) <= maxDistance + region.getSize()) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public void cleanup() {
        regions.clear();
    }
    
    // ================= Getters/Setters =================
    
    public List<BoundingBox> getRegions() {
        return new ArrayList<>(regions);
    }
    
    public Vector3f getColor() {
        return new Vector3f(color);
    }
    
    public void setColor(Vector3f color) {
        this.color = new Vector3f(color);
    }
    
    public float getLineWidth() {
        return lineWidth;
    }
    
    public void setLineWidth(float lineWidth) {
        this.lineWidth = Math.max(0.1f, lineWidth);
    }
    
    public boolean isShowFill() {
        return showFill;
    }
    
    public void setShowFill(boolean showFill) {
        this.showFill = showFill;
    }
    
    public boolean isEnablePulse() {
        return enablePulse;
    }
    
    public void setEnablePulse(boolean enablePulse) {
        this.enablePulse = enablePulse;
    }
    
    // ================= 内部类 =================
    
    /**
     * 边界框
     */
    public static class BoundingBox {
        public final Vec3d min;
        public final Vec3d max;
        
        public BoundingBox(Vec3d min, Vec3d max) {
            this.min = new Vec3d(
                Math.min(min.x, max.x),
                Math.min(min.y, max.y),
                Math.min(min.z, max.z)
            );
            this.max = new Vec3d(
                Math.max(min.x, max.x),
                Math.max(min.y, max.y),
                Math.max(min.z, max.z)
            );
        }
        
        public Vec3d getCenter() {
            return new Vec3d(
                (min.x + max.x) / 2,
                (min.y + max.y) / 2,
                (min.z + max.z) / 2
            );
        }
        
        public double getSize() {
            return Math.max(
                Math.max(max.x - min.x, max.y - min.y),
                max.z - min.z
            );
        }
        
        public Vec3d getSize3D() {
            return new Vec3d(max.x - min.x, max.y - min.y, max.z - min.z);
        }
    }
} 