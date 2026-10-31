package com.nodecraft.client.input;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.mixin.accessor.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * NodeCraft输入系统
 * 使用投影矩阵反投影方法从鼠标光标位置进行射线检测（而非屏幕中心十字准星）
 * 参考 TreeFactory / ChronoBlocks 的实现
 */
public final class NodecraftInputSystem {

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    // 射线缓存优化
    private static Vec3d lastCameraPos = null;
    private static double lastMouseX = Double.NaN;
    private static double lastMouseY = Double.NaN;
    private static BlockHitResult cachedRay = null;
    private static final double CACHE_POSITION_EPSILON = 0.001;
    private static final double MAX_RAYCAST_DISTANCE = 256.0;

    private NodecraftInputSystem() {}

    /**
     * World-space mouse ray matching the rendered camera (actual FOV + camera quaternion).
     * Prefer this for gizmo/handle picking so hit tests align with on-screen visuals.
     */
    public record MouseRay(Vec3d origin, Vec3d direction) {
    }

    /**
     * Builds the same mouse→world ray used by {@link #raycastFromMouse()} without doing a block hit.
     * Uses {@code MinecraftClient#mouse} coordinates.
     */
    public static MouseRay rayFromMouse() {
        if (MC.mouse == null) {
            return null;
        }
        return rayFromScreen(MC.mouse.getX(), MC.mouse.getY());
    }

    /**
     * Accurate FOV mouse→world ray from explicit screen/window coordinates
     * (e.g. ImGui {@code io.getMousePos()}). Prefer this in the NodeCraft editor so handle
     * dragging tracks the same cursor ImGui reports while a button is held.
     */
    public static MouseRay rayFromScreen(double mouseLogicalX, double mouseLogicalY) {
        if (MC.world == null || MC.getCameraEntity() == null || MC.getWindow() == null || MC.gameRenderer == null) {
            return null;
        }

        Camera camera = MC.gameRenderer.getCamera();
        if (camera == null) {
            return null;
        }

        int windowW = MC.getWindow().getWidth();
        int windowH = MC.getWindow().getHeight();
        int fbw = MC.getWindow().getFramebufferWidth();
        int fbh = MC.getWindow().getFramebufferHeight();
        if (windowW <= 0 || windowH <= 0 || fbw <= 0 || fbh <= 0) {
            return null;
        }
        double mouseX = mouseLogicalX * fbw / (double) windowW;
        double mouseY = mouseLogicalY * fbh / (double) windowH;

        Vec3d cameraPos = camera.getCameraPos();
        double ndcX = (mouseX / (double) fbw) * 2.0 - 1.0;
        double ndcY = 1.0 - (mouseY / (double) fbh) * 2.0;

        GameRenderer gr = MC.gameRenderer;
        float tickDelta = getRenderTickDeltaSafe();
        float fov = getActualFov(gr, camera, tickDelta);
        Matrix4f projMatrix = new Matrix4f(gr.getBasicProjectionMatrix(fov));
        Matrix4f invProj = new Matrix4f(projMatrix).invert();

        Vector4f nearClip = new Vector4f((float) ndcX, (float) ndcY, -1.0f, 1.0f).mul(invProj);
        Vector4f farClip = new Vector4f((float) ndcX, (float) ndcY, 1.0f, 1.0f).mul(invProj);
        if (nearClip.w != 0.0f) nearClip.div(nearClip.w);
        if (farClip.w != 0.0f) farClip.div(farClip.w);

        Vec3d viewDir = new Vec3d(
            farClip.x - nearClip.x,
            farClip.y - nearClip.y,
            farClip.z - nearClip.z
        ).normalize();

        var q = camera.getRotation();
        float x = (float) viewDir.x;
        float y = (float) viewDir.y;
        float z = (float) viewDir.z;
        float qx = q.x, qy = q.y, qz = q.z, qw = q.w;
        float cx1 = qy * z - qz * y;
        float cy1 = qz * x - qx * z;
        float cz1 = qx * y - qy * x;
        float rx = x + 2.0f * (qy * cz1 - qz * cy1 + qw * cx1);
        float ry = y + 2.0f * (qz * cx1 - qx * cz1 + qw * cy1);
        float rz = z + 2.0f * (qx * cy1 - qy * cx1 + qw * cz1);
        Vec3d rayDir = new Vec3d(rx, ry, rz).normalize();
        return new MouseRay(cameraPos, rayDir);
    }

