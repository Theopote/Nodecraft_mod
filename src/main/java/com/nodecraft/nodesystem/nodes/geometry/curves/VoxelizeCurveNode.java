package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.voxelize_curve",
    displayName = "Voxelize Path",
    description = "Converts a path directly into voxel block coordinates using cylindrical path segments.",
    category = "geometry.curves",
    order = 21
)
public class VoxelizeCurveNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Default Radius", category = "Voxelize", order = 1)
    private double defaultRadius = 1.0d;

    @NodeProperty(displayName = "Fill Tube", category = "Voxelize", order = 2)
    private boolean fillTube = true;

    @NodeProperty(displayName = "Cap Ends", category = "Voxelize", order = 3)
    private boolean capEnds = true;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_BLOCKS_TREE_ID = "output_blocks_tree";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_SEGMENT_GEOMETRY_TREE_ID = "output_segment_geometry_tree";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VoxelizeCurveNode() {
        super(UUID.randomUUID(), "geometry.curves.voxelize_curve");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to voxelize (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Tube radius in blocks/meters", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized curve block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_TREE_ID, "Blocks Tree", "Voxelized blocks grouped as one branch for this curve", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite cylinder geometry used for voxelization", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_SEGMENT_GEOMETRY_TREE_ID, "Segment Geometry Tree", "Cylinder segment geometry keyed by path segment index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Path polyline used for voxelization", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Path points as point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the generated curve blocks", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Source path length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when voxelization succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Converts a path directly into voxel block coordinates using cylindrical path segments.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        double radius = Math.max(0.0d, getInputDouble(INPUT_RADIUS_ID, defaultRadius));
        if (verts == null || verts.size() < 2 || radius <= EPS) {
            writeInvalid();
            return;
        }

        boolean closed = PathUtils.isClosed(verts);
        double length = computeLength(verts, closed);

        List<GeometryData> cylinders = buildSegmentGeometry(verts, closed, radius);
        if (cylinders.isEmpty()) {
            writeInvalid();
            return;
        }

        GeometryData geometry = new CompositeGeometryData(cylinders);
        BlockPosList blocks = GeometryVoxelizer.voxelize(geometry, fillTube);
        RegionData region = GeometryVoxelizer.createBoundingRegion(geometry);
        PolylineData polyline = PathUtils.createPolylineOrNull(PathUtils.toVec3dList(
            closed ? verts.subList(0, verts.size() - 1) : verts,
            closed
        ));
        if (polyline == null || blocks.isEmpty()) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_BLOCKS_TREE_ID, buildBlockTree(blocks));
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_SEGMENT_GEOMETRY_TREE_ID, buildSegmentTree(cylinders));
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(verts));
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_LENGTH_ID, length);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static double computeLength(List<Vector3d> verts, boolean closed) {
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null || cumulative.length == 0) {
            return 0.0d;
        }
        return cumulative[cumulative.length - 1];
    }

    public double getDefaultRadius() {
        return defaultRadius;
    }

    public void setDefaultRadius(double defaultRadius) {
        double resolved = Math.max(0.0d, defaultRadius);
        if (Double.compare(this.defaultRadius, resolved) != 0) {
            this.defaultRadius = resolved;
            markDirty();
        }
    }

    public boolean isFillTube() {
        return fillTube;
    }

    public void setFillTube(boolean fillTube) {
        if (this.fillTube != fillTube) {
            this.fillTube = fillTube;
            markDirty();
        }
    }

    public boolean isCapEnds() {
        return capEnds;
    }

    public void setCapEnds(boolean capEnds) {
        if (this.capEnds != capEnds) {
            this.capEnds = capEnds;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "defaultRadius", defaultRadius,
            "fillTube", fillTube,
            "capEnds", capEnds
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultRadius") instanceof Number value) {
            setDefaultRadius(value.doubleValue());
        }
        if (map.get("fillTube") instanceof Boolean value) {
            setFillTube(value);
        }
        if (map.get("capEnds") instanceof Boolean value) {
            setCapEnds(value);
        }
    }

    private List<GeometryData> buildSegmentGeometry(List<Vector3d> sampledPoints, boolean closed, double radius) {
        List<Vector3d> points = closed && sampledPoints.size() > 2
            ? sampledPoints.subList(0, sampledPoints.size() - 1)
            : sampledPoints;
        int segmentCount = closed ? points.size() : points.size() - 1;
        List<GeometryData> cylinders = new ArrayList<>(segmentCount + (capEnds && !closed ? 2 : 0));
        for (int i = 0; i < segmentCount; i++) {
            Vector3d start = points.get(i);
            Vector3d end = points.get((i + 1) % points.size());
            if (start.distanceSquared(end) > EPS * EPS) {
                cylinders.add(new CylinderGeometryData(start, end, radius));
            }
        }
        if (capEnds && !closed && points.size() >= 2 && radius > EPS) {
            cylinders.add(new CylinderGeometryData(points.getFirst(), points.getFirst(), radius));
            cylinders.add(new CylinderGeometryData(points.getLast(), points.getLast(), radius));
        }
        return cylinders;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_BLOCKS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_SEGMENT_GEOMETRY_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private DataTreeData buildBlockTree(BlockPosList blocks) {
        return new DataTreeData(List.of(new DataTreeData.Branch(List.of(0), new ArrayList<>(blocks.getPositions()))));
    }

    private DataTreeData buildSegmentTree(List<GeometryData> segments) {
        List<DataTreeData.Branch> branches = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            branches.add(new DataTreeData.Branch(List.of(i), List.of(segments.get(i))));
        }
        return new DataTreeData(branches);
    }
}
