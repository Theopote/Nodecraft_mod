package com.nodecraft.nodesystem.interaction;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.util.Coordinate;

final class HoveredBlockHighlightService {

    private final String ownerId;
    private Coordinate hoveredBlockCoordinate;
    private String hoverPreviewId;

    HoveredBlockHighlightService(String ownerId) {
        this.ownerId = ownerId;
    }

    void updateHoveredBlockHighlight(Coordinate newHoveredBlock) {
        if (java.util.Objects.equals(hoveredBlockCoordinate, newHoveredBlock)) {
            return;
        }

        NodeCraft.LOGGER.debug("更新悬停方块高亮: {} -> {}", hoveredBlockCoordinate, newHoveredBlock);
        clear();

        if (newHoveredBlock != null) {
            PreviewOptions options = new PreviewOptions()
                .setColor(1.0f, 1.0f, 1.0f)
                .setOpacity(0.8f)
                .wireframeMode()
                .setLineWidth(2.0f);

            hoverPreviewId = PreviewRenderer.getInstance().showPreview(
                ownerId,
                "block_highlight",
                newHoveredBlock,
                options
            );

            if (hoverPreviewId == null) {
                NodeCraft.LOGGER.warn("悬停高亮预览创建失败: PreviewRenderer.showPreview 返回 null");
            }
        }

        hoveredBlockCoordinate = newHoveredBlock;
    }

    void clear() {
        if (hoverPreviewId != null) {
            PreviewRenderer.getInstance().hidePreview(hoverPreviewId);
            hoverPreviewId = null;
        }
        hoveredBlockCoordinate = null;
    }
}