    /**
     * 使用OpenGL标准unproject方法构建从鼠标位置到世界的射线
     * @return 射线命中的方块结果，或 null
     */
    public static BlockHitResult raycastFromMouse() {
        if (MC.world == null || MC.player == null || MC.getCameraEntity() == null) {
            return null;
        }

        Camera camera = MC.gameRenderer.getCamera();
        if (camera == null) {
            return null;
        }

        // 避免在暂停时重复计算
        if (MC.isPaused() && cachedRay != null) {
            return cachedRay;
        }

        MouseRay mouseRay = rayFromMouse();
        if (mouseRay == null) {
            return null;
        }

        Vec3d cameraPos = mouseRay.origin();
        Vec3d rayDir = mouseRay.direction();
        double mouseX = MC.mouse.getX() * MC.getWindow().getFramebufferWidth() / (double) MC.getWindow().getWidth();
        double mouseY = MC.mouse.getY() * MC.getWindow().getFramebufferHeight() / (double) MC.getWindow().getHeight();

        // 检查缓存 - 如果鼠标和相机位置没有变化则复用上次结果
        if (cachedRay != null
                && lastCameraPos != null
                && cameraPos.distanceTo(lastCameraPos) < CACHE_POSITION_EPSILON
                && Math.abs(mouseX - lastMouseX) < 0.1
                && Math.abs(mouseY - lastMouseY) < 0.1) {
            return cachedRay;
        }

        Vec3d endPos = cameraPos.add(rayDir.multiply(MAX_RAYCAST_DISTANCE));

        BlockHitResult result = MC.world.raycast(new RaycastContext(
                cameraPos,
                endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                MC.player
        ));

        lastCameraPos = cameraPos;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        cachedRay = result;

        return result;
    }

    /**
     * 清除缓存（当窗口关闭或状态改变时调用）
     */
    public static void clearCache() {
        cachedRay = null;
        lastCameraPos = null;
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
    }

    /**
     * 获取当前渲染 tickDelta
     */
    private static float getRenderTickDeltaSafe() {
        try {
            Object rtc = MC.getRenderTickCounter();
            if (rtc == null) return 0.0f;

            try {
                var m = rtc.getClass().getMethod("getTickProgress", boolean.class);
                Object v = m.invoke(rtc, false);
                if (v instanceof Float f) return f;
            } catch (ReflectiveOperationException ignored) {}

            try {
                var m = rtc.getClass().getMethod("getDynamicDeltaTicks");
                Object v = m.invoke(rtc);
                if (v instanceof Float f) return f;
            } catch (ReflectiveOperationException ignored) {}

            try {
                var m = rtc.getClass().getMethod("getFixedDeltaTicks");
                Object v = m.invoke(rtc);
                if (v instanceof Float f) return f;
            } catch (ReflectiveOperationException ignored) {}
        } catch (Throwable ignored) {}
        return 0.0f;
    }

    /**
     * 获取当前帧的真实 FOV（包含动态 FOV、疾跑/速度效果）
     */
    private static float getActualFov(GameRenderer gr, Camera camera, float tickDelta) {
        if (camera == null) {
            return (float) MC.options.getFov().getValue();
        }

        try {
            if (gr instanceof GameRendererAccessor accessor) {
                float fov = accessor.nodecraft$invokeGetFov(camera, tickDelta, true);
                if (fov > 0 && fov < 180) {
                    return fov;
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("通过 Accessor 获取真实FOV时异常: {}", e.getMessage());
        }

        // 兜底：使用静态 FOV
        return (float) MC.options.getFov().getValue();
    }
}
