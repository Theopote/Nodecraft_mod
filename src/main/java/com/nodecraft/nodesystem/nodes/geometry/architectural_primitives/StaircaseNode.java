package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates architectural staircases from a line segment.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.staircase",
    displayName = "Staircase",
    description = "Generates architectural staircases from a line segment",
    category = "geometry.architectural_primitives",
    order = 4
)
public class StaircaseNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    private static final String INPUT_LINE_ID = "input_line";
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

    public StaircaseNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.staircase");

        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Straight path for straight runs; for spiral, start is the stair axis base and direction defines the entry tangent in plan", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_LAYOUT_ID, "Layout", "Stair layout: straight, u, double_run, switchback, or spiral", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_STEP_COUNT_ID, "Step Count", "Number of steps to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_FIRST_FLIGHT_STEPS_ID, "First Flight Steps", "Optional step count used before the landing in U/double-run layouts", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_STEP_RUN_ID, "Step Run", "Horizontal run of each step", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEP_RISE_ID, "Step Rise", "Vertical rise of each step", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Stair width measured across the run", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LANDING_LENGTH_ID, "Landing Length", "Optional top landing length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TURN_GAP_ID, "Turn Gap", "Clear gap between U-shaped flights", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TURN_DIRECTION_ID, "Turn Direction", "U-shape turn direction: left, right, or auto", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_SPIRAL_RADIUS_ID, "Spiral Radius", "Radius from the vertical stair axis to the tread centerline", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_CORE_RADIUS_ID, "Spiral Core Radius", "Inner void radius used by the spiral layout", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_TURNS_ID, "Spiral Turns", "Number of turns for the spiral layout", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_HEIGHT_ID, "Spiral Height", "Total vertical rise for the spiral stair", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_START_ANGLE_ID, "Spiral Start Angle", "Additional plan rotation in degrees applied to the spiral stair", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the staircase steps", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of step solids created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid staircase could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates straight, U-shaped, double-run, switchback, or vertical spiral staircases from a path line";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object lineObj = inputValues.get(INPUT_LINE_ID);

        GeometryData geometry = null;
        int count = 0;
        boolean valid = false;

        if (lineObj instanceof LineData line) {
            Vec3d startVec = line.getStart();
            Vec3d endVec = line.getEnd();
            ArchitecturalPrimitiveSupport.LineFrame frame = ArchitecturalPrimitiveSupport.resolveLineFrame(startVec, endVec);
            if (frame != null) {
                StairParameters parameters = resolveStairParameters();
                List<GeometryData> steps = switch (parameters.layout()) {
                    case "u", "double_run", "switchback" -> buildDoubleRunStairs(frame, parameters);
                    case "spiral" -> buildSpiralStairs(frame, parameters, resolveSpiralParameters(frame, parameters));
                    default -> buildStraightStairs(frame, parameters.stepCount(), parameters.stepRun(), parameters.stepRise(), parameters.width(), parameters.landingLength());
                };
                if (!steps.isEmpty()) {
                    geometry = new CompositeGeometryData(steps);
                    count = steps.size();
                    valid = true;
                }
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
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
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.runAxis(), frame.upAxis(), frame.sideAxis()));
        }

        if (landingLength > 0.0d) {
            Vector3d landingCenter = new Vector3d(frame.start())
                .fma(stepRun * stepCount + landingLength / 2.0d, frame.runAxis())
                .fma(stepRise * stepCount + stepRise / 2.0d, frame.upAxis());
            Vector3d halfExtents = new Vector3d(landingLength / 2.0d, stepRise / 2.0d, width / 2.0d);
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(landingCenter, halfExtents, frame.runAxis(), frame.upAxis(), frame.sideAxis()));
        }

        return List.copyOf(results);
    }

    private List<GeometryData> buildDoubleRunStairs(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        StairParameters parameters
    ) {
        int secondFlightCount = Math.max(1, parameters.stepCount() - parameters.firstFlightSteps());
        double turnGap = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_TURN_GAP_ID), 0.0d);
        double turnDirection = resolveTurnDirection(inputValues.get(INPUT_TURN_DIRECTION_ID));
        Vector3d sideOffset = new Vector3d(frame.sideAxis()).mul(turnDirection * (parameters.width() + turnGap));

        List<GeometryData> results = new ArrayList<>(parameters.stepCount() + 1);
        results.addAll(buildStraightStairs(frame, parameters.firstFlightSteps(), parameters.stepRun(), parameters.stepRise(), parameters.width(), 0.0d));

        if (parameters.landingLength() > EPSILON) {
            Vector3d landingCenter = new Vector3d(frame.start())
                .fma(parameters.stepRun() * parameters.firstFlightSteps() + parameters.landingLength() / 2.0d, frame.runAxis())
                .fma(parameters.stepRise() * parameters.firstFlightSteps() + parameters.stepRise() / 2.0d, frame.upAxis())
                .fma((parameters.width() + turnGap) / 2.0d * turnDirection, frame.sideAxis());
            Vector3d landingHalfExtents = new Vector3d(parameters.landingLength() / 2.0d, parameters.stepRise() / 2.0d, (parameters.width() + turnGap) / 2.0d);
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(landingCenter, landingHalfExtents, frame.runAxis(), frame.upAxis(), frame.sideAxis()));
        }

        Vector3d secondFlightStart = new Vector3d(frame.start())
            .fma(parameters.stepRun() * parameters.firstFlightSteps() + parameters.landingLength(), frame.runAxis())
            .fma(parameters.stepRise() * parameters.firstFlightSteps(), frame.upAxis())
            .add(sideOffset);

        for (int index = 0; index < secondFlightCount; index++) {
            Vector3d center = new Vector3d(secondFlightStart)
                .fma(-(parameters.stepRun() * index + parameters.stepRun() / 2.0d), frame.runAxis())
                .fma(parameters.stepRise() * index + parameters.stepRise() / 2.0d, frame.upAxis());
            results.add(createStepBox(center, new Vector3d(frame.runAxis()).negate(), frame.upAxis(), frame.sideAxis(), parameters.stepRun(), parameters.stepRise(), parameters.width()));
        }

        return List.copyOf(results);
    }

    private List<GeometryData> buildSpiralStairs(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        StairParameters parameters,
        SpiralParameters spiral
    ) {
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

            results.add(createStepBox(center, tangent, spiral.upAxis(), new Vector3d(radial).normalize(), parameters.stepRun(), parameters.stepRise(), parameters.width()));
        }

        return List.copyOf(results);
    }

    private StairParameters resolveStairParameters() {
        int stepCount = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_STEP_COUNT_ID), 1);
        return new StairParameters(
            resolveLayout(inputValues.get(INPUT_LAYOUT_ID)),
            stepCount,
            resolveFirstFlightSteps(inputValues.get(INPUT_FIRST_FLIGHT_STEPS_ID), stepCount),
            ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_STEP_RUN_ID), 1.0d),
            ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_STEP_RISE_ID), 0.2d),
            ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_WIDTH_ID), 1.0d),
            ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_LANDING_LENGTH_ID), 0.0d)
        );
    }

    private SpiralParameters resolveSpiralParameters(ArchitecturalPrimitiveSupport.LineFrame frame, StairParameters parameters) {
        double spiralHeight = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_SPIRAL_HEIGHT_ID), parameters.stepRise() * parameters.stepCount());
        double spiralTurns = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_SPIRAL_TURNS_ID), 1.0d);
        double spiralRadius = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_SPIRAL_RADIUS_ID), Math.max(parameters.width(), parameters.stepRun()));
        double coreRadius = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_SPIRAL_CORE_RADIUS_ID), Math.max(0.0d, spiralRadius - parameters.width()));
        double rotationSign = resolveSpiralRotationSign(inputValues.get(INPUT_TURN_DIRECTION_ID));
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
            Math.toRadians(ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_SPIRAL_START_ANGLE_ID), 0.0d)),
            rotationSign,
            new Vector3d(frame.start()),
            upAxis,
            radialBasis,
            tangentBasis
        );
    }

    private BoxGeometryData createStepBox(Vector3d center, Vector3d runAxis, Vector3d upAxis, Vector3d sideAxis, double stepRun, double stepRise, double width) {
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
        return new Vector3d(spiral.radialBasis()).mul(Math.cos(angle)).add(new Vector3d(spiral.tangentBasis()).mul(Math.sin(angle)));
    }

    private String resolveLayout(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim().toLowerCase(Locale.ROOT);
        }
        return "straight";
    }

    private int resolveFirstFlightSteps(Object value, int totalSteps) {
        if (totalSteps <= 1) {
            return 1;
        }
        if (value instanceof Number number) {
            return Math.max(1, Math.min(totalSteps - 1, number.intValue()));
        }
        return Math.max(1, Math.min(totalSteps - 1, (totalSteps + 1) / 2));
    }

    private double resolveTurnDirection(Object value) {
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("left") || normalized.contains("ccw")) {
                return -1.0d;
            }
        }
        return 1.0d;
    }

    private double resolveSpiralRotationSign(Object value) {
        return -resolveTurnDirection(value);
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