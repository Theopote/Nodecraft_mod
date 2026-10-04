package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.TrigMathOps;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import com.nodecraft.nodesystem.util.ProfileConstructionUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Architectural staircase with layout-specific walk path and step metadata.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.staircase",
    displayName = "Staircase",
    description = "Generates stairs: straight follows PATH; U = 180 reverse pair; double_run = 90 turn; switchback = multi-flight 180; spiral uses annular-sector treads. Height along world +Y for spiral.",
    category = "geometry.architectural_primitives",
    order = 5
)
public class StaircaseNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;
    private static final Set<String> LAYOUTS = Set.of("straight", "u", "double_run", "switchback", "spiral");
    private static final Set<String> TURNS = Set.of("left", "right");
    private static final int SPIRAL_ARC_SEGMENTS = 12;

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
    private static final String OUTPUT_STEP_FRAMES_ID = "output_step_frames";
    private static final String OUTPUT_STEP_CENTERS_ID = "output_step_centers";
    private static final String OUTPUT_WALK_PATH_ID = "output_walk_path";
    private static final String OUTPUT_BOTTOM_ID = "output_bottom";
    private static final String OUTPUT_TOP_ID = "output_top";
    private static final String OUTPUT_RISE_ID = "output_rise";
    private static final String OUTPUT_RUN_ID = "output_run";
    private static final String OUTPUT_TOTAL_RISE_ID = "output_total_rise";
    private static final String OUTPUT_TOTAL_RUN_ID = "output_total_run";
    private static final String OUTPUT_LANDING_GEOMETRY_ID = "output_landing_geometry";
    private static final String OUTPUT_LANDING_FRAMES_ID = "output_landing_frames";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public StaircaseNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.staircase");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path for stairs: straight layout follows the path; spiral uses start as axis base and direction as entry tangent",
            NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_LAYOUT_ID, "Layout",
            "Stair layout: straight, u (180 reverse pair), double_run (90 turn), switchback (multi-flight 180), or spiral",
            NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_STEP_COUNT_ID, "Step Count", "Number of steps to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_FIRST_FLIGHT_STEPS_ID, "First Flight Steps",
            "U/double_run: steps before the first landing. Switchback: steps per flight (1 <= value < Step Count)",
            NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_STEP_RUN_ID, "Step Run", "Horizontal run of each step", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEP_RISE_ID, "Step Rise", "Vertical rise of each step", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Stair width measured across the run", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LANDING_LENGTH_ID, "Landing Length", "Landing length between flights (0 = none)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TURN_GAP_ID, "Turn Gap", "Clear gap between U/switchback flights", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TURN_DIRECTION_ID, "Turn Direction", "Turn direction: left or right", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_SPIRAL_RADIUS_ID, "Spiral Radius", "Radius from the vertical stair axis to the tread centerline", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_CORE_RADIUS_ID, "Spiral Core Radius", "Inner void radius used by the spiral layout", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_TURNS_ID, "Spiral Turns", "Number of turns for the spiral layout", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_HEIGHT_ID, "Spiral Height", "Total vertical rise for the spiral stair", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_START_ANGLE_ID, "Spiral Start Angle",
            "Additional plan rotation in degrees applied to the spiral stair (any finite degree; period-reduced)",
            NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the staircase steps", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_STEP_FRAMES_ID, "Step Frames", "Placement frames at each step center", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_STEP_CENTERS_ID, "Step Centers", "Step center points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_WALK_PATH_ID, "Walk Path", "Resolved walking centerline (not the input chord)", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_BOTTOM_ID, "Bottom", "Walk-path start", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_TOP_ID, "Top", "Walk-path end", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_RISE_ID, "Rise", "Per-step rise", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RUN_ID, "Run", "Per-step run", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_RISE_ID, "Total Rise", "Total vertical rise", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_RUN_ID, "Total Run", "Total horizontal run including landings", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_LANDING_GEOMETRY_ID, "Landing Geometry", "Landing solids when present", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_LANDING_FRAMES_ID, "Landing Frames", "Placement frames at landing centers", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of step solids created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid staircase could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates straight (path-following), U, double-run (90), switchback, or spiral (annular-sector tread) staircases";
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

        double requiredRun = stepCount * stepRun + landingLength;
        if (!ArchitecturalNodeOutputs.allFinite(requiredRun, stepCount * stepRun, stepCount * stepRise)) {
            writeInvalid("Derived stair dimensions are non-finite");
            return;
        }

        StairParameters parameters = new StairParameters(
            layout, stepCount, firstFlightSteps, stepRun, stepRise, width, landingLength);

        StairBuild build;
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
                double turnSign = "left".equals(turn) ? -1.0d : 1.0d;
                build = switch (layout) {
                    case "u" -> buildUStairs(frame, parameters, turnGap, turnSign);
                    case "double_run" -> buildDoubleRunStairs(frame, parameters, turnGap, turnSign);
                    default -> buildSwitchbackStairs(frame, parameters, turnGap, turnSign);
                };
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
                build = buildSpiralStairs(parameters, spiral);
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
                if (requiredRun > path.length() + EPSILON) {
                    writeInvalid("Path is too short for requested Step Count × Step Run + Landing Length");
                    return;
                }
                build = buildPathFollowingStairs(path, parameters);
            }
        }

        if (build == null || build.steps().isEmpty()) {
            writeInvalid("Unable to generate staircase geometry for the given path and parameters");
            return;
        }
        if (!ArchitecturalNodeOutputs.allFinite(build.totalRise(), build.totalRun(), parameters.stepRise(), parameters.stepRun())) {
            writeInvalid("Derived stair metrics are non-finite");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(build.steps()));
        outputValues.put(OUTPUT_STEP_FRAMES_ID, build.stepFrames());
        outputValues.put(OUTPUT_STEP_CENTERS_ID, build.stepCenters());
        outputValues.put(OUTPUT_WALK_PATH_ID, polylinePath(build.walk()));
        outputValues.put(OUTPUT_BOTTOM_ID, new PointData(build.walk().getFirst()));
        outputValues.put(OUTPUT_TOP_ID, new PointData(build.walk().getLast()));
        outputValues.put(OUTPUT_RISE_ID, parameters.stepRise());
        outputValues.put(OUTPUT_RUN_ID, parameters.stepRun());
        outputValues.put(OUTPUT_TOTAL_RISE_ID, build.totalRise());
        outputValues.put(OUTPUT_TOTAL_RUN_ID, build.totalRun());
        outputValues.put(OUTPUT_LANDING_GEOMETRY_ID, GeometryOutputUtils.packGeometry(build.landings()));
        outputValues.put(OUTPUT_LANDING_FRAMES_ID, build.landingFrames());
        outputValues.put(OUTPUT_COUNT_ID, build.steps().size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private StairBuild buildPathFollowingStairs(
        ArchitecturalPathSupport.PathGeometry path,
        StairParameters parameters
    ) {
        List<PlacedSolid> steps = new ArrayList<>(parameters.stepCount() + 1);
        List<Vector3d> walk = new ArrayList<>();
        ArchitecturalPathSupport.SampleFrame startFrame = ArchitecturalPathSupport.sampleAt(path, 0.0d);
        walk.add(new Vector3d(startFrame.origin()));
        for (int index = 0; index < parameters.stepCount(); index++) {
            double centerDistance = parameters.stepRun() * index + parameters.stepRun() / 2.0d;
            ArchitecturalPathSupport.SampleFrame frame = ArchitecturalPathSupport.sampleAt(path, centerDistance);
            Vector3d center = new Vector3d(frame.origin())
                .fma(parameters.stepRise() * index + parameters.stepRise() / 2.0d, frame.up());
            PlacedSolid step = orientedStep(center, frame.tangent(), frame.up(), frame.side(), parameters);
            if (step == null) {
                return null;
            }
            steps.add(step);
            walk.add(new Vector3d(center).fma(parameters.stepRise() / 2.0d, frame.up()));
        }
        if (parameters.landingLength() > 0.0d) {
            double landingCenterDistance = parameters.stepRun() * parameters.stepCount() + parameters.landingLength() / 2.0d;
            ArchitecturalPathSupport.SampleFrame frame = ArchitecturalPathSupport.sampleAt(path, landingCenterDistance);
            Vector3d landingCenter = new Vector3d(frame.origin())
                .fma(parameters.stepRise() * parameters.stepCount() + parameters.stepRise() / 2.0d, frame.up());
            PlacedSolid landing = orientedBox(
                landingCenter, frame.tangent(), frame.up(), frame.side(),
                parameters.landingLength(), parameters.stepRise(), parameters.width());
            if (landing == null) {
                return null;
            }
            walk.add(new Vector3d(landingCenter).fma(parameters.stepRise() / 2.0d, frame.up()));
            return toBuild(steps, List.of(landing), walk, parameters.stepRise() * parameters.stepCount(),
                parameters.stepRun() * parameters.stepCount() + parameters.landingLength());
        }
        return toBuild(steps, List.of(), walk, parameters.stepRise() * parameters.stepCount(),
            parameters.stepRun() * parameters.stepCount());
    }

    private StairBuild buildUStairs(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        StairParameters parameters,
        double turnGap,
        double turnDirection
    ) {
        return buildTwoFlight(
            frame, parameters, turnGap, turnDirection, true);
    }

    private StairBuild buildDoubleRunStairs(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        StairParameters parameters,
        double turnGap,
        double turnDirection
    ) {
        return buildTwoFlight(frame, parameters, turnGap, turnDirection, false);
    }

    private StairBuild buildTwoFlight(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        StairParameters parameters,
        double turnGap,
        double turnDirection,
        boolean reverseSecond
    ) {
        int secondFlightCount = parameters.stepCount() - parameters.firstFlightSteps();
        List<PlacedSolid> steps = new ArrayList<>();
        List<PlacedSolid> landings = new ArrayList<>();
        List<Vector3d> walk = new ArrayList<>();

        Vector3d run = frame.runAxis();
        Vector3d up = frame.upAxis();
        Vector3d side = frame.sideAxis();
        walk.add(new Vector3d(frame.start()));
        if (!addStraightFlight(steps, walk, frame.start(), run, up, side, 0, parameters.firstFlightSteps(), parameters)) {
            return null;
        }

        Vector3d firstEnd = new Vector3d(frame.start())
            .fma(parameters.stepRun() * parameters.firstFlightSteps(), run)
            .fma(parameters.stepRise() * parameters.firstFlightSteps(), up);

        if (parameters.landingLength() > EPSILON) {
            Vector3d landingCenter;
            Vector3d landingRun;
            Vector3d landingSide;
            Vector3d landingHalf;
            if (reverseSecond) {
                landingCenter = new Vector3d(firstEnd)
                    .fma(parameters.landingLength() / 2.0d, run)
                    .fma(parameters.stepRise() / 2.0d, up)
                    .fma(turnDirection * (parameters.width() + turnGap) / 2.0d, side);
                landingRun = run;
                landingSide = side;
                landingHalf = new Vector3d(
                    parameters.landingLength() / 2.0d,
                    parameters.stepRise() / 2.0d,
                    (parameters.width() + turnGap) / 2.0d);
            } else {
                landingCenter = new Vector3d(firstEnd)
                    .fma(parameters.landingLength() / 2.0d, run)
                    .fma(parameters.stepRise() / 2.0d, up)
                    .fma(turnDirection * (parameters.width() + turnGap) / 2.0d, side);
                landingRun = run;
                landingSide = side;
                landingHalf = new Vector3d(
                    parameters.landingLength() / 2.0d,
                    parameters.stepRise() / 2.0d,
                    (parameters.width() + turnGap) / 2.0d);
            }
            PlacedSolid landing = orientedBoxExtents(landingCenter, landingRun, up, landingSide, landingHalf);
            if (landing == null) {
                return null;
            }
            landings.add(landing);
            walk.add(new Vector3d(landingCenter).fma(parameters.stepRise() / 2.0d, up));
        }

        Vector3d secondStart;
        Vector3d secondRun;
        if (reverseSecond) {
            secondStart = new Vector3d(firstEnd)
                .fma(parameters.landingLength(), run)
                .add(new Vector3d(side).mul(turnDirection * (parameters.width() + turnGap)));
            secondRun = new Vector3d(run).negate();
        } else {
            secondStart = new Vector3d(firstEnd)
                .fma(parameters.landingLength(), run)
                .add(new Vector3d(side).mul(turnDirection * (parameters.width() + turnGap)));
            secondRun = new Vector3d(side).mul(turnDirection);
        }
        Vector3d secondWidth = reverseSecond ? side : run;
        if (!addStraightFlight(steps, walk, secondStart, secondRun, up, secondWidth,
            parameters.firstFlightSteps(), secondFlightCount, parameters)) {
            return null;
        }

        int landingCount = landings.isEmpty() ? 0 : 1;
        return toBuild(steps, landings, walk,
            parameters.stepRise() * parameters.stepCount(),
            parameters.stepRun() * parameters.stepCount() + parameters.landingLength() * landingCount);
    }

    private StairBuild buildSwitchbackStairs(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        StairParameters parameters,
        double turnGap,
        double turnDirection
    ) {
        List<Integer> flights = splitFlights(parameters.stepCount(), parameters.firstFlightSteps());
        List<PlacedSolid> steps = new ArrayList<>();
        List<PlacedSolid> landings = new ArrayList<>();
        List<Vector3d> walk = new ArrayList<>();
        Vector3d origin = new Vector3d(frame.start());
        Vector3d run = new Vector3d(frame.runAxis());
        Vector3d up = frame.upAxis();
        Vector3d side = frame.sideAxis();
        walk.add(new Vector3d(origin));
        int stepsDone = 0;
        int landingCount = 0;
        for (int f = 0; f < flights.size(); f++) {
            int count = flights.get(f);
            if (!addStraightFlight(steps, walk, origin, run, up, side, stepsDone, count, parameters)) {
                return null;
            }
            stepsDone += count;
            if (f == flights.size() - 1) {
                break;
            }
            Vector3d flightEnd = new Vector3d(origin)
                .fma(parameters.stepRun() * count, run)
                .fma(parameters.stepRise() * count, up);
            if (parameters.landingLength() > EPSILON) {
                Vector3d landingCenter = new Vector3d(flightEnd)
                    .fma(parameters.landingLength() / 2.0d, run)
                    .fma(parameters.stepRise() / 2.0d, up)
                    .fma(turnDirection * (parameters.width() + turnGap) / 2.0d, side);
                PlacedSolid landing = orientedBoxExtents(
                    landingCenter, run, up, side,
                    new Vector3d(parameters.landingLength() / 2.0d, parameters.stepRise() / 2.0d,
                        (parameters.width() + turnGap) / 2.0d));
                if (landing == null) {
                    return null;
                }
                landings.add(landing);
                walk.add(new Vector3d(landingCenter).fma(parameters.stepRise() / 2.0d, up));
                landingCount++;
            }
            origin = new Vector3d(flightEnd)
                .fma(parameters.landingLength(), run)
                .add(new Vector3d(side).mul(turnDirection * (parameters.width() + turnGap)));
            run = new Vector3d(run).negate();
        }
        return toBuild(steps, landings, walk,
            parameters.stepRise() * parameters.stepCount(),
            parameters.stepRun() * parameters.stepCount() + parameters.landingLength() * landingCount);
    }

    private static List<Integer> splitFlights(int stepCount, int perFlight) {
        List<Integer> flights = new ArrayList<>();
        int remaining = stepCount;
        while (remaining > 0) {
            int n = Math.min(perFlight, remaining);
            if (remaining - n == 0 || remaining - n >= 1) {
                flights.add(n);
                remaining -= n;
            } else {
                flights.add(remaining);
                remaining = 0;
            }
        }
        return flights;
    }

    private boolean addStraightFlight(
        List<PlacedSolid> steps,
        List<Vector3d> walk,
        Vector3d start,
        Vector3d run,
        Vector3d up,
        Vector3d side,
        int globalStartIndex,
        int count,
        StairParameters parameters
    ) {
        for (int index = 0; index < count; index++) {
            Vector3d center = new Vector3d(start)
                .fma(parameters.stepRun() * index + parameters.stepRun() / 2.0d, run)
                .fma(parameters.stepRise() * (globalStartIndex + index) + parameters.stepRise() / 2.0d, up);
            PlacedSolid step = orientedStep(center, run, up, side, parameters);
            if (step == null) {
                return false;
            }
            steps.add(step);
            walk.add(new Vector3d(center).fma(parameters.stepRise() / 2.0d, up));
        }
        return true;
    }

    private StairBuild buildSpiralStairs(StairParameters parameters, SpiralParameters spiral) {
        if (parameters.stepCount() < 2) {
            return null;
        }
        List<PlacedSolid> steps = new ArrayList<>(parameters.stepCount());
        List<Vector3d> walk = new ArrayList<>();
        walk.add(new Vector3d(spiral.axisBase()));
        for (int index = 0; index < parameters.stepCount(); index++) {
            double startAngle = spiral.startAngle() + spiral.rotationSign() * spiral.anglePerStep() * index;
            double endAngle = startAngle + spiral.rotationSign() * spiral.anglePerStep();
            Vector3d origin = new Vector3d(spiral.axisBase())
                .fma(spiral.risePerStep() * index, spiral.upAxis());
            GeometryData tread = annularSectorTread(origin, spiral, startAngle, endAngle, parameters.stepRise());
            if (tread == null) {
                return null;
            }
            double midAngle = startAngle + spiral.rotationSign() * spiral.anglePerStep() * 0.5d;
            Vector3d radial = spiralRadial(spiral, midAngle);
            Vector3d center = new Vector3d(origin)
                .fma(spiral.risePerStep() * 0.5d, spiral.upAxis())
                .fma(spiral.radius(), radial);
            Vector3d tangent = VectorUtils.safeCross(spiral.upAxis(), radial);
            tangent = VectorUtils.safeNormalize(tangent);
            if (tangent == null) {
                return null;
            }
            tangent.mul(spiral.rotationSign());
            Vector3d side = VectorUtils.safeNormalize(radial);
            if (side == null) {
                return null;
            }
            FrameData stepFrame = FrameData.orthonormal(center, tangent, spiral.upAxis(), side);
            if (stepFrame == null) {
                stepFrame = FrameData.orthonormal(center, tangent, spiral.upAxis(), null);
            }
            if (stepFrame == null) {
                return null;
            }
            steps.add(new PlacedSolid(tread, stepFrame, new PointData(center)));
            walk.add(new Vector3d(center).fma(parameters.stepRise() / 2.0d, spiral.upAxis()));
        }
        double totalAngle = Math.abs(spiral.anglePerStep()) * parameters.stepCount();
        double totalRun = spiral.radius() * totalAngle;
        return toBuild(steps, List.of(), walk, spiral.risePerStep() * parameters.stepCount(), totalRun);
    }

    private @Nullable GeometryData annularSectorTread(
        Vector3d origin,
        SpiralParameters spiral,
        double startAngle,
        double endAngle,
        double rise
    ) {
        int segments = SPIRAL_ARC_SEGMENTS;
        boolean solidPie = spiral.innerRadius() <= EPSILON;
        int vertexCount = solidPie
            ? ProfileConstructionUtils.uniqueSectorVertices(segments)
            : ProfileConstructionUtils.uniqueAnnularSectorVertices(segments);
        if (!ProfileConstructionUtils.requireUniqueVertices(vertexCount)) {
            return null;
        }
        List<Vector3d> points = new ArrayList<>(vertexCount + 1);
        if (solidPie) {
            points.add(new Vector3d(origin));
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                double a = startAngle + (endAngle - startAngle) * t;
                points.add(worldOnSpiral(origin, spiral, spiral.outerRadius(), a));
            }
            points.add(new Vector3d(origin));
        } else {
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                double a = startAngle + (endAngle - startAngle) * t;
                points.add(worldOnSpiral(origin, spiral, spiral.outerRadius(), a));
            }
            for (int i = segments; i >= 0; i--) {
                double t = i / (double) segments;
                double a = startAngle + (endAngle - startAngle) * t;
                points.add(worldOnSpiral(origin, spiral, spiral.innerRadius(), a));
            }
            points.add(new Vector3d(points.getFirst()));
        }
        PlaneData plane = PlaneData.canonical(origin, spiral.upAxis());
        if (plane == null) {
            return null;
        }
        PolygonProfileData profile = tryClosedProfile(points, plane);
        if (profile == null) {
            return null;
        }
        Vector3d extrusion = VectorUtils.safeScale(spiral.upAxis(), rise);
        if (extrusion == null) {
            return null;
        }
        try {
            return new PrismGeometryData(profile.getUniquePoints(), extrusion);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static @Nullable PolygonProfileData tryClosedProfile(List<Vector3d> points, PlaneData plane) {
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(points, plane, null);
        if (profile != null) {
            return profile;
        }
        List<Vector3d> reversed = new ArrayList<>(points.size());
        for (int i = points.size() - 1; i >= 0; i--) {
            reversed.add(new Vector3d(points.get(i)));
        }
        return ProfileConstructionUtils.tryCreateProfile(reversed, plane, null);
    }

    private Vector3d worldOnSpiral(Vector3d origin, SpiralParameters spiral, double radius, double angle) {
        Vector3d radial = spiralRadial(spiral, angle);
        return new Vector3d(origin).fma(radius, radial);
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
        Double startAngleDeg = ArchitecturalInputUtils.resolveOptionalFiniteDouble(
            this, INPUT_SPIRAL_START_ANGLE_ID, 0.0d);
        if (startAngleDeg == null) {
            writeInvalid("Spiral Start Angle must be a finite DOUBLE");
            return null;
        }

        double rotationSign = "left".equals(turn) ? 1.0d : -1.0d;
        Vector3d upAxis = new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d entryTangent = resolveSpiralEntryTangent(frame.runAxis(), upAxis);
        Vector3d radialBasis = VectorUtils.safeCross(entryTangent, upAxis);
        if (radialBasis != null) {
            radialBasis.mul(rotationSign);
        }
        radialBasis = VectorUtils.safeNormalize(radialBasis);
        if (radialBasis == null) {
            radialBasis = new Vector3d(1.0d, 0.0d, 0.0d);
        }
        Vector3d tangentBasis = VectorUtils.safeNormalize(VectorUtils.safeCross(upAxis, radialBasis));
        if (tangentBasis == null) {
            tangentBasis = new Vector3d(0.0d, 0.0d, 1.0d);
        }
        double centerlineRadius = Math.max(spiralRadius, coreRadius + parameters.width() * 0.5d);
        double inner = coreRadius;
        double outer = centerlineRadius + parameters.width() * 0.5d;
        if (!(outer > inner + EPSILON) || !ArchitecturalNodeOutputs.allFinite(
            spiralHeight / parameters.stepCount(),
            2.0d * Math.PI * spiralTurns / parameters.stepCount(),
            inner, outer, centerlineRadius
        )) {
            writeInvalid("Spiral tread radii or derived angles are non-finite");
            return null;
        }
        return new SpiralParameters(
            centerlineRadius,
            inner,
            outer,
            spiralHeight / parameters.stepCount(),
            2.0d * Math.PI * spiralTurns / parameters.stepCount(),
            Math.toRadians(TrigMathOps.normalizeDegrees360(startAngleDeg)),
            rotationSign,
            new Vector3d(frame.start()),
            upAxis,
            radialBasis,
            tangentBasis
        );
    }

    private @Nullable PlacedSolid orientedStep(
        Vector3d center,
        Vector3d run,
        Vector3d up,
        Vector3d side,
        StairParameters parameters
    ) {
        return orientedBox(center, run, up, side, parameters.stepRun(), parameters.stepRise(), parameters.width());
    }

    private @Nullable PlacedSolid orientedBox(
        Vector3d center,
        Vector3d run,
        Vector3d up,
        Vector3d side,
        double length,
        double height,
        double width
    ) {
        return orientedBoxExtents(center, run, up, side, new Vector3d(length / 2.0d, height / 2.0d, width / 2.0d));
    }

    private @Nullable PlacedSolid orientedBoxExtents(
        Vector3d center,
        Vector3d run,
        Vector3d up,
        Vector3d side,
        Vector3d halfExtents
    ) {
        if (!VectorUtils.isFinite(center) || !ArchitecturalNodeOutputs.allFinite(halfExtents.x, halfExtents.y, halfExtents.z)) {
            return null;
        }
        Vector3d runN = VectorUtils.safeNormalize(run);
        Vector3d upN = VectorUtils.safeNormalize(up);
        Vector3d sideN = VectorUtils.safeNormalize(side);
        if (runN == null || upN == null || sideN == null) {
            return null;
        }
        GeometryData box = ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, runN, upN, sideN);
        FrameData frame = FrameData.orthonormal(center, runN, upN, sideN);
        if (box == null || frame == null) {
            return null;
        }
        return new PlacedSolid(box, frame, new PointData(center));
    }

    private Vector3d resolveSpiralEntryTangent(Vector3d runAxis, Vector3d upAxis) {
        Vector3d horizontal = VectorUtils.safeSubtract(runAxis, VectorUtils.safeScale(upAxis, VectorUtils.safeDot(runAxis, upAxis)));
        Vector3d normalized = VectorUtils.safeNormalize(horizontal);
        return normalized != null ? normalized : new Vector3d(1.0d, 0.0d, 0.0d);
    }

    private Vector3d spiralRadial(SpiralParameters spiral, double angle) {
        return new Vector3d(spiral.radialBasis()).mul(Math.cos(angle))
            .add(new Vector3d(spiral.tangentBasis()).mul(Math.sin(angle)));
    }

    private static StairBuild toBuild(
        List<PlacedSolid> steps,
        List<PlacedSolid> landings,
        List<Vector3d> walk,
        double totalRise,
        double totalRun
    ) {
        if (steps.isEmpty() || walk.size() < 2) {
            return null;
        }
        List<GeometryData> stepGeom = new ArrayList<>();
        List<FrameData> stepFrames = new ArrayList<>();
        List<PointData> stepCenters = new ArrayList<>();
        for (PlacedSolid step : steps) {
            stepGeom.add(step.geometry());
            stepFrames.add(step.frame());
            stepCenters.add(step.center());
        }
        List<GeometryData> landingGeom = new ArrayList<>();
        List<FrameData> landingFrames = new ArrayList<>();
        for (PlacedSolid landing : landings) {
            landingGeom.add(landing.geometry());
            landingFrames.add(landing.frame());
        }
        return new StairBuild(
            List.copyOf(stepGeom),
            List.copyOf(stepFrames),
            List.copyOf(stepCenters),
            List.copyOf(walk),
            List.copyOf(landingGeom),
            List.copyOf(landingFrames),
            totalRise,
            totalRun
        );
    }

    private static @Nullable PathData polylinePath(List<Vector3d> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        List<Vec3d> verts = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            verts.add(new Vec3d(point.x, point.y, point.z));
        }
        return PathData.fromPolyline(new PolylineData(verts));
    }

    private void writeInvalid(String error) {
        ArchitecturalNodeOutputs.putNull(outputValues,
            OUTPUT_GEOMETRY_ID, OUTPUT_WALK_PATH_ID, OUTPUT_BOTTOM_ID, OUTPUT_TOP_ID, OUTPUT_LANDING_GEOMETRY_ID);
        ArchitecturalNodeOutputs.putEmptyLists(outputValues, OUTPUT_STEP_FRAMES_ID, OUTPUT_STEP_CENTERS_ID, OUTPUT_LANDING_FRAMES_ID);
        ArchitecturalNodeOutputs.putNans(outputValues, OUTPUT_RISE_ID, OUTPUT_RUN_ID, OUTPUT_TOTAL_RISE_ID, OUTPUT_TOTAL_RUN_ID);
        ArchitecturalNodeOutputs.putCount(outputValues, OUTPUT_COUNT_ID, 0);
        ArchitecturalNodeOutputs.markInvalid(outputValues, error);
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
        double innerRadius,
        double outerRadius,
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

    private record PlacedSolid(GeometryData geometry, FrameData frame, PointData center) {
    }

    private record StairBuild(
        List<GeometryData> steps,
        List<FrameData> stepFrames,
        List<PointData> stepCenters,
        List<Vector3d> walk,
        List<GeometryData> landings,
        List<FrameData> landingFrames,
        double totalRise,
        double totalRun
    ) {
    }
}
