package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generates architectural staircases from a path.
 * <p>
 * Layout {@code straight} follows the true PATH centerline. U / double-run / switchback /
 * spiral layouts still use the path chord for plan orientation.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.staircase",
    displayName = "Staircase",
    description = "Generates architectural staircases from a path",
    category = "geometry.architectural_primitives",
    order = 5
)
public class StaircaseNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;
    private static final Set<String> LAYOUTS = Set.of("straight", "u", "double_run", "switchback", "spiral");
    private static final Set<String> TURNS = Set.of("left", "right");

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_LAYOUT_ID = "input_layout";
    private static final String INPUT_STEP_COUNT_ID = "input_step_count";
    private static final String INPUT_FIRST_FLIGHT_STEPS_ID = "input_first_flight_steps";
    private static final String INPUT_STEP_RUN_ID = "input_step_run";
    private static final String INPUT_STEP_RISE_ID = "input_step_rise";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_LANDING_LENGTH_ID = "input_landing_length";
    private static final String INPUT_TURN_GAP_ID = "input_turn_gap";
    private static final String INPUT_TURN_DIRECTION_ID = "input_turn_direction";
    private static final String INPUT_SPIRAL_RADIUS_ID = "input_spiral_radius";
    private static final String INPUT_SPIRAL_CORE_RADIUS_ID = "input_spiral_core_radius";
    private static final String INPUT_SPIRAL_TURNS_ID = "input_spiral_turns";
    private static final String INPUT_SPIRAL_HEIGHT_ID = "input_spiral_height";
    private static final String INPUT_SPIRAL_START_ANGLE_ID = "input_spiral_start_angle";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public StaircaseNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.staircase");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path for stairs: straight layout follows the path; spiral uses start as axis base and direction as entry tangent",
            NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_LAYOUT_ID, "Layout",
            "Stair layout: straight, u, double_run, switchback, or spiral", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_STEP_COUNT_ID, "Step Count", "Number of steps to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_FIRST_FLIGHT_STEPS_ID, "First Flight Steps",
            "Exact step count before the landing in U/double-run/switchback layouts (1 <= value < Step Count)",
            NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_STEP_RUN_ID, "Step Run", "Horizontal run of each step", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEP_RISE_ID, "Step Rise", "Vertical rise of each step", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Stair width measured across the run", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LANDING_LENGTH_ID, "Landing Length", "Optional top landing length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TURN_GAP_ID, "Turn Gap", "Clear gap between U-shaped flights", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TURN_DIRECTION_ID, "Turn Direction", "Turn direction: left or right", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_SPIRAL_RADIUS_ID, "Spiral Radius", "Radius from the vertical stair axis to the tread centerline", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_CORE_RADIUS_ID, "Spiral Core Radius", "Inner void radius used by the spiral layout", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_TURNS_ID, "Spiral Turns", "Number of turns for the spiral layout", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_HEIGHT_ID, "Spiral Height", "Total vertical rise for the spiral stair", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_START_ANGLE_ID, "Spiral Start Angle", "Additional plan rotation in degrees applied to the spiral stair", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the staircase steps", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of step solids created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid staircase could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates straight (path-following), U-shaped, double-run, switchback, or spiral staircases";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String layout = ArchitecturalInputUtils.resolveKnownStringEnum(this, INPUT_LAYOUT_ID, "straight", LAYOUTS);
        if (layout == null) {
            writeInvalid("Layout must be one of: straight, u, double_run, switchback, spiral");
            return;
        }

        Integer stepCount = ArchitecturalInputUtils.resolveOptionalBoundedExactInteger(
            this, INPUT_STEP_COUNT_ID, 1, 1, GenerationLimits.MAX_ARCHITECTURAL_INSTANCES);
        if (stepCount == null) {
            writeInvalid("Step Count must be an exact INTEGER between 1 and MAX_ARCHITECTURAL_INSTANCES ("
                + GenerationLimits.MAX_ARCHITECTURAL_INSTANCES + ")");
            return;
        }

        boolean needsFirstFlight = "u".equals(layout) || "double_run".equals(layout) || "switchback".equals(layout);
        int firstFlightSteps = 1;
        if (needsFirstFlight) {
            if (stepCount < 2) {
                writeInvalid("U / double_run / switchback layouts require Step Count >= 2");
                return;
            }
            int defaultFirst = Math.max(1, stepCount / 2);
            if (defaultFirst >= stepCount) {
                defaultFirst = stepCount - 1;
            }
            Integer firstFlight = ArchitecturalInputUtils.resolveOptionalExactPositiveInteger(
                this, INPUT_FIRST_FLIGHT_STEPS_ID, defaultFirst);
            if (firstFlight == null || firstFlight < 1 || firstFlight >= stepCount) {
                writeInvalid("First Flight Steps must be an exact INTEGER with 1 <= value < Step Count (no clamping)");
                return;
            }
            firstFlightSteps = firstFlight;
        }

        Double stepRun = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_STEP_RUN_ID, 1.0d);
        if (stepRun == null) {
            writeInvalid("Step Run must be a finite positive DOUBLE");
            return;
        }
        Double stepRise = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_STEP_RISE_ID, 0.2d);
        if (stepRise == null) {
            writeInvalid("Step Rise must be a finite positive DOUBLE");
            return;
        }
        Double width = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_WIDTH_ID, 1.0d);
        if (width == null) {
            writeInvalid("Width must be a finite positive DOUBLE");
            return;
        }
        Double landingLength = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(
            this, INPUT_LANDING_LENGTH_ID, 0.0d);
        if (landingLength == null) {
            writeInvalid("Landing Length must be a finite non-negative DOUBLE");
            return;
        }

        StairParameters parameters = new StairParameters(
            layout, stepCount, firstFlightSteps, stepRun, stepRise, width, landingLength);

        List<GeometryData> steps;
        switch (layout) {
            case "u", "double_run", "switchback" -> {
                ArchitecturalPrimitiveSupport.LineFrame frame =
                    ArchitecturalPrimitiveSupport.resolvePathChordFrame(inputValues.get(INPUT_PATH_ID));
                if (frame == null) {
                    writeInvalid(ArchitecturalInputUtils.isConnected(this, INPUT_PATH_ID)
                        ? "Path must be a finite PATH with at least 2 points (chord used for plan orientation)"
                        : "Path is required");
                    return;
                }
                Double turnGap = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(
                    this, INPUT_TURN_GAP_ID, 0.0d);
                if (turnGap == null) {
                    writeInvalid("Turn Gap must be a finite non-negative DOUBLE");
                    return;
                }
                String turn = ArchitecturalInputUtils.resolveKnownStringEnum(
                    this, INPUT_TURN_DIRECTION_ID, "right", TURNS);
                if (turn == null) {
                    writeInvalid("Turn Direction must be one of: left, right");
                    return;
                }
                steps = buildDoubleRunStairs(frame, parameters, turnGap, "left".equals(turn) ? -1.0d : 1.0d);
            }
            case "spiral" -> {
                ArchitecturalPrimitiveSupport.LineFrame frame =
                    ArchitecturalPrimitiveSupport.resolvePathChordFrame(inputValues.get(INPUT_PATH_ID));
                if (frame == null) {
                    writeInvalid(ArchitecturalInputUtils.isConnected(this, INPUT_PATH_ID)
                        ? "Path must be a finite PATH with at least 2 points (chord used for plan orientation)"
                        : "Path is required");
                    return;
                }
                String turn = ArchitecturalInputUtils.resolveKnownStringEnum(
                    this, INPUT_TURN_DIRECTION_ID, "right", TURNS);
                if (turn == null) {
                    writeInvalid("Turn Direction must be one of: left, right");
                    return;
                }
                SpiralParameters spiral = resolveSpiralParameters(frame, parameters, turn);
                if (spiral == null) {
                    return;
                }
                steps = buildSpiralStairs(parameters, spiral);
            }
            default -> {
                ArchitecturalPathSupport.PathGeometry path =
                    ArchitecturalPathSupport.resolve(inputValues.get(INPUT_PATH_ID));
                if (path == null) {
                    writeInvalid(ArchitecturalInputUtils.isConnected(this, INPUT_PATH_ID)
                        ? "Path must be a finite PATH with at least 2 points"
                        : "Path is required");
                    return;
                }
                List<ArchitecturalPathSupport.Segment> segments = ArchitecturalPathSupport.segments(path);
                if (segments.size() > GenerationLimits.MAX_ARCHITECTURAL_PATH_SEGMENTS) {
                    writeInvalid("Path segment count exceeds MAX_ARCHITECTURAL_PATH_SEGMENTS ("
                        + GenerationLimits.MAX_ARCHITECTURAL_PATH_SEGMENTS + ")");
                    return;
                }
                double requiredRun = parameters.stepCount() * parameters.stepRun()
                    + parameters.landingLength();
                if (requiredRun > path.length() + EPSILON) {
                    writeInvalid("Path is too short for requested Step Count × Step Run + Landing Length");
                    return;
                }
                steps = buildPathFollowingStairs(
                    path, parameters.stepCount(), parameters.stepRun(),
                    parameters.stepRise(), parameters.width(), parameters.landingLength());
            }
        }

        if (steps.isEmpty()) {
            writeInvalid("Unable to generate staircase geometry for the given path and parameters");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(steps));
        outputValues.put(OUTPUT_COUNT_ID, steps.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private List<GeometryData> buildPathFollowingStairs(
        ArchitecturalPathSupport.PathGeometry path,
        int stepCount,
        double stepRun,
        double stepRise,
        double width,
        double landingLength
    ) {
        List<GeometryData> results = new ArrayList<>(stepCount + 1);

        for (int index = 0; index < stepCount; index++) {
            double centerDistance = stepRun * index + stepRun / 2.0d;
            ArchitecturalPathSupport.SampleFrame frame = ArchitecturalPathSupport.sampleAt(path, centerDistance);
            Vector3d center = new Vector3d(frame.origin()).fma(stepRise * index + stepRise / 2.0d, frame.up());
            Vector3d halfExtents = new Vector3d(stepRun / 2.0d, stepRise / 2.0d, width / 2.0d);
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(
                center, halfExtents, frame.tangent(), frame.up(), frame.side()));
        }

        if (landingLength > 0.0d && !results.isEmpty()) {
            double landingCenterDistance = stepRun * stepCount + landingLength / 2.0d;
            ArchitecturalPathSupport.SampleFrame frame = ArchitecturalPathSupport.sampleAt(path, landingCenterDistance);
            Vector3d landingCenter = new Vector3d(frame.origin())
                .fma(stepRise * Math.min(stepCount, results.size()) + stepRise / 2.0d, frame.up());
            Vector3d halfExtents = new Vector3d(landingLength / 2.0d, stepRise / 2.0d, width / 2.0d);
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(
                landingCenter, halfExtents, frame.tangent(), frame.up(), frame.side()));
        }

        return List.copyOf(results);
    }

    private List<GeometryData> buildStraightStairs(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        int stepCount,
        double stepRun,
        double stepRise,
        double width,
        double landingLength
    ) {
        List<GeometryData> results = new ArrayList<>(stepCount + 1);

        for (int index = 0; index < stepCount; index++) {
            Vector3d center = new Vector3d(frame.start())
                .fma(stepRun * index + stepRun / 2.0d, frame.runAxis())
                .fma(stepRise * index + stepRise / 2.0d, frame.upAxis());

            Vector3d halfExtents = new Vector3d(stepRun / 2.0d, stepRise / 2.0d, width / 2.0d);
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(
                center, halfExtents, frame.runAxis(), frame.upAxis(), frame.sideAxis()));
        }

        if (landingLength > 0.0d) {
            Vector3d landingCenter = new Vector3d(frame.start())
                .fma(stepRun * stepCount + landingLength / 2.0d, frame.runAxis())
                .fma(stepRise * stepCount + stepRise / 2.0d, frame.upAxis());
            Vector3d halfExtents = new Vector3d(landingLength / 2.0d, stepRise / 2.0d, width / 2.0d);
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(
                landingCenter, halfExtents, frame.runAxis(), frame.upAxis(), frame.sideAxis()));
        }

        return List.copyOf(results);
    }

    private List<GeometryData> buildDoubleRunStairs(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        StairParameters parameters,
        double turnGap,
        double turnDirection
    ) {
        int secondFlightCount = parameters.stepCount() - parameters.firstFlightSteps();
        Vector3d sideOffset = new Vector3d(frame.sideAxis()).mul(turnDirection * (parameters.width() + turnGap));

        List<GeometryData> results = new ArrayList<>(parameters.stepCount() + 1);
        results.addAll(buildStraightStairs(
            frame, parameters.firstFlightSteps(), parameters.stepRun(), parameters.stepRise(),
            parameters.width(), 0.0d));

        if (parameters.landingLength() > EPSILON) {
            Vector3d landingCenter = new Vector3d(frame.start())
                .fma(parameters.stepRun() * parameters.firstFlightSteps() + parameters.landingLength() / 2.0d, frame.runAxis())
                .fma(parameters.stepRise() * parameters.firstFlightSteps() + parameters.stepRise() / 2.0d, frame.upAxis())
                .fma((parameters.width() + turnGap) / 2.0d * turnDirection, frame.sideAxis());
            Vector3d landingHalfExtents = new Vector3d(
                parameters.landingLength() / 2.0d,
                parameters.stepRise() / 2.0d,
                (parameters.width() + turnGap) / 2.0d);
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(
                landingCenter, landingHalfExtents, frame.runAxis(), frame.upAxis(), frame.sideAxis()));
        }

        Vector3d secondFlightStart = new Vector3d(frame.start())
            .fma(parameters.stepRun() * parameters.firstFlightSteps() + parameters.landingLength(), frame.runAxis())
            .fma(parameters.stepRise() * parameters.firstFlightSteps(), frame.upAxis())
            .add(sideOffset);

        for (int index = 0; index < secondFlightCount; index++) {
            Vector3d center = new Vector3d(secondFlightStart)
                .fma(-(parameters.stepRun() * index + parameters.stepRun() / 2.0d), frame.runAxis())
                .fma(parameters.stepRise() * index + parameters.stepRise() / 2.0d, frame.upAxis());
            results.add(createStepBox(
                center, new Vector3d(frame.runAxis()).negate(), frame.upAxis(), frame.sideAxis(),
                parameters.stepRun(), parameters.stepRise(), parameters.width()));
        }

        return List.copyOf(results);
    }

    private List<GeometryData> buildSpiralStairs(StairParameters parameters, SpiralParameters spiral) {
        if (parameters.stepCount() < 2) {
            return List.of();
        }

        List<GeometryData> results = new ArrayList<>(parameters.stepCount());

        for (int index = 0; index < parameters.stepCount(); index++) {
            double angle = spiral.startAngle() + spiral.rotationSign() * spiral.anglePerStep() * (index + 0.5d);
            Vector3d radial = spiralRadial(spiral, angle);
            Vector3d center = new Vector3d(spiral.axisBase())
                .fma(spiral.risePerStep() * (index + 0.5d), spiral.upAxis())
                .fma(spiral.radius(), radial);

            Vector3d tangent = new Vector3d(spiral.upAxis()).cross(radial).mul(spiral.rotationSign());
            if (tangent.lengthSquared() <= EPSILON) {
                continue;
            }
            tangent.normalize();

            results.add(createStepBox(
                center, tangent, spiral.upAxis(), new Vector3d(radial).normalize(),
                parameters.stepRun(), parameters.stepRise(), parameters.width()));
        }

        return List.copyOf(results);
    }

    private @Nullable SpiralParameters resolveSpiralParameters(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        StairParameters parameters,
        String turn
    ) {
        Double spiralHeight = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_SPIRAL_HEIGHT_ID, parameters.stepRise() * parameters.stepCount());
        if (spiralHeight == null) {
            writeInvalid("Spiral Height must be a finite positive DOUBLE");
            return null;
        }
        Double spiralTurns = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_SPIRAL_TURNS_ID, 1.0d);
        if (spiralTurns == null) {
            writeInvalid("Spiral Turns must be a finite positive DOUBLE");
            return null;
        }
        Double spiralRadius = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_SPIRAL_RADIUS_ID, Math.max(parameters.width(), parameters.stepRun()));
        if (spiralRadius == null) {
            writeInvalid("Spiral Radius must be a finite positive DOUBLE");
            return null;
        }
        Double coreRadius = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(
            this, INPUT_SPIRAL_CORE_RADIUS_ID, Math.max(0.0d, spiralRadius - parameters.width()));
        if (coreRadius == null) {
            writeInvalid("Spiral Core Radius must be a finite non-negative DOUBLE");
            return null;
        }
        Double startAngleDeg = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(
            this, INPUT_SPIRAL_START_ANGLE_ID, 0.0d);
        if (startAngleDeg == null) {
            writeInvalid("Spiral Start Angle must be a finite non-negative DOUBLE");
            return null;
        }

        double rotationSign = "left".equals(turn) ? 1.0d : -1.0d;
        Vector3d upAxis = new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d entryTangent = resolveSpiralEntryTangent(frame.runAxis(), upAxis);
        Vector3d radialBasis = new Vector3d(entryTangent).cross(upAxis).mul(rotationSign);
        if (radialBasis.lengthSquared() <= EPSILON) {
            radialBasis.set(1.0d, 0.0d, 0.0d);
        } else {
            radialBasis.normalize();
        }
        Vector3d tangentBasis = new Vector3d(upAxis).cross(radialBasis);
        if (tangentBasis.lengthSquared() <= EPSILON) {
            tangentBasis.set(0.0d, 0.0d, 1.0d);
        } else {
            tangentBasis.normalize();
        }
        return new SpiralParameters(
            Math.max(spiralRadius, coreRadius + parameters.width() * 0.5d),
            spiralHeight / parameters.stepCount(),
            2.0d * Math.PI * spiralTurns / parameters.stepCount(),
            Math.toRadians(startAngleDeg),
            rotationSign,
            new Vector3d(frame.start()),
            upAxis,
            radialBasis,
            tangentBasis
        );
    }

    private BoxGeometryData createStepBox(
        Vector3d center,
        Vector3d runAxis,
        Vector3d upAxis,
        Vector3d sideAxis,
        double stepRun,
        double stepRise,
        double width
    ) {
        Vector3d halfExtents = new Vector3d(stepRun / 2.0d, stepRise / 2.0d, width / 2.0d);
        return ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, runAxis, upAxis, sideAxis);
    }

    private Vector3d resolveSpiralEntryTangent(Vector3d runAxis, Vector3d upAxis) {
        Vector3d horizontal = new Vector3d(runAxis).sub(new Vector3d(upAxis).mul(runAxis.dot(upAxis)));
        if (horizontal.lengthSquared() <= EPSILON) {
            return new Vector3d(1.0d, 0.0d, 0.0d);
        }
        return horizontal.normalize();
    }

    private Vector3d spiralRadial(SpiralParameters spiral, double angle) {
        return new Vector3d(spiral.radialBasis()).mul(Math.cos(angle))
            .add(new Vector3d(spiral.tangentBasis()).mul(Math.sin(angle)));
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private record StairParameters(
        String layout,
        int stepCount,
        int firstFlightSteps,
        double stepRun,
        double stepRise,
        double width,
        double landingLength
    ) {
    }

    private record SpiralParameters(
        double radius,
        double risePerStep,
        double anglePerStep,
        double startAngle,
        double rotationSign,
        Vector3d axisBase,
        Vector3d upAxis,
        Vector3d radialBasis,
        Vector3d tangentBasis
    ) {
    }
}
