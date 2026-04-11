package com.nodecraft.nodesystem.nodes.spatial.points;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry;

/**
 * Deprecated manual registration helper kept only for compatibility with
 * older bootstrap paths that may still expect this class to exist.
 *
 * The canonical registry flow uses annotation scanning. This helper must not
 * hand-register nodes that already live in v1 canonical categories.
 *
 * It therefore registers only the remaining legacy nodes that still physically
 * live in the {@code spatial.points} package.
 */
@Deprecated
public final class SpatialPointNodes {

    private SpatialPointNodes() {
    }

    /**
     * Registers only the remaining legacy spatial point nodes when an older
     * manual bootstrap path still invokes this helper.
     */
    public static void registerNodes() {
        NodeRegistry registry = NodeRegistry.getInstance();

        registry.registerNode(new NodeInfo(
            "spatial.points.point_between_two_points",
            "Point Between Two Points",
            "Legacy compatibility node for point interpolation workflows.",
            "spatial.points",
            PointBetweenTwoPointsNode.class
        ));
        registry.registerNode(new NodeInfo(
            "spatial.points.randomize_coordinates",
            "Randomize Coordinates",
            "Legacy compatibility node for randomized block-coordinate offsets.",
            "spatial.points",
            RandomizeCoordinatesNode.class
        ));
    }
}
