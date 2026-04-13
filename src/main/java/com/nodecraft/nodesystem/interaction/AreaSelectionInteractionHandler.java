package com.nodecraft.nodesystem.interaction;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.minecraft.client.MinecraftClientController;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

final class AreaSelectionInteractionHandler implements NodeEditorInteractionManager.InteractionModeHandler {

    private final AreaPreviewStyleSettings areaPreviewStyle;
    private final String ownerId;
    private final Runnable cancelInteraction;
    private final Runnable completeInteraction;

    private NodeEditorInteractionManager.IAreaSelectionCallback currentCallback;
    private Coordinate firstPoint;
    private Coordinate lastHoverPoint;
    private String firstPointPreviewId;
    private String areaPreviewId;

    AreaSelectionInteractionHandler(
        AreaPreviewStyleSettings areaPreviewStyle,
        String ownerId,
        Runnable cancelInteraction,
        Runnable completeInteraction
    ) {
        this.areaPreviewStyle = areaPreviewStyle;
        this.ownerId = ownerId;
        this.cancelInteraction = cancelInteraction;
        this.completeInteraction = completeInteraction;
    }

    @Override
    public void onEnter(String nodeId, NodeEditorInteractionManager.IInteractionCallback callback) {
        if (!(callback instanceof NodeEditorInteractionManager.IAreaSelectionCallback)) {
            throw new IllegalArgumentException("区域选择模式需要IAreaSelectionCallback");
        }

        currentCallback = (NodeEditorInteractionManager.IAreaSelectionCallback) callback;
        firstPoint = null;
        lastHoverPoint = null;

        MinecraftClientController.getInstance().showHudMessage(getHintMessage());
        NodeCraft.LOGGER.info("节点 {} 进入区域选择模式", nodeId);
    }

    @Override
    public void onUpdate(
        Coordinate hoveredBlock,
        BlockHitResult hitResult,
        boolean isLeftMouseClicked,
        boolean isRightMouseClicked
    ) {
        if (hoveredBlock != null) {
            lastHoverPoint = hoveredBlock;
        }

        if (isLeftMouseClicked && hoveredBlock != null) {
            if (firstPoint == null) {
                firstPoint = hoveredBlock;
                currentCallback.onFirstPointSelected(firstPoint);
                showFirstPointPreview(firstPoint);

                MinecraftClientController.getInstance().showHudMessage("请选择第二个点完成区域选择");
                NodeCraft.LOGGER.info("区域选择第一个点: {}", firstPoint);
            } else {
                Coordinate completedStart = firstPoint;
                Coordinate completedEnd = hoveredBlock;

                currentCallback.onAreaSelected(completedStart, completedEnd);
                completeInteraction.run();

                NodeCraft.LOGGER.info("区域选择完成: {} 到 {}", completedStart, completedEnd);
            }
        } else if (isRightMouseClicked) {
            if (firstPoint != null) {
                clearPreviews();
                firstPoint = null;
                MinecraftClientController.getInstance().showHudMessage(getHintMessage());
                NodeCraft.LOGGER.info("重置区域选择");
            } else {
                cancelInteraction.run();
            }
        }

        if (firstPoint != null) {
            Coordinate previewEnd = hoveredBlock != null ? hoveredBlock : lastHoverPoint;
            if (previewEnd != null) {
                updateAreaPreview(firstPoint, previewEnd);
            }
        } else {
            clearAreaPreview();
        }
    }

    @Override
    public void onCancel() {
        clearPreviews();
        if (currentCallback != null) {
            currentCallback.onInteractionCancelled();
        }
        MinecraftClientController.getInstance().clearHudMessage();
        NodeCraft.LOGGER.info("区域选择已取消");
        resetState();
    }

    @Override
    public void onComplete() {
        clearPreviews();
        MinecraftClientController.getInstance().clearHudMessage();
        resetState();
    }

    @Override
    public String getDisplayName() {
        return "区域选择";
    }

    @Override
    public String getHintMessage() {
        return firstPoint == null ? "请左键点击选择第一个点" : "请左键点击选择第二个点，右键重置";
    }

    private void showFirstPointPreview(Coordinate point) {
        clearFirstPointPreview();

        PreviewOptions options = new PreviewOptions()
            .setColor(0.0f, 1.0f, 0.0f)
            .setTintColor(0.2f, 1.0f, 0.2f)
            .setOpacity(0.8f)
            .setShowFill(true)
            .setShowOutline(true)
            .setLineWidth(3.0f);

        firstPointPreviewId = PreviewManager.highlightBlock(ownerId, point, options);
    }

    private void updateAreaPreview(Coordinate start, Coordinate end) {
        int minX = Math.min(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int minZ = Math.min(start.getZ(), end.getZ());
        int maxX = Math.max(start.getX(), end.getX());
        int maxY = Math.max(start.getY(), end.getY());
        int maxZ = Math.max(start.getZ(), end.getZ());

        PreviewOptions options = areaPreviewStyle.createRegionBoxPreviewOptions();
        Vec3d min = new Vec3d(minX, minY, minZ);
        Vec3d max = new Vec3d(maxX + 1.0d, maxY + 1.0d, maxZ + 1.0d);
        Object[] regionData = new Object[] { min, max };

        if (areaPreviewId == null) {
            areaPreviewId = PreviewManager.showRegionBox(ownerId, min, max, options);
        } else {
            PreviewManager.updatePreview(areaPreviewId, regionData);
            PreviewManager.updatePreviewOptions(areaPreviewId, options);
        }
    }

    private void clearFirstPointPreview() {
        if (firstPointPreviewId != null) {
            PreviewRenderer.getInstance().hidePreview(firstPointPreviewId);
            firstPointPreviewId = null;
        }
    }

    private void clearAreaPreview() {
        if (areaPreviewId != null) {
            PreviewRenderer.getInstance().hidePreview(areaPreviewId);
            areaPreviewId = null;
        }
    }

    private void clearPreviews() {
        clearFirstPointPreview();
        clearAreaPreview();
    }

    private void resetState() {
        currentCallback = null;
        firstPoint = null;
        lastHoverPoint = null;
    }
}
