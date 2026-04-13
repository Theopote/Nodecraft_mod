package com.nodecraft.nodesystem.interaction;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.util.Coordinate;

final class HoveredBlockHighlightService {

    private Coordinate hoveredBlockCoordinate;

    HoveredBlockHighlightService(String ownerId) {
    }

    void updateHoveredBlockHighlight(Coordinate newHoveredBlock) {
        if (java.util.Objects.equals(hoveredBlockCoordinate, newHoveredBlock)) {
            return;
        }

        NodeCraft.LOGGER.debug("更新悬停方块高亮: {} -> {}", hoveredBlockCoordinate, newHoveredBlock);
        clear();
        hoveredBlockCoordinate = newHoveredBlock;
    }

    void clear() {
        hoveredBlockCoordinate = null;
    }
}
