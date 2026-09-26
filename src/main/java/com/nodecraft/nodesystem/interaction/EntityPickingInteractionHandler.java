package com.nodecraft.nodesystem.interaction;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.minecraft.client.MinecraftClientController;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

final class EntityPickingInteractionHandler implements NodeEditorInteractionManager.InteractionModeHandler {

    private static final double DEFAULT_ENTITY_RADIUS = 0.15d;

    private final WorldPickingService worldPicking;
    private final Runnable cancelInteraction;
    private final Runnable completeInteraction;

    private NodeEditorInteractionManager.IEntityPickerCallback currentCallback;

    EntityPickingInteractionHandler(
        WorldPickingService worldPicking,
        Runnable cancelInteraction,
        Runnable completeInteraction
    ) {
        this.worldPicking = worldPicking;
        this.cancelInteraction = cancelInteraction;
        this.completeInteraction = completeInteraction;
    }

    @Override
    public void onEnter(String nodeId, NodeEditorInteractionManager.IInteractionCallback callback) {
        if (!(callback instanceof NodeEditorInteractionManager.IEntityPickerCallback)) {
            throw new IllegalArgumentException("实体拾取模式需要IEntityPickerCallback");
        }

        currentCallback = (NodeEditorInteractionManager.IEntityPickerCallback) callback;

        MinecraftClientController.getInstance().showHudMessage(getHintMessage());
        NodeCraft.LOGGER.info("节点 {} 进入实体拾取模式", nodeId);
    }

    @Override
    public void onUpdate(
        Coordinate hoveredBlock,
        BlockHitResult hitResult,
        boolean isLeftMouseClicked,
        boolean isRightMouseClicked
    ) {
        if (isRightMouseClicked) {
            cancelInteraction.run();
            return;
        }

        if (isLeftMouseClicked) {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world == null || client.getCameraEntity() == null) {
                    MinecraftClientController.getInstance().showHudMessage("世界未加载，无法拾取实体");
                    return;
                }

                float mouseX = (float) client.mouse.getX();
                float mouseY = (float) client.mouse.getY();
                WorldPickingService.Ray ray = worldPicking.getCachedOrComputeRay(mouseX, mouseY);
                if (ray == null) {
                    MinecraftClientController.getInstance().showHudMessage("无法计算拾取射线");
                    return;
                }

                float maxDistance = currentCallback.getEntityPickMaxDistance();
                WorldPickingService.EntityHitResult hit = worldPicking.pickEntityWithRay(
                    ray,
                    maxDistance,
                    DEFAULT_ENTITY_RADIUS
                );
                if (hit == null) {
                    MinecraftClientController.getInstance().showHudMessage("光标下没有可拾取的实体");
                    return;
                }

                Entity entity = hit.entity();
                currentCallback.onEntityPicked(
                    entity.getUuidAsString(),
                    Registries.ENTITY_TYPE.getId(entity.getType()).toString(),
                    new Vec3d(entity.getX(), entity.getY(), entity.getZ()),
                    entity
                );
                completeInteraction.run();

                NodeCraft.LOGGER.info("实体拾取完成: {} ({})", entity.getType(), entity.getUuidAsString());
            } catch (Exception e) {
                NodeCraft.LOGGER.error("处理实体拾取时出错", e);
                cancelInteraction.run();
            }
        }
    }

    @Override
    public void onCancel() {
        if (currentCallback != null) {
            currentCallback.onInteractionCancelled();
        }
        MinecraftClientController.getInstance().clearHudMessage();
        NodeCraft.LOGGER.info("实体拾取已取消");
        currentCallback = null;
    }

    @Override
    public void onComplete() {
        MinecraftClientController.getInstance().clearHudMessage();
        currentCallback = null;
    }

    @Override
    public String getDisplayName() {
        return "实体拾取";
    }

    @Override
    public String getHintMessage() {
        return "请左键点击一个实体进行拾取";
    }
}
