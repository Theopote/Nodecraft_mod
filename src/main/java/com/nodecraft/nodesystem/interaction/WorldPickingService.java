package com.nodecraft.nodesystem.interaction;

import com.nodecraft.core.NodeCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 世界射线与方块拾取：屏幕坐标 → 世界射线 → {@link BlockHitResult}。
 * <p>
 * 从 {@link NodeEditorInteractionManager} 拆出，供编辑器与世界输入节点复用。
 */
public final class WorldPickingService {

    private static final float MOUSE_MOVEMENT_THRESHOLD = 0.1f;
    private static final float CAMERA_MOVEMENT_THRESHOLD = 0.1f;

    private float cachedMouseX = -1;
    private float cachedMouseY = -1;
    private Ray cachedRay = null;
    private float cachedCameraYaw = Float.NaN;
    private float cachedCameraPitch = Float.NaN;

    private long rayComputeCount = 0;
    private long rayCacheHitCount = 0;

    public WorldPickingService() {
    }

    /**
     * 屏幕鼠标坐标对应的世界射线。
     */
    public Ray getRayFromMouse(float mouseX, float mouseY) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.getCameraEntity() == null || client.getWindow() == null) {
                return null;
            }

            Camera camera = client.gameRenderer.getCamera();
            Vec3d cameraPos = client.getCameraEntity().getCameraPosVec(1.0f);

            int windowWidth = client.getWindow().getWidth();
            int windowHeight = client.getWindow().getHeight();
            int framebufferWidth = client.getWindow().getFramebufferWidth();
            int framebufferHeight = client.getWindow().getFramebufferHeight();

            if (windowWidth <= 0 || windowHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0) {
                return null;
            }

            float mouseFramebufferX;
            float mouseFramebufferY;

            if (mouseX > windowWidth + 1.0f || mouseY > windowHeight + 1.0f) {
                mouseFramebufferX = mouseX;
                mouseFramebufferY = mouseY;
            } else {
                mouseFramebufferX = (float) (mouseX * framebufferWidth / (double) windowWidth);
                mouseFramebufferY = (float) (mouseY * framebufferHeight / (double) windowHeight);
            }

            if (mouseFramebufferX < 0 || mouseFramebufferX > framebufferWidth
                || mouseFramebufferY < 0 || mouseFramebufferY > framebufferHeight) {
                mouseFramebufferX = Math.max(0.0f, Math.min(mouseFramebufferX, framebufferWidth - 1.0f));
                mouseFramebufferY = Math.max(0.0f, Math.min(mouseFramebufferY, framebufferHeight - 1.0f));
            }

            float ndcX = (2.0f * mouseFramebufferX) / framebufferWidth - 1.0f;
            float ndcY = 1.0f - (2.0f * mouseFramebufferY) / framebufferHeight;

            Vec3d rayDirection = calculateRayDirection(ndcX, ndcY, camera);

            if (rayDirection != null) {
                Ray ray = new Ray(cameraPos, rayDirection.normalize());

                if (NodeCraft.LOGGER.isDebugEnabled()) {
                    NodeCraft.LOGGER.debug("射线计算: 鼠标({}, {}) -> NDC({}, {}) -> 射线{}",
                        mouseX, mouseY, ndcX, ndcY, ray);
                }

                return ray;
            }

            NodeCraft.LOGGER.warn("主要射线计算方法失败，使用备用方法");
            return getFallbackRayFromMouse(mouseX, mouseY);

        } catch (Exception e) {
            NodeCraft.LOGGER.error("计算鼠标射线时出错", e);
            return getFallbackRayFromMouse(mouseX, mouseY);
        }
    }

    /**
     * 使用给定射线对方块轮廓做 raycast（最大距离 100）。
     */
    public BlockHitResult pickBlockWithRay(Ray ray) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.getCameraEntity() == null || ray == null) {
                return null;
            }

            double maxDistance = 100.0;
            Vec3d endPos = ray.origin.add(ray.direction.multiply(maxDistance));

            RaycastContext raycastContext = new RaycastContext(
                ray.origin,
                endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.getCameraEntity()
            );

            BlockHitResult hitResult = client.world.raycast(raycastContext);

            if (hitResult.getType() != HitResult.Type.MISS) {
                if (NodeCraft.LOGGER.isDebugEnabled()) {
                    BlockPos pos = hitResult.getBlockPos();
                    NodeCraft.LOGGER.debug("射线拾取成功: 射线{} -> 方块({}, {}, {})",
                        ray, pos.getX(), pos.getY(), pos.getZ());
                }
                return hitResult;
            }

        } catch (Exception e) {
            NodeCraft.LOGGER.error("射线拾取方块时出错", e);
        }

        return null;
    }

    /**
     * 鼠标或相机变化较小时复用上帧射线。
     */
    public Ray getCachedOrComputeRay(float mouseX, float mouseY) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.getCameraEntity() == null) {
                invalidateRayCache();
                return null;
            }

            Camera camera = client.gameRenderer.getCamera();
            float currentYaw = camera.getYaw();
            float currentPitch = camera.getPitch();

            boolean mouseMoved = Math.abs(mouseX - cachedMouseX) > MOUSE_MOVEMENT_THRESHOLD
                || Math.abs(mouseY - cachedMouseY) > MOUSE_MOVEMENT_THRESHOLD;
            boolean cameraMoved = Float.isNaN(cachedCameraYaw) || Float.isNaN(cachedCameraPitch)
                || Math.abs(currentYaw - cachedCameraYaw) > CAMERA_MOVEMENT_THRESHOLD
                || Math.abs(currentPitch - cachedCameraPitch) > CAMERA_MOVEMENT_THRESHOLD;

            if (!mouseMoved && !cameraMoved && cachedRay != null) {
                rayCacheHitCount++;
                if (NodeCraft.LOGGER.isDebugEnabled()) {
                    NodeCraft.LOGGER.debug("使用缓存射线: 鼠标({}, {}) 相机({}, {}) [命中次数: {}]",
                        mouseX, mouseY, currentYaw, currentPitch, rayCacheHitCount);
                }
                return cachedRay;
            }

            Ray newRay = getRayFromMouse(mouseX, mouseY);
            if (newRay != null) {
                rayComputeCount++;
                cachedMouseX = mouseX;
                cachedMouseY = mouseY;
                cachedRay = newRay;
                cachedCameraYaw = currentYaw;
                cachedCameraPitch = currentPitch;

                if (NodeCraft.LOGGER.isDebugEnabled()) {
                    NodeCraft.LOGGER.debug("重新计算射线: 鼠标({}, {}) 相机({}, {}) -> {} [计算次数: {}]",
                        mouseX, mouseY, currentYaw, currentPitch, newRay, rayComputeCount);
                }
            }

            return newRay;

        } catch (Exception e) {
            NodeCraft.LOGGER.error("获取缓存射线时出错", e);
            invalidateRayCache();
            return null;
        }
    }

    public void invalidateRayCache() {
        cachedMouseX = -1;
        cachedMouseY = -1;
        cachedRay = null;
        cachedCameraYaw = Float.NaN;
        cachedCameraPitch = Float.NaN;
    }

    public long getRayComputeCount() {
        return rayComputeCount;
    }

    public long getRayCacheHitCount() {
        return rayCacheHitCount;
    }

    private Ray getFallbackRayFromMouse(float mouseX, float mouseY) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.getCameraEntity() == null) {
                return null;
            }

            Camera camera = client.gameRenderer.getCamera();
            Vec3d cameraPos = camera.getBlockPos().toCenterPos();
            Vec3d direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();

            NodeCraft.LOGGER.warn("使用备用射线计算方法 - 注意：这将使用屏幕中心而非鼠标位置");

            return new Ray(cameraPos, direction);

        } catch (Exception e) {
            NodeCraft.LOGGER.error("备用射线计算方法也失败", e);
            return null;
        }
    }

    private Vec3d calculateRayDirection(float ndcX, float ndcY, Camera camera) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.gameRenderer == null || client.getWindow() == null) {
                NodeCraft.LOGGER.warn("GameRenderer或Window为null，无法计算射线方向");
                return null;
            }

            double fov = client.options.getFov().getValue();

            Matrix4f projectionMatrix = client.gameRenderer.getBasicProjectionMatrix((float) fov);
            if (projectionMatrix == null) {
                NodeCraft.LOGGER.warn("无法获取投影矩阵");
                return getFallbackRayDirection(ndcX, ndcY, camera);
            }

            Matrix4f viewMatrix = createViewMatrix(camera);
            if (viewMatrix == null) {
                NodeCraft.LOGGER.warn("无法创建视图矩阵");
                return getFallbackRayDirection(ndcX, ndcY, camera);
            }

            Matrix4f inverseProjView = new Matrix4f(projectionMatrix);
            inverseProjView.mul(viewMatrix);

            try {
                inverseProjView.invert();
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("无法计算投影视图矩阵的逆矩阵: {}", e.getMessage());
                return getFallbackRayDirection(ndcX, ndcY, camera);
            }

            Vector4f nearPoint = new Vector4f(ndcX, ndcY, -1.0f, 1.0f);
            nearPoint.mul(inverseProjView);
            if (Math.abs(nearPoint.w) < 1e-6f) {
                NodeCraft.LOGGER.warn("近平面点的w分量接近零");
                return getFallbackRayDirection(ndcX, ndcY, camera);
            }
            nearPoint.div(nearPoint.w);

            Vector4f farPoint = new Vector4f(ndcX, ndcY, 1.0f, 1.0f);
            farPoint.mul(inverseProjView);
            if (Math.abs(farPoint.w) < 1e-6f) {
                NodeCraft.LOGGER.warn("远平面点的w分量接近零");
                return getFallbackRayDirection(ndcX, ndcY, camera);
            }
            farPoint.div(farPoint.w);

            Vector3f direction = new Vector3f(
                farPoint.x - nearPoint.x,
                farPoint.y - nearPoint.y,
                farPoint.z - nearPoint.z
            );

            float length = direction.length();
            if (length < 1e-6f) {
                NodeCraft.LOGGER.warn("射线方向向量长度过小，无法归一化");
                return getFallbackRayDirection(ndcX, ndcY, camera);
            }
            direction.normalize();

            Vec3d rayDirection = new Vec3d(direction.x, direction.y, direction.z);

            if (rayDirection.lengthSquared() < 0.1 || rayDirection.lengthSquared() > 2.0) {
                NodeCraft.LOGGER.warn("计算出的射线方向向量长度异常: {}", rayDirection);
                return getFallbackRayDirection(ndcX, ndcY, camera);
            }

            if (NodeCraft.LOGGER.isDebugEnabled()) {
                NodeCraft.LOGGER.debug("精确射线计算成功: NDC({}, {}) -> 方向{}", ndcX, ndcY, rayDirection);
            }

            return rayDirection;

        } catch (Exception e) {
            NodeCraft.LOGGER.error("精确射线计算时出错，使用备用方法", e);
            return getFallbackRayDirection(ndcX, ndcY, camera);
        }
    }

    private Matrix4f createViewMatrix(Camera camera) {
        try {
            Vec3d cameraPos = camera.getBlockPos().toCenterPos();
            float pitch = camera.getPitch();
            float yaw = camera.getYaw();

            Matrix4f viewMatrix = new Matrix4f();
            viewMatrix.rotateX((float) Math.toRadians(pitch));
            viewMatrix.rotateY((float) Math.toRadians(yaw + 180.0f));
            viewMatrix.translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);

            return viewMatrix;

        } catch (Exception e) {
            NodeCraft.LOGGER.error("创建视图矩阵时出错", e);
            return null;
        }
    }

    private Vec3d getFallbackRayDirection(float ndcX, float ndcY, Camera camera) {
        try {
            float pitch = camera.getPitch();
            float yaw = camera.getYaw();

            MinecraftClient client = MinecraftClient.getInstance();
            double fov = client.options.getFov().getValue();

            double fovRadians = Math.toRadians(fov);
            double aspectRatio = (double) client.getWindow().getWidth() / client.getWindow().getHeight();

            double halfFovY = fovRadians / 2.0;
            double halfFovX = Math.atan(Math.tan(halfFovY) * aspectRatio);

            double offsetYaw = ndcX * halfFovX;
            double offsetPitch = ndcY * halfFovY;

            float finalPitch = (float) (Math.toRadians(pitch) - offsetPitch);
            float finalYaw = (float) (Math.toRadians(yaw) + offsetYaw);

            double cosYaw = Math.cos(finalYaw);
            double sinYaw = Math.sin(finalYaw);
            double cosPitch = Math.cos(finalPitch);
            double sinPitch = Math.sin(finalPitch);

            Vec3d direction = new Vec3d(
                -sinYaw * cosPitch,
                -sinPitch,
                cosYaw * cosPitch
            );

            NodeCraft.LOGGER.debug("使用备用射线计算方法");
            return direction.normalize();

        } catch (Exception e) {
            NodeCraft.LOGGER.error("备用射线计算也失败", e);
            return Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
        }
    }

    /**
     * 世界空间射线（原点 + 单位方向）。
     */
    public static final class Ray {
        public final Vec3d origin;
        public final Vec3d direction;

        public Ray(Vec3d origin, Vec3d direction) {
            this.origin = origin;
            this.direction = direction;
        }

        @Override
        public String toString() {
            return String.format("Ray{origin=%s, direction=%s}", origin, direction);
        }
    }
}
