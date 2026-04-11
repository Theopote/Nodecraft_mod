package com.nodecraft.nodesystem.nodes.spatial.points;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.nodes.reference.planes.DistancePointToPlaneNode;
import com.nodecraft.nodesystem.nodes.reference.points.BlockToPointNode;
import com.nodecraft.nodesystem.nodes.reference.points.PointAlongVectorNode;
import com.nodecraft.nodesystem.nodes.world.query.FilterGridPointsNode;
import com.nodecraft.nodesystem.nodes.world.query.IsGridPointNode;
import com.nodecraft.nodesystem.nodes.world.selection.PointToBlockIfGridNode;
import com.nodecraft.nodesystem.nodes.world.selection.SnapPointListToBlocksNode;
import com.nodecraft.nodesystem.nodes.world.selection.SnapPointToBlockNode;

/**
 * Deprecated manual registration helper kept only for compatibility with
 * older bootstrap paths. The main registry flow uses annotation scanning.
 */
@Deprecated
public final class SpatialPointNodes {

    private SpatialPointNodes() {
    }

    /**
     * Registers all spatial point related nodes when an older manual
     * registration path still invokes this helper.
     */
    public static void registerNodes() {
        NodeRegistry registry = NodeRegistry.getInstance();

        registry.registerNode(new NodeInfo("reference.points.point_from_block", "Block To Point", "Convert a block coordinate into a geometric point for geometry workflows.", "reference.points", BlockToPointNode.class));
        registry.registerNode(new NodeInfo("spatial.points.project_point_to_plane", "Project Point To Plane", "Project a geometric point onto a plane.", "spatial.points", ProjectPointToPlaneNode.class));
        registry.registerNode(new NodeInfo("reference.planes.distance_point_to_plane", "Distance Point To Plane", "Measure the absolute and signed distance from a point to a plane.", "reference.planes", DistancePointToPlaneNode.class));
        registry.registerNode(new NodeInfo("reference.points.point_along_vector", "Point Along Vector", "Create a new point by moving along a direction vector.", "reference.points", PointAlongVectorNode.class));
        registry.registerNode(new NodeInfo("spatial.points.point_between_two_points", "Point Between Two Points", "Interpolate a point between A and B using a parameter t.", "spatial.points", PointBetweenTwoPointsNode.class));
        registry.registerNode(new NodeInfo("spatial.points.points_to_path", "Points To Path", "Build a line or polyline from an ordered point list.", "spatial.points", PointsToPathNode.class));
        registry.registerNode(new NodeInfo("spatial.points.path_to_points", "Path To Points", "Extract an ordered point list from a line, polyline, or curve.", "spatial.points", PathToPointsNode.class));
        registry.registerNode(new NodeInfo("world.selection.snap_point_to_block", "Snap Point To Block", "Explicitly snap a geometric point onto the block grid.", "world.selection", SnapPointToBlockNode.class));
        registry.registerNode(new NodeInfo("world.query.is_grid_point", "Is Grid Point", "Check whether a point is already on the block grid.", "world.query", IsGridPointNode.class));
        registry.registerNode(new NodeInfo("world.selection.point_to_block_if_grid", "Point To Block If Grid", "Strictly convert only grid-aligned points into block coordinates.", "world.selection", PointToBlockIfGridNode.class));
        registry.registerNode(new NodeInfo("world.query.filter_grid_points", "Filter Grid Points", "Split a point list into grid-aligned and off-grid subsets.", "world.query", FilterGridPointsNode.class));
        registry.registerNode(new NodeInfo("world.selection.snap_points_to_blocks", "Snap Point List To Blocks", "Snap a point list onto the block grid using a chosen mode.", "world.selection", SnapPointListToBlocksNode.class));
        registry.registerNode(new NodeInfo("spatial.points.offset_coordinates", "Offset Coordinates", "Translate a block coordinate list by an offset vector.", "spatial.points", OffsetCoordinatesNode.class));
        registry.registerNode(new NodeInfo("spatial.points.rotate_coordinates", "Rotate Coordinates", "Rotate a block coordinate list around a center and axis.", "spatial.points", RotateCoordinatesNode.class));
        registry.registerNode(new NodeInfo("spatial.points.scale_coordinates", "Scale Coordinates", "Scale a block coordinate list relative to a center point.", "spatial.points", ScaleCoordinatesNode.class));
        registry.registerNode(new NodeInfo("spatial.points.mirror_coordinates", "Mirror Coordinates", "Mirror a block coordinate list across a plane.", "spatial.points", MirrorCoordinatesNode.class));
        registry.registerNode(new NodeInfo("spatial.points.randomize_coordinates", "Randomize Coordinates", "Apply random offsets to a block coordinate list.", "spatial.points", RandomizeCoordinatesNode.class));
    }
}
