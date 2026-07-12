package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.sweep_two_rails",
    displayName = "Sweep 2 Rails",
    description = "Sweeps a profile between two guide rails with optional scale and rotation controls",
    category = "geometry.solids",
    order = 7
)
public class SweepTwoRailsNode extends BaseNode {

    @NodeProperty(displayName = "Section Count", category = "Sweep", order = 1)
    private int sectionCount = 24;

    @NodeProperty(displayName = "Close Profile", category = "Sweep", order = 2)
    private boolean closeProfile = true;

    @NodeProperty(displayName = "Flip Profile", category = "Sweep", order = 3)
    private boolean flipProfile = false;

    @NodeProperty(displayName = "Orient To Rails", category = "Sweep", order = 4)
    private boolean orientToRails = true;

    @NodeProperty(displayName = "Start Scale", category = "Scale", order = 10)
    private double startScale = 1.0d;

    @NodeProperty(displayName = "End Scale", category = "Scale", order = 11)
    private double endScale = 1.0d;

    @NodeProperty(displayName = "Start Rotation Degrees", category = "Rotation", order = 20)
    private double startRotationDegrees = 0.0d;

    @NodeProperty(displayName = "End Rotation Degrees", category = "Rotation", order = 21)
    private double endRotationDegrees = 0.0d;

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_PROFILE_POINTS_ID = "input_profile_points";
    private static final String INPUT_RAIL_A_LINE_ID = "input_rail_a_line";
    private static final String INPUT_RAIL_A_POLYLINE_ID = "input_rail_a_polyline";
    private static final String INPUT_RAIL_A_CURVE_ID = "input_rail_a_curve";
    private static final String INPUT_RAIL_A_POINTS_ID = "input_rail_a_points";
    private static final String INPUT_RAIL_B_LINE_ID = "input_rail_b_line";
    private static final String INPUT_RAIL_B_POLYLINE_ID = "input_rail_b_polyline";
    private static final String INPUT_RAIL_B_CURVE_ID = "input_rail_b_curve";
    private static final String INPUT_RAIL_B_POINTS_ID = "input_rail_b_points";
    private static final String INPUT_SCALE_VALUES_ID = "input_scale_values";
    private static final String INPUT_ROTATION_VALUES_ID = "input_rotation_values";

