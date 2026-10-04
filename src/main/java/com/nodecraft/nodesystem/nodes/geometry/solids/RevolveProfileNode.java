package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.revolve",
    displayName = "Revolve Surface",
    description = "Revolves a polygon profile around an axis and emits section profiles plus a side surface strip",
    category = "geometry.solids",
    order = 11
)
public class RevolveProfileNode extends AbstractSolidNode {

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

    public RevolveProfileNode() {
        super(UUID.randomUUID(), "geometry.solids.revolve");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to revolve", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_AXIS_LINE_ID, "Axis Line", "Optional axis line defining origin and direction", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_AXIS_ORIGIN_ID, "Axis Origin", "Fallback axis origin point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_DIRECTION_ID, "Axis Direction", "Fallback axis direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_DEGREES_ID, "Angle Degrees", "Revolution angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEPS_ID, "Steps", "Number of rotational sections to generate", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_SECTION_PROFILES_ID, "Section Profiles", "Polygon profiles generated along the revolution", NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "Boundary paths for each revolved section", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ALL_POINTS_ID, "All Points", "Flattened list of all revolved section points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Reusable strip surface made of revolved sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of generated rotational sections", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a profile and axis were resolved", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Revolves a polygon profile around an axis and emits section profiles plus a side surface strip";
    }

