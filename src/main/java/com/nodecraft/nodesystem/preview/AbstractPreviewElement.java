package com.nodecraft.nodesystem.preview;

import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * 抽象预览元素基类
 * 所有预览元素都继承自此类
 */
public abstract class AbstractPreviewElement {
    
    protected final String id;
    protected final String ownerNodeId;
    protected final long createdTime;
    protected volatile long lastUpdatedTime;
    
    protected boolean visible = true;
    protected float opacity = 1.0f;
    protected int renderPriority = 0; // 渲染优先级，数值越小越先渲染
    
    protected PreviewOptions options;
    
    public AbstractPreviewElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        this.id = id;
        this.ownerNodeId = ownerNodeId;
        this.createdTime = System.currentTimeMillis();
        this.lastUpdatedTime = this.createdTime;
        this.options = options != null ? options : new PreviewOptions();
        
        // 从选项中设置基础属性
        if (this.options.opacity != null) {
            this.opacity = this.options.opacity;
        }
        if (this.options.visible != null) {
            this.visible = this.options.visible;
        }
        if (this.options.renderPriority != null) {
            this.renderPriority = this.options.renderPriority;
        }
    }
    
    // ================= 抽象方法 =================
    
    /**
     * 处理输入数据，子类需要实现此方法来解析和存储特定类型的数据
     */
    protected abstract void processData(Object data);
    
    /**
     * 渲染预览元素
     * @param matrices 矩阵栈
     * @param camera 相机
     * @param partialTicks 部分tick
     * @param globalOpacity 全局透明度
     */
    public abstract void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity);
    
    /**
     * 判断是否应该渲染此元素（距离剔除、视锥剔除等）
     */
    public abstract boolean shouldRender(Camera camera);
    
    /**
     * 清理资源
     */
    public abstract void cleanup();
    
    // ================= 基础方法 =================
    
    public String getId() {
        return id;
    }
    
    public String getOwnerNodeId() {
        return ownerNodeId;
    }
    
    public long getCreatedTime() {
        return createdTime;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    public float getOpacity() {
        return opacity;
    }
    
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
    }
    
    public int getRenderPriority() {
        return renderPriority;
    }
    
    public void setRenderPriority(int priority) {
        this.renderPriority = priority;
    }
    
    public PreviewOptions getOptions() {
        return options;
    }
    
    // ================= 更新方法 =================
    
    /**
     * 更新数据
     */
    public void updateData(Object newData) {
        processData(newData);
        touch();
    }
    
    /**
     * 更新选项
     */
    public void updateOptions(PreviewOptions newOptions) {
        if (newOptions != null) {
            this.options = newOptions;
            
            // 更新基础属性
            if (newOptions.opacity != null) {
                setOpacity(newOptions.opacity);
            }
            if (newOptions.visible != null) {
                setVisible(newOptions.visible);
            }
            if (newOptions.renderPriority != null) {
                setRenderPriority(newOptions.renderPriority);
            }
            touch();
        }
    }
    
    // ================= 辅助方法 =================
    
    /**
     * 计算到相机的距离
     */
    protected double getDistanceToCamera(Camera camera, Vec3d position) {
        Vec3d cameraPos = camera.getCameraPos();
        return cameraPos.distanceTo(position);
    }
    
    /**
     * 基于距离计算透明度衰减
     */
    protected float calculateDistanceFade(Camera camera, Vec3d position, float maxDistance) {
        double distance = getDistanceToCamera(camera, position);
        if (distance > maxDistance) {
            return 0.0f;
        }
        
        // 线性衰减
        float fadeStart = maxDistance * 0.8f;
        if (distance > fadeStart) {
            return 1.0f - (float)((distance - fadeStart) / (maxDistance - fadeStart));
        }
        
        return 1.0f;
    }
    
    /**
     * 检查位置是否在视锥内（简单的球体检查）
     */
    protected boolean isInFrustum(Camera camera, Vec3d position, float radius) {
        // 简单的距离检查，实际实现可以更精确
        double distance = getDistanceToCamera(camera, position);
        return distance <= PreviewRenderer.getInstance().getSettings().maxRenderDistance + radius;
    }
    
    /**
     * 获取年龄（毫秒）
     */
    protected long getAge() {
        return System.currentTimeMillis() - createdTime;
    }

    /**
     * Refresh the expiration timer when an existing preview is updated in place.
     */
    protected void touch() {
        lastUpdatedTime = System.currentTimeMillis();
    }

    protected long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdatedTime;
    }
    
    /**
     * 检查是否过期（如果设置了持续时间）
     */
    protected boolean isExpired() {
        if (options.duration != null && options.duration > 0) {
            return getTimeSinceLastUpdate() > options.duration * 1000L;
        }
        return false;
    }
} 