    private static final String OUTPUT_RAIL_A_POINTS_ID = "output_rail_a_points";
    private static final String OUTPUT_RAIL_B_POINTS_ID = "output_rail_b_points";
    private static final String OUTPUT_SECTION_PATHS_ID = "output_section_paths";
    private static final String OUTPUT_SECTION_PATHS_TREE_ID = "output_section_paths_tree";
    private static final String OUTPUT_ALL_POINTS_ID = "output_all_points";
    private static final String OUTPUT_SECTION_POINTS_TREE_ID = "output_section_points_tree";
    private static final String OUTPUT_RAIL_SEGMENTS_ID = "output_rail_segments";
    private static final String OUTPUT_RAIL_SEGMENTS_TREE_ID = "output_rail_segments_tree";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SweepTwoRailsNode() {
        super(UUID.randomUUID(), "geometry.solids.sweep_two_rails");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Optional polygon profile to sweep", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_PROFILE_POINTS_ID, "Profile Points", "Optional ordered point profile fallback", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_RAIL_A_LINE_ID, "Rail A Line", "First guide rail as a line", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_RAIL_A_POLYLINE_ID, "Rail A Polyline", "First guide rail as a polyline", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_RAIL_A_CURVE_ID, "Rail A Curve", "First guide rail as a curve", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_RAIL_A_POINTS_ID, "Rail A Points", "First guide rail as ordered points", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_RAIL_B_LINE_ID, "Rail B Line", "Second guide rail as a line", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_RAIL_B_POLYLINE_ID, "Rail B Polyline", "Second guide rail as a polyline", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_RAIL_B_CURVE_ID, "Rail B Curve", "Second guide rail as a curve", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_RAIL_B_POINTS_ID, "Rail B Points", "Second guide rail as ordered points", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_SCALE_VALUES_ID, "Scale Values", "Optional scale list sampled along the rails", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_ROTATION_VALUES_ID, "Rotation Values", "Optional rotation degrees list sampled along the rails", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_RAIL_A_POINTS_ID, "Rail A Points", "Resampled first guide rail points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_B_POINTS_ID, "Rail B Points", "Resampled second guide rail points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "List of swept section polylines", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_TREE_ID, "Section Paths Tree", "Section paths keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_ALL_POINTS_ID, "All Points", "Flattened list of all swept section points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_POINTS_TREE_ID, "Section Points Tree", "Section points keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_ID, "Rail Segments", "Line segments connecting corresponding section points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_TREE_ID, "Rail Segments Tree", "Rail segments grouped by source section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Reusable strip surface made of swept sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of swept sections between the rails", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a profile and both rails were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Sweeps a profile between two guide rails with optional scale and rotation controls";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> profilePoints = resolveProfilePoints();
        List<Vector3d> rawRailA = resolveRailPoints(
            inputValues.get(INPUT_RAIL_A_LINE_ID),
            inputValues.get(INPUT_RAIL_A_POLYLINE_ID),
            inputValues.get(INPUT_RAIL_A_CURVE_ID),
            inputValues.get(INPUT_RAIL_A_POINTS_ID)
        );
        List<Vector3d> rawRailB = resolveRailPoints(
            inputValues.get(INPUT_RAIL_B_LINE_ID),
            inputValues.get(INPUT_RAIL_B_POLYLINE_ID),
            inputValues.get(INPUT_RAIL_B_CURVE_ID),
            inputValues.get(INPUT_RAIL_B_POINTS_ID)
        );

        if (profilePoints.size() < 2 || rawRailA.size() < 2 || rawRailB.size() < 2) {
            writeEmptyOutputs();
            return;
        }
        if (flipProfile) {
            profilePoints = new ArrayList<>(profilePoints);
            Collections.reverse(profilePoints);
        }

        int resolvedSectionCount = GenerationLimits.clampSectionCountForProfile(2, sectionCount, profilePoints.size());
        List<Vector3d> railA = resamplePath(rawRailA, resolvedSectionCount);
        List<Vector3d> railB = resamplePath(rawRailB, resolvedSectionCount);
        List<Vector3d> centers = computeCenters(railA, railB);
        Vector3d profileOrigin = SolidNodeUtils.computeCenter(profilePoints);
        double profileWidth = computeProfileWidth(profilePoints);
        List<Double> scaleValues = resolveNumberList(inputValues.get(INPUT_SCALE_VALUES_ID));
        List<Double> rotationValues = resolveNumberList(inputValues.get(INPUT_ROTATION_VALUES_ID));

        List<List<Vector3d>> sections = new ArrayList<>(resolvedSectionCount);
        List<Object> sectionPaths = new ArrayList<>(resolvedSectionCount);
        List<Vector3d> allPoints = new ArrayList<>(profilePoints.size() * resolvedSectionCount);

        for (int i = 0; i < resolvedSectionCount; i++) {
            Vector3d railPointA = railA.get(i);
            Vector3d railPointB = railB.get(i);
            Vector3d center = centers.get(i);
            double t = resolvedSectionCount <= 1 ? 0.0d : (double) i / (double) (resolvedSectionCount - 1);
            double scale = resolveScalarAt(scaleValues, t, startScale, endScale);
            double rotationRadians = Math.toRadians(resolveScalarAt(rotationValues, t, startRotationDegrees, endRotationDegrees));
            double railWidth = railPointA.distance(railPointB);
            SolidNodeUtils.Frame frame = orientToRails
                ? buildRailFrame(center, railPointA, railPointB, centers, i)
                : SolidNodeUtils.Frame.identity(center);

            List<Vector3d> section = new ArrayList<>(profilePoints.size());
            for (Vector3d profilePoint : profilePoints) {
                Vector3d local = new Vector3d(profilePoint).sub(profileOrigin);
                local = transformLocalProfilePoint(local, scale, rotationRadians, railWidth, profileWidth);
                Vector3d worldPoint = frame.transform(local);
                section.add(worldPoint);
                allPoints.add(worldPoint);
            }
            sections.add(section);
            sectionPaths.add(SolidNodeUtils.createPolyline(section, closeProfile));
        }

        List<List<LineData>> railSegmentRows = buildRailSegmentRows(sections);
        List<LineData> railSegments = flattenRailSegments(railSegmentRows);
        List<Boolean> sectionClosedFlags = new ArrayList<>(sections.size());
        for (int i = 0; i < sections.size(); i++) {
            sectionClosedFlags.add(closeProfile);
        }
        SurfaceStripData surfaceStrip = new SurfaceStripData(sections, sectionClosedFlags);

        outputValues.put(OUTPUT_RAIL_A_POINTS_ID, List.copyOf(railA));
        outputValues.put(OUTPUT_RAIL_B_POINTS_ID, List.copyOf(railB));
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, SolidDataTreeUtils.indexedValueTree(sectionPaths));
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.copyOf(allPoints));
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(sections));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.copyOf(railSegments));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(railSegmentRows));
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, sections.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("sectionCount", sectionCount);
        state.put("closeProfile", closeProfile);
        state.put("flipProfile", flipProfile);
        state.put("orientToRails", orientToRails);
        state.put("startScale", startScale);
        state.put("endScale", endScale);
        state.put("startRotationDegrees", startRotationDegrees);
        state.put("endRotationDegrees", endRotationDegrees);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("sectionCount") instanceof Number value) sectionCount = GenerationLimits.clampSegments(2, value.intValue());
        if (map.get("closeProfile") instanceof Boolean value) closeProfile = value;
        if (map.get("flipProfile") instanceof Boolean value) flipProfile = value;
        if (map.get("orientToRails") instanceof Boolean value) orientToRails = value;
        if (map.get("startScale") instanceof Number value) startScale = value.doubleValue();
        if (map.get("endScale") instanceof Number value) endScale = value.doubleValue();
        if (map.get("startRotationDegrees") instanceof Number value) startRotationDegrees = value.doubleValue();
        if (map.get("endRotationDegrees") instanceof Number value) endRotationDegrees = value.doubleValue();
        markDirty();
    }

    public int getSectionCount() {
        return sectionCount;
    }

    public void setSectionCount(int sectionCount) {
        this.sectionCount = GenerationLimits.clampSegments(2, sectionCount);
        markDirty();
    }

    public boolean isCloseProfile() {
        return closeProfile;
    }

    public void setCloseProfile(boolean closeProfile) {
        this.closeProfile = closeProfile;
        markDirty();
    }

    public boolean isFlipProfile() {
        return flipProfile;
    }

    public void setFlipProfile(boolean flipProfile) {
        this.flipProfile = flipProfile;
        markDirty();
    }

    public boolean isOrientToRails() {
        return orientToRails;
    }

    public void setOrientToRails(boolean orientToRails) {
        this.orientToRails = orientToRails;
        markDirty();
    }

    public double getStartScale() {
        return startScale;
    }

    public void setStartScale(double startScale) {
        this.startScale = startScale;
        markDirty();
    }

    public double getEndScale() {
        return endScale;
    }

    public void setEndScale(double endScale) {
        this.endScale = endScale;
        markDirty();
    }

    public double getStartRotationDegrees() {
        return startRotationDegrees;
    }

    public void setStartRotationDegrees(double startRotationDegrees) {
        this.startRotationDegrees = startRotationDegrees;
        markDirty();
    }

    public double getEndRotationDegrees() {
        return endRotationDegrees;
    }

    public void setEndRotationDegrees(double endRotationDegrees) {
        this.endRotationDegrees = endRotationDegrees;
        markDirty();
    }

    private List<Vector3d> resolveProfilePoints() {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        if (profileObj instanceof PolygonProfileData profile) {
            return profile.getUniquePoints();
        }
        return SolidNodeUtils.resolvePointList(inputValues.get(INPUT_PROFILE_POINTS_ID));
    }

    private List<Vector3d> resolveRailPoints(Object lineObj, Object polylineObj, Object curveObj, Object pointsObj) {
        return SolidNodeUtils.resolveSpinePoints(lineObj, polylineObj, curveObj, pointsObj);
    }

    private List<Vector3d> computeCenters(List<Vector3d> railA, List<Vector3d> railB) {
        List<Vector3d> centers = new ArrayList<>(railA.size());
        for (int i = 0; i < railA.size(); i++) {
            centers.add(new Vector3d(railA.get(i)).add(railB.get(i)).mul(0.5d));
        }
        return centers;
    }

    private SolidNodeUtils.Frame buildRailFrame(Vector3d center,
                                                Vector3d railPointA,
                                                Vector3d railPointB,
                                                List<Vector3d> centers,
                                                int index) {
        Vector3d xAxis = new Vector3d(railPointB).sub(railPointA);
        if (xAxis.lengthSquared() <= SolidNodeUtils.EPSILON * SolidNodeUtils.EPSILON) {
            return SolidNodeUtils.buildFrame(center, SolidNodeUtils.computeTangent(centers, index));
        }
        xAxis.normalize();

        Vector3d tangent = SolidNodeUtils.computeTangent(centers, index);
        Vector3d yAxis = new Vector3d(tangent).cross(xAxis);
        if (yAxis.lengthSquared() <= SolidNodeUtils.EPSILON * SolidNodeUtils.EPSILON) {
            return SolidNodeUtils.buildFrame(center, tangent);
        }
        yAxis.normalize();

        Vector3d zAxis = new Vector3d(xAxis).cross(yAxis);
        if (zAxis.lengthSquared() <= SolidNodeUtils.EPSILON * SolidNodeUtils.EPSILON) {
            return SolidNodeUtils.buildFrame(center, tangent);
        }
        zAxis.normalize();

        return new SolidNodeUtils.Frame(center, xAxis, yAxis, zAxis);
    }

    private List<Vector3d> resamplePath(List<Vector3d> points, int count) {
        if (points.size() == count) {
            return copyPoints(points);
        }
        double totalLength = computeLength(points);
        if (totalLength <= SolidNodeUtils.EPSILON) {
            List<Vector3d> repeated = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                repeated.add(new Vector3d(points.getFirst()));
            }
            return List.copyOf(repeated);
        }

        List<Vector3d> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double distance = count <= 1 ? 0.0d : totalLength * (double) i / (double) (count - 1);
            samples.add(samplePathAtDistance(points, distance));
        }
        return List.copyOf(samples);
    }

    private double computeLength(List<Vector3d> points) {
        double length = 0.0d;
        for (int i = 0; i < points.size() - 1; i++) {
            length += points.get(i).distance(points.get(i + 1));
        }
        return length;
    }

    private Vector3d samplePathAtDistance(List<Vector3d> points, double targetDistance) {
        if (targetDistance <= 0.0d) {
            return new Vector3d(points.getFirst());
        }
        double walked = 0.0d;
        for (int i = 0; i < points.size() - 1; i++) {
            Vector3d start = points.get(i);
            Vector3d end = points.get(i + 1);
            double segmentLength = start.distance(end);
            if (segmentLength <= SolidNodeUtils.EPSILON) {
                continue;
            }
            if (walked + segmentLength >= targetDistance) {
                double t = (targetDistance - walked) / segmentLength;
                return new Vector3d(start).lerp(end, t);
            }
            walked += segmentLength;
        }
        return new Vector3d(points.getLast());
    }

    private List<Vector3d> copyPoints(List<Vector3d> points) {
        List<Vector3d> copied = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            copied.add(new Vector3d(point));
        }
        return List.copyOf(copied);
    }

    private double computeProfileWidth(List<Vector3d> profilePoints) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        for (Vector3d point : profilePoints) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
        }
        double width = maxX - minX;
        return width <= SolidNodeUtils.EPSILON ? 1.0d : width;
    }

    private List<Double> resolveNumberList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Double> numbers = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                numbers.add(number.doubleValue());
            }
        }
        return List.copyOf(numbers);
    }

    private double resolveScalarAt(List<Double> values, double t, double start, double end) {
        if (values.isEmpty()) {
            return start + (end - start) * t;
        }
        if (values.size() == 1) {
            return values.getFirst();
        }
        double scaled = Math.max(0.0d, Math.min(1.0d, t)) * (values.size() - 1);
        int i = (int) Math.floor(scaled);
        int next = Math.min(values.size() - 1, i + 1);
        double localT = scaled - i;
        return values.get(i) + (values.get(next) - values.get(i)) * localT;
    }

    private Vector3d transformLocalProfilePoint(Vector3d local,
                                                double scale,
                                                double rotationRadians,
                                                double railWidth,
                                                double profileWidth) {
        double scaledX = local.x * scale;
        double scaledY = local.y * scale;
        double cos = Math.cos(rotationRadians);
        double sin = Math.sin(rotationRadians);
        double rotatedX = scaledX * cos - scaledY * sin;
        double rotatedY = scaledX * sin + scaledY * cos;
        double railScale = railWidth <= SolidNodeUtils.EPSILON ? 1.0d : railWidth / profileWidth;
        return new Vector3d(rotatedX * railScale, rotatedY, local.z * scale);
    }

    private List<List<LineData>> buildRailSegmentRows(List<List<Vector3d>> sections) {
        List<List<LineData>> rows = new ArrayList<>();
        for (int sectionIndex = 0; sectionIndex < sections.size() - 1; sectionIndex++) {
            List<Vector3d> current = sections.get(sectionIndex);
            List<Vector3d> next = sections.get(sectionIndex + 1);
            int segmentCount = Math.min(current.size(), next.size());
            List<LineData> row = new ArrayList<>(segmentCount);
            for (int pointIndex = 0; pointIndex < segmentCount; pointIndex++) {
                Vector3d start = current.get(pointIndex);
                Vector3d end = next.get(pointIndex);
                row.add(new LineData(
                    new Vec3d(start.x, start.y, start.z),
                    new Vec3d(end.x, end.y, end.z)
                ));
            }
            rows.add(row);
        }
        return rows;
    }

    private List<LineData> flattenRailSegments(List<List<LineData>> rows) {
        List<LineData> railSegments = new ArrayList<>();
        for (List<LineData> row : rows) {
            railSegments.addAll(row);
        }
        return railSegments;
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_RAIL_A_POINTS_ID, List.of());
        outputValues.put(OUTPUT_RAIL_B_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.of());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, null);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
