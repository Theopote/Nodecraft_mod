package com.nodecraft.gui.ai;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.graph.NodeGraph;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

final class AiWorldRegionResolver {

    private static final String SELECTED_REGION_TYPE = "world.selection.selected_region";
    private static final String OUTPUT_REGION_ID = "output_region";

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
        if (node == null || !SELECTED_REGION_TYPE.equals(node.getTypeId())) {
            return null;
        }
        // Selection corners are session/runtime outputs, not persisted node state.
        if (!(node.getOutput(OUTPUT_REGION_ID) instanceof RegionData region) || !region.isComplete()) {
            return null;
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return null;
        }

        int minX = min.getX();
        int minY = min.getY();
        int minZ = min.getZ();
        int maxX = max.getX();
        int maxY = max.getY();
        int maxZ = max.getZ();
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
}
