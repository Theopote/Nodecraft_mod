package com.nodecraft.nodesystem.interaction;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.minecraft.client.MinecraftClientController;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

final class EntityPickingInteractionHandler implements NodeEditorInteractionManager.InteractionModeHandler {

    private final Runnable cancelInteraction;
    private final Runnable completeInteraction;

    private NodeEditorInteractionManager.IEntityPickerCallback currentCallback;

    EntityPickingInteractionHandler(Runnable cancelInteraction, Runnable completeInteraction) {
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
                // Stub picker until full entity raycast is wired: use hovered block cell center as continuous position.
                Coordinate entityPos = hoveredBlock != null ? hoveredBlock : new Coordinate(0, 0, 0);
                String entityId = "example_entity_" + System.currentTimeMillis();
                String entityType = "minecraft:pig";
                Vec3d exact = new Vec3d(entityPos.x() + 0.5d, entityPos.y() + 0.5d, entityPos.z() + 0.5d);

                currentCallback.onEntityPicked(entityId, entityType, exact, null);
                completeInteraction.run();

                NodeCraft.LOGGER.info("实体拾取完成: {} ({})", entityType, entityId);
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
