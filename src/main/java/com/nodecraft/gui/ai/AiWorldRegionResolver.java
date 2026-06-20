package com.nodecraft.gui.ai;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class AiWorldRegionResolver {

    private static final String SELECTED_REGION_TYPE = "world.selection.selected_region";

    private AiWorldRegionResolver() {
    }

    static AiWorldContextSnapshot.SelectedRegionContext resolve(NodeGraph graph, INode selectedNode) {
        AiWorldContextSnapshot.SelectedRegionContext selected = toRegion(selectedNode);
        if (selected != null) {
            return selected;
        }
        if (graph == null) {
            return AiWorldContextSnapshot.SelectedRegionContext.none();
        }

        List<AiWorldContextSnapshot.SelectedRegionContext> regions = new ArrayList<>();
        for (INode node : graph.getNodes()) {
            AiWorldContextSnapshot.SelectedRegionContext region = toRegion(node);
            if (region != null) {
                regions.add(region);
            }
        }
        if (regions.isEmpty()) {
            return AiWorldContextSnapshot.SelectedRegionContext.none();
        }
        if (regions.size() > 1) {
            return AiWorldContextSnapshot.SelectedRegionContext.ambiguous();
        }
        return regions.getFirst();
    }

    private static AiWorldContextSnapshot.SelectedRegionContext toRegion(INode node) {
        if (node == null || !SELECTED_REGION_TYPE.equals(node.getTypeId()) || !(node instanceof BaseNode baseNode)) {
            return null;
        }
        if (!(baseNode.getNodeState() instanceof Map<?, ?> state)) {
            return null;
        }
        int[] pos1 = readPosition(state.get("pos1"));
        int[] pos2 = readPosition(state.get("pos2"));
        if (pos1 == null || pos2 == null) {
            return null;
        }

        int minX = Math.min(pos1[0], pos2[0]);
        int minY = Math.min(pos1[1], pos2[1]);
        int minZ = Math.min(pos1[2], pos2[2]);
        int maxX = Math.max(pos1[0], pos2[0]);
        int maxY = Math.max(pos1[1], pos2[1]);
        int maxZ = Math.max(pos1[2], pos2[2]);
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        return new AiWorldContextSnapshot.SelectedRegionContext(
                "available",
                node.getId() == null ? null : node.getId().toString(),
                new AiWorldContextSnapshot.BlockCoordinate(minX, minY, minZ),
                new AiWorldContextSnapshot.BlockCoordinate(maxX, maxY, maxZ),
                new AiWorldContextSnapshot.BlockCoordinate(sizeX, sizeY, sizeZ),
                new AiWorldContextSnapshot.Vec3(
                        (minX + maxX) / 2.0d,
                        (minY + maxY) / 2.0d,
                        (minZ + maxZ) / 2.0d
                ),
                (long) sizeX * sizeY * sizeZ
        );
    }

    private static int[] readPosition(Object value) {
        if (!(value instanceof Map<?, ?> position)
                || !(position.get("x") instanceof Number x)
                || !(position.get("y") instanceof Number y)
                || !(position.get("z") instanceof Number z)) {
            return null;
        }
        return new int[]{Math.round(x.floatValue()), Math.round(y.floatValue()), Math.round(z.floatValue())};
    }
}