    @Override
    public String getDisplayName() {
        return "Revolve Surface";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        if (!(profileObj instanceof PolygonProfileData profile)) {
            invalidate("Profile is missing or invalid");
            return;
        }

        Axis axis = resolveAxis();
        if (axis == null) {
            invalidate("Axis is missing or invalid");
            return;
        }

        List<Vector3d> baseUniquePoints = profile.getUniquePoints();
        if (baseUniquePoints.size() < 3) {
            invalidate("Profile must have at least 3 vertices");
            return;
        }

        Double angleDegreesObj = resolveFiniteDouble(INPUT_ANGLE_DEGREES_ID, 360.0d);
        if (angleDegreesObj == null) {
            invalidate("Angle Degrees is connected but invalid (must be finite)");
            return;
        }
        double angleDegrees = angleDegreesObj;
        double angleRadians = Math.toRadians(angleDegrees);
        if (Math.abs(angleRadians) <= EPSILON) {
            invalidate("Revolution angle must be non-zero");
            return;
        }

        Integer stepsObj = resolvePositiveInteger(INPUT_STEPS_ID, defaultSteps);
        if (stepsObj == null) {
            invalidate("Steps is connected but invalid (must be a positive integer)");
            return;
        }
        int steps = stepsObj;
        if (steps < 2) {
            invalidate("Steps must be at least 2");
            return;
        }
        if (!GenerationLimits.isWithinSurfaceSections(steps + 1)) {
            invalidate("Section count exceeds limit (" + GenerationLimits.MAX_SURFACE_SECTIONS + ")");
            return;
        }

        boolean closedRevolution = Math.abs(Math.abs(angleDegrees) - 360.0d) <= 1.0e-6d;
        int sectionCount = closedRevolution ? steps : steps + 1;

        List<PolygonProfileData> sectionProfiles = new ArrayList<>(sectionCount);
        List<PathData> sectionPaths = new ArrayList<>(sectionCount);
        List<Vector3d> allPoints = new ArrayList<>(baseUniquePoints.size() * sectionCount);
        List<List<Vector3d>> stripSections = new ArrayList<>(sectionCount);
        List<Boolean> sectionClosedFlags = new ArrayList<>(sectionCount);

        for (int i = 0; i < sectionCount; i++) {
            double t = closedRevolution ? (double) i / (double) steps : (double) i / (double) (sectionCount - 1);
            double currentAngle = angleRadians * t;

            List<Vector3d> uniqueSectionPoints = new ArrayList<>(baseUniquePoints.size());
            for (Vector3d point : baseUniquePoints) {
                Vector3d revolvedPoint = SolidNodeUtils.rotateAroundAxis(point, axis.origin(), axis.direction(), currentAngle);
                if (revolvedPoint == null) {
                    invalidate("Revolved section point is non-finite");
                    return;
                }
                uniqueSectionPoints.add(revolvedPoint);
                allPoints.add(revolvedPoint);
            }

            List<Vector3d> closedSectionPoints = new ArrayList<>(uniqueSectionPoints.size() + 1);
            closedSectionPoints.addAll(uniqueSectionPoints);
            closedSectionPoints.add(new Vector3d(uniqueSectionPoints.getFirst()));

            Vector3d sectionCenter = SolidNodeUtils.computeCenter(uniqueSectionPoints);
            if (sectionCenter == null) {
                invalidate("Revolved section center is missing or non-finite");
                return;
            }
            Vector3d sectionNormal = SolidNodeUtils.rotateAroundAxis(
                profile.plane().getNormal(),
                new Vector3d(),
                axis.direction(),
                currentAngle
            );
            if (sectionNormal == null || sectionNormal.lengthSquared() <= EPSILON) {
                sectionNormal = new Vector3d(axis.direction());
            }
            PlaneData sectionPlane = new PlaneData(sectionCenter, sectionNormal);
            PolygonProfileData sectionProfile;
            try {
                sectionProfile = new PolygonProfileData(closedSectionPoints, sectionPlane);
            } catch (IllegalArgumentException ex) {
                invalidate(ex.getMessage() == null ? "Revolved section profile is invalid" : ex.getMessage());
                return;
            }

            sectionProfiles.add(sectionProfile);
            PathData boundary = SolidNodeUtils.toPath(sectionProfile.getBoundary());
            if (boundary == null) {
                invalidate("Section path at index " + i + " is invalid");
                return;
            }
            sectionPaths.add(boundary);
            stripSections.add(List.copyOf(uniqueSectionPoints));
            sectionClosedFlags.add(true);
        }

        SurfaceStripData surfaceStrip;
        try {
            surfaceStrip = new SurfaceStripData(stripSections, sectionClosedFlags);
        } catch (IllegalArgumentException ex) {
            invalidate(ex.getMessage() == null ? "Surface strip is invalid" : ex.getMessage());
            return;
        }
        String stripError = validateSurfaceStrip(surfaceStrip);
        if (stripError != null) {
            invalidate(stripError);
            return;
        }

        outputValues.put(OUTPUT_SECTION_PROFILES_ID, List.copyOf(sectionProfiles));
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_ALL_POINTS_ID, SpatialValueResolver.toPointDataList(allPoints));
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, sectionCount);
        markSuccess();
    }

    public int getDefaultSteps() {
        return defaultSteps;
    }

    public void setDefaultSteps(int defaultSteps) {
        markDirtyIfChanged(this.defaultSteps, defaultSteps);
        this.defaultSteps = defaultSteps;
    }

    private @Nullable Axis resolveAxis() {
        Object axisLineObj = inputValues.get(INPUT_AXIS_LINE_ID);
        if (axisLineObj instanceof LineData line) {
            Vector3d start = new Vector3d(line.start().x, line.start().y, line.start().z);
            Vector3d end = new Vector3d(line.end().x, line.end().y, line.end().z);
            Vector3d unit = com.nodecraft.nodesystem.util.VectorUtils.safeNormalize(
                com.nodecraft.nodesystem.util.VectorUtils.safeSubtract(end, start));
            if (unit != null) {
                return new Axis(start, unit);
            }
        }

        Vector3d origin = SolidNodeUtils.resolvePoint(inputValues.get(INPUT_AXIS_ORIGIN_ID));
        Vector3d direction = SolidNodeUtils.resolveDirection(inputValues.get(INPUT_AXIS_DIRECTION_ID));
        Vector3d unit = com.nodecraft.nodesystem.util.VectorUtils.safeNormalize(direction);
        if (origin == null || unit == null) {
            return null;
        }
        return new Axis(origin, unit);
    }

    private void invalidate(String error) {
        putEmptyListOutputs(OUTPUT_SECTION_PROFILES_ID, OUTPUT_SECTION_PATHS_ID, OUTPUT_ALL_POINTS_ID);
        putNullOutputs(OUTPUT_SURFACE_STRIP_ID);
        putIntOutputs(0, OUTPUT_SECTION_COUNT_ID);
        markInvalid(error);
    }

    private record Axis(Vector3d origin, Vector3d direction) {
    }
}
