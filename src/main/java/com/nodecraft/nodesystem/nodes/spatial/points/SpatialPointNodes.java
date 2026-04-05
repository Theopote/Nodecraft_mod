package com.nodecraft.nodesystem.nodes.spatial.points;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry;

/**
 * Registers spatial point related nodes.
 */
public class SpatialPointNodes {

    /**
     * Registers all spatial point related nodes.
     */
    public static void registerNodes() {
        NodeRegistry registry = NodeRegistry.getInstance();

        registry.registerNode(new NodeInfo("spatial.points.block_to_point", "Block To Point", "", "spatial.points", BlockToPointNode.class));
        registry.registerNode(new NodeInfo("spatial.points.snap_point_to_block", "Snap Point To Block", "", "spatial.points", SnapPointToBlockNode.class));
        registry.registerNode(new NodeInfo("spatial.points.is_grid_point", "Is Grid Point", "", "spatial.points", IsGridPointNode.class));
        registry.registerNode(new NodeInfo("spatial.points.filter_grid_points", "Filter Grid Points", "", "spatial.points", FilterGridPointsNode.class));
        registry.registerNode(new NodeInfo("spatial.points.snap_point_list_to_blocks", "Snap Point List To Blocks", "", "spatial.points", SnapPointListToBlocksNode.class));
        registry.registerNode(new NodeInfo("spatial.points.offsetcoordinates", "Offset Coordinates", "", "spatial.points", OffsetCoordinatesNode.class));
        registry.registerNode(new NodeInfo("spatial.points.rotatecoordinates", "Rotate Coordinates", "", "spatial.points", RotateCoordinatesNode.class));
        registry.registerNode(new NodeInfo("spatial.points.scalecoordinates", "Scale Coordinates", "", "spatial.points", ScaleCoordinatesNode.class));
        registry.registerNode(new NodeInfo("spatial.points.mirrorcoordinates", "Mirror Coordinates", "", "spatial.points", MirrorCoordinatesNode.class));
        registry.registerNode(new NodeInfo("spatial.points.randomizecoordinates", "Randomize Coordinates", "", "spatial.points", RandomizeCoordinatesNode.class));
    }
}
