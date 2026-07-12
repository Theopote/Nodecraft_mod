package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.revolve",
    displayName = "Revolve Profile",
    description = "Revolves a polygon profile around an axis and emits section profiles plus a side surface strip",
    category = "geometry.solids",
    order = 7
)
public class RevolveProfileNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Default Steps", category = "Revolve", order = 1)
    private int defaultSteps = 24;

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_AXIS_LINE_ID = "input_axis_line";
    private static final String INPUT_AXIS_ORIGIN_ID = "input_axis_origin";
    private static final String INPUT_AXIS_DIRECTION_ID = "input_axis_direction";
    private static final String INPUT_ANGLE_DEGREES_ID = "input_angle_degrees";
    private static final String INPUT_STEPS_ID = "input_steps";

    private static final String OUTPUT_SECTION_PROFILES_ID = "output_section_profiles";
    private static final String OUTPUT_SECTION_PATHS_ID = "output_section_paths";
    private static final String OUTPUT_ALL_POINTS_ID = "output_all_points";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";
    private static final String OUTPUT_ANGLE_RADIANS_ID = "output_angle_radians";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RevolveProfileNode() {
        super(UUID.randomUUID(), "geometry.solids.revolve");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to revolve", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_AXIS_LINE_ID, "Axis Line", "Optional axis line defining origin and direction", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_AXIS_ORIGIN_ID, "Axis Origin", "Fallback axis origin point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_DIRECTION_ID, "Axis Direction", "Fallback axis direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_DEGREES_ID, "Angle Degrees", "Revolution angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEPS_ID, "Steps", "Number of rotational sections to generate", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_SECTION_PROFILES_ID, "Section Profiles", "Polygon profiles generated along the revolution", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "Boundary polylines for each revolved section", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ALL_POINTS_ID, "All Points", "Flattened list of all revolved section points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Reusable strip surface made of revolved sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of generated rotational sections", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ANGLE_RADIANS_ID, "Angle Radians", "Resolved revolution angle in radians", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a profile and axis were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Revolves a polygon profile around an axis and emits section profiles plus a side surface strip";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        if (!(profileObj instanceof PolygonProfileData profile)) {
            writeEmptyOutputs();
            return;
        }

        Axis axis = resolveAxis();
        if (axis == null) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> baseUniquePoints = profile.getUniquePoints();
        if (baseUniquePoints.size() < 3) {
            writeEmptyOutputs();
            return;
        }

        double angleDegrees = getInputDouble(INPUT_ANGLE_DEGREES_ID, 360.0d);
        double angleRadians = Math.toRadians(angleDegrees);
        if (Math.abs(angleRadians) <= EPSILON) {
            writeEmptyOutputs();
            return;
        }

        boolean closedRevolution = Math.abs(Math.abs(angleDegrees) - 360.0d) <= 1.0e-6d;
        int steps = GenerationLimits.clampSegments(2, getInputInt(INPUT_STEPS_ID, defaultSteps));
        int sectionCount = closedRevolution ? steps : steps + 1;
        sectionCount = GenerationLimits.clampSectionCountForProfile(
            closedRevolution ? 2 : 3,
            sectionCount,
            baseUniquePoints.size()
        );
        steps = closedRevolution ? sectionCount : sectionCount - 1;

        List<Object> sectionProfiles = new ArrayList<>(sectionCount);
        List<Object> sectionPaths = new ArrayList<>(sectionCount);
        List<Vector3d> allPoints = new ArrayList<>(baseUniquePoints.size() * sectionCount);
        List<List<Vector3d>> stripSections = new ArrayList<>(sectionCount);
        List<Boolean> sectionClosedFlags = new ArrayList<>(sectionCount);

        for (int i = 0; i < sectionCount; i++) {
            double t = closedRevolution ? (double) i / (double) steps : (double) i / (double) (sectionCount - 1);
            double currentAngle = angleRadians * t;

            List<Vector3d> uniqueSectionPoints = new ArrayList<>(baseUniquePoints.size());
            for (Vector3d point : baseUniquePoints) {
                Vector3d revolvedPoint = SolidNodeUtils.rotateAroundAxis(point, axis.origin(), axis.direction(), currentAngle);
                uniqueSectionPoints.add(revolvedPoint);
                allPoints.add(revolvedPoint);
            }

            List<Vector3d> closedSectionPoints = new ArrayList<>(uniqueSectionPoints.size() + 1);
            closedSectionPoints.addAll(uniqueSectionPoints);
            closedSectionPoints.add(new Vector3d(uniqueSectionPoints.get(0)));

            Vector3d sectionCenter = SolidNodeUtils.computeCenter(uniqueSectionPoints);
            Vector3d sectionNormal = SolidNodeUtils.rotateAroundAxis(
                profile.getPlane().getNormal(),
                new Vector3d(),
                axis.direction(),
                currentAngle
            );
            if (sectionNormal.lengthSquared() <= EPSILON) {
                sectionNormal = new Vector3d(axis.direction());
            }
            PlaneData sectionPlane = new PlaneData(sectionCenter, sectionNormal);
            PolygonProfileData sectionProfile = new PolygonProfileData(closedSectionPoints, sectionPlane);

            sectionProfiles.add(sectionProfile);
            sectionPaths.add(sectionProfile.getBoundary());
            stripSections.add(List.copyOf(uniqueSectionPoints));
            sectionClosedFlags.add(true);
        }

        SurfaceStripData surfaceStrip = new SurfaceStripData(stripSections, sectionClosedFlags);

        outputValues.put(OUTPUT_SECTION_PROFILES_ID, List.copyOf(sectionProfiles));
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.copyOf(allPoints));
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, sectionCount);
        outputValues.put(OUTPUT_ANGLE_RADIANS_ID, angleRadians);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public int getDefaultSteps() {
        return defaultSteps;
    }

    public void setDefaultSteps(int defaultSteps) {
        this.defaultSteps = GenerationLimits.clampSegments(2, defaultSteps);
        markDirty();
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SECTION_PROFILES_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.of());
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, null);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, 0);
        outputValues.put(OUTPUT_ANGLE_RADIANS_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private @Nullable Axis resolveAxis() {
        Object axisLineObj = inputValues.get(INPUT_AXIS_LINE_ID);
        if (axisLineObj instanceof LineData line) {
            Vector3d start = new Vector3d(line.getStart().x, line.getStart().y, line.getStart().z);
            Vector3d end = new Vector3d(line.getEnd().x, line.getEnd().y, line.getEnd().z);
            Vector3d direction = end.sub(start, new Vector3d());
            if (direction.lengthSquared() > EPSILON) {
                return new Axis(start, direction.normalize());
            }
        }

        Vector3d origin = SolidNodeUtils.resolvePoint(inputValues.get(INPUT_AXIS_ORIGIN_ID));
        Vector3d direction = SolidNodeUtils.resolveDirection(inputValues.get(INPUT_AXIS_DIRECTION_ID));
        if (origin == null || direction == null || direction.lengthSquared() <= EPSILON) {
            return null;
        }
        return new Axis(origin, direction.normalize());
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private record Axis(Vector3d origin, Vector3d direction) {
    }
}
