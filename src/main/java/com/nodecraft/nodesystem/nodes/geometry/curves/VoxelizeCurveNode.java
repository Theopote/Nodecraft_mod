package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
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
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.voxelize_curve",
    displayName = "Voxelize Curve",
    description = "Converts a curve, polyline, or line directly into voxel block coordinates using cylindrical path segments",
    category = "geometry.curves",
    order = 21
)
public class VoxelizeCurveNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Default Radius", category = "Voxelize", order = 1)
    private double defaultRadius = 1.0d;

    @NodeProperty(displayName = "Default Spacing", category = "Voxelize", order = 2)
    private double defaultSpacing = 0.0d;

    @NodeProperty(displayName = "Fill Tube", category = "Voxelize", order = 3)
    private boolean fillTube = true;

    @NodeProperty(displayName = "Cap Ends", category = "Voxelize", order = 4)
    private boolean capEnds = true;

    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_COUNT_ID = "input_count";

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

        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Curve to voxelize", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline", "Fallback polyline to voxelize", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Fallback line to voxelize", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Tube radius in blocks/meters", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Optional path rebuild spacing before voxelization", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Optional rebuild sample count; overrides spacing", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized curve block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_TREE_ID, "Blocks Tree", "Voxelized blocks grouped as one branch for this curve", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite cylinder geometry used for voxelization", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_SEGMENT_GEOMETRY_TREE_ID, "Segment Geometry Tree", "Cylinder segment geometry keyed by path segment index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Sampled path used for voxelization", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled path points as Vector3d list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the generated curve blocks", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Source path length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when voxelization succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Converts a curve, polyline, or line directly into voxel block coordinates using cylindrical path segments";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolveVertices();
        SampledPath sampled = samplePath(verts);
        double radius = Math.max(0.0d, getInputDouble(INPUT_RADIUS_ID, defaultRadius));
        if (sampled == null || sampled.points().size() < 2 || radius <= EPS) {
            writeInvalid();
            return;
        }

        List<GeometryData> cylinders = buildSegmentGeometry(sampled.points(), sampled.closed(), radius);
        if (cylinders.isEmpty()) {
            writeInvalid();
            return;
        }

        GeometryData geometry = new CompositeGeometryData(cylinders);
        BlockPosList blocks = GeometryVoxelizer.voxelize(geometry, fillTube);
        RegionData region = GeometryVoxelizer.createBoundingRegion(geometry);
        PolylineData polyline = PathUtils.createPolylineOrNull(PathUtils.toVec3dList(
            sampled.closed() ? sampled.points().subList(0, sampled.points().size() - 1) : sampled.points(),
            sampled.closed()
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
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(sampled.points()));
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_LENGTH_ID, sampled.length());
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
        outputValues.put(OUTPUT_VALID_ID, true);
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

    public double getDefaultSpacing() {
        return defaultSpacing;
    }

    public void setDefaultSpacing(double defaultSpacing) {
        double resolved = Math.max(0.0d, defaultSpacing);
        if (Double.compare(this.defaultSpacing, resolved) != 0) {
            this.defaultSpacing = resolved;
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
            "defaultSpacing", defaultSpacing,
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
        if (map.get("defaultSpacing") instanceof Number value) {
            setDefaultSpacing(value.doubleValue());
        }
        if (map.get("fillTube") instanceof Boolean value) {
            setFillTube(value);
        }
        if (map.get("capEnds") instanceof Boolean value) {
            setCapEnds(value);
        }
    }

    private @Nullable List<Vector3d> resolveVertices() {
        return PathUtils.resolveVertices(
            inputValues.get(INPUT_CURVE_ID),
            inputValues.get(INPUT_POLYLINE_ID),
            inputValues.get(INPUT_LINE_ID)
        );
    }

    private @Nullable SampledPath samplePath(@Nullable List<Vector3d> verts) {
        if (verts == null || verts.size() < 2) {
            return null;
        }
        boolean closed = PathUtils.isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            return null;
        }

        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            return null;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS) {
            return null;
        }

        int count = inputValues.get(INPUT_COUNT_ID) instanceof Number number ? number.intValue() : -1;
        double spacing = getInputDouble(INPUT_SPACING_ID, defaultSpacing);
        if (count < 2 && spacing <= EPS) {
            List<Vector3d> copied = new ArrayList<>(verts.size());
            for (Vector3d vert : verts) {
                copied.add(new Vector3d(vert));
            }
            return new SampledPath(List.copyOf(copied), closed, total);
        }

        List<Double> distances = buildSampleDistances(total, count, spacing);
        if (distances.isEmpty()) {
            return null;
        }
        List<Vector3d> samples = new ArrayList<>(distances.size() + (closed ? 1 : 0));
        for (double distance : distances) {
            samples.add(PathUtils.sampleAtDistance(unique, closed, cumulative, distance));
        }
        if (closed && !samples.isEmpty()) {
            samples.add(new Vector3d(samples.getFirst()));
        }
        return new SampledPath(List.copyOf(samples), closed, total);
    }

    private List<Double> buildSampleDistances(double total, int count, double spacing) {
        List<Double> distances = new ArrayList<>();
        if (count >= 2) {
            count = GenerationLimits.clampPositiveCount(count);
            for (int i = 0; i < count; i++) {
                distances.add(total * i / (double) (count - 1));
            }
        } else if (spacing > EPS) {
            for (double distance = 0.0d; distance <= total + EPS; distance += spacing) {
                distances.add(Math.min(distance, total));
            }
            if (!distances.isEmpty() && distances.getLast() < total - EPS) {
                distances.add(total);
            }
        }
        return distances;
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

    private record SampledPath(List<Vector3d> points, boolean closed, double length) {
    }
}
