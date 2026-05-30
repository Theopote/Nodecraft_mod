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
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates a straight staircase from a line segment.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.staircase",
    displayName = "Staircase",
    description = "Generates a straight staircase from a line segment",
    category = "geometry.architectural_primitives",
    order = 4
)
public class StaircaseNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_LAYOUT_ID = "input_layout";
    private static final String INPUT_STEP_COUNT_ID = "input_step_count";
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

        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Straight path used for the staircase run", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_LAYOUT_ID, "Layout", "Stair layout: straight, u, or spiral", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_STEP_COUNT_ID, "Step Count", "Number of steps to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_STEP_RUN_ID, "Step Run", "Horizontal run of each step", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEP_RISE_ID, "Step Rise", "Vertical rise of each step", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Stair width measured across the run", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LANDING_LENGTH_ID, "Landing Length", "Optional top landing length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TURN_GAP_ID, "Turn Gap", "Clear gap between U-shaped flights", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TURN_DIRECTION_ID, "Turn Direction", "U-shape turn direction: left, right, or auto", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_SPIRAL_RADIUS_ID, "Spiral Radius", "Radius from spiral axis to the step centerline", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_CORE_RADIUS_ID, "Spiral Core Radius", "Inner void radius used by the spiral layout", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_TURNS_ID, "Spiral Turns", "Number of turns for the spiral layout", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_HEIGHT_ID, "Spiral Height", "Total height for the spiral layout", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPIRAL_START_ANGLE_ID, "Spiral Start Angle", "Start angle in degrees for the spiral layout", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the staircase steps", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of step solids created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid staircase could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates straight, U-shaped, or spiral staircases from a path line";
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
                String layout = resolveLayout(inputValues.get(INPUT_LAYOUT_ID));
                int stepCount = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_STEP_COUNT_ID), 1);
                double stepRun = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_STEP_RUN_ID), 1.0d);
                double stepRise = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_STEP_RISE_ID), 0.2d);
                double width = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_WIDTH_ID), 1.0d);
                double landingLength = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_LANDING_LENGTH_ID), 0.0d);

                List<GeometryData> steps = switch (layout) {
                    case "u" -> buildUStairs(frame, stepCount, stepRun, stepRise, width, landingLength);
                    case "spiral" -> buildSpiralStairs(frame, stepCount, stepRun, stepRise, width);
                    default -> buildStraightStairs(frame, stepCount, stepRun, stepRise, width, landingLength);
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

    private List<GeometryData> buildUStairs(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        int stepCount,
        double stepRun,
        double stepRise,
        double width,
        double landingLength
    ) {
        int firstFlightCount = Math.max(1, (stepCount + 1) / 2);
        int secondFlightCount = Math.max(1, stepCount - firstFlightCount);
        double turnGap = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_TURN_GAP_ID), 0.0d);
        double turnDirection = resolveTurnDirection(inputValues.get(INPUT_TURN_DIRECTION_ID));
        Vector3d sideOffset = new Vector3d(frame.sideAxis()).mul(turnDirection * (width + turnGap));

        List<GeometryData> results = new ArrayList<>(stepCount + 1);
        results.addAll(buildStraightStairs(frame, firstFlightCount, stepRun, stepRise, width, 0.0d));

        Vector3d landingCenter = new Vector3d(frame.start())
            .fma(stepRun * firstFlightCount + landingLength / 2.0d, frame.runAxis())
            .fma(stepRise * firstFlightCount + stepRise / 2.0d, frame.upAxis())
            .fma((width + turnGap) / 2.0d * turnDirection, frame.sideAxis());
        Vector3d landingHalfExtents = new Vector3d(landingLength / 2.0d, stepRise / 2.0d, (width + turnGap) / 2.0d);
        results.add(ArchitecturalPrimitiveSupport.createOrientedBox(landingCenter, landingHalfExtents, frame.runAxis(), frame.upAxis(), frame.sideAxis()));

        Vector3d secondFlightStart = new Vector3d(frame.start())
            .fma(stepRun * firstFlightCount + landingLength, frame.runAxis())
            .fma(stepRise * firstFlightCount, frame.upAxis())
            .add(sideOffset);

        for (int index = 0; index < secondFlightCount; index++) {
            Vector3d center = new Vector3d(secondFlightStart)
                .fma(-(stepRun * index + stepRun / 2.0d), frame.runAxis())
                .fma(stepRise * index + stepRise / 2.0d, frame.upAxis());
            Vector3d halfExtents = new Vector3d(stepRun / 2.0d, stepRise / 2.0d, width / 2.0d);
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, new Vector3d(frame.runAxis()).negate(), frame.upAxis(), frame.sideAxis()));
        }

        return List.copyOf(results);
    }

    private List<GeometryData> buildSpiralStairs(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        int stepCount,
        double stepRun,
        double stepRise,
        double width
    ) {
        if (stepCount < 2) {
            return List.of();
        }

        double spiralHeight = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_SPIRAL_HEIGHT_ID), frame.length() > EPSILON ? frame.length() : stepRise * stepCount);
        double spiralTurns = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_SPIRAL_TURNS_ID), 1.0d);
        double spiralRadius = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_SPIRAL_RADIUS_ID), Math.max(width, stepRun));
        double coreRadius = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_SPIRAL_CORE_RADIUS_ID), Math.max(0.0d, spiralRadius - width));
        double startAngle = Math.toRadians(ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_SPIRAL_START_ANGLE_ID), 0.0d));
        double direction = resolveTurnDirection(inputValues.get(INPUT_TURN_DIRECTION_ID));

        double radius = Math.max(spiralRadius, coreRadius + width * 0.5d);
        double risePerStep = spiralHeight / stepCount;
        double angleRate = direction * 2.0d * Math.PI * spiralTurns / stepCount;
        Vector3d axis = new Vector3d(frame.runAxis()).normalize();
        List<GeometryData> results = new ArrayList<>(stepCount);

        for (int index = 0; index < stepCount; index++) {
            double t = (index + 0.5d) / stepCount;
            double angle = startAngle + angleRate * (index + 0.5d);
            Vector3d radial = spiralRadial(frame, angle);
            Vector3d center = new Vector3d(frame.start())
                .fma(risePerStep * (index + 0.5d), axis)
                .fma(radius, radial);

            Vector3d tangent = new Vector3d(axis).mul(risePerStep);
            Vector3d radialDerivative = spiralTangentRadial(frame, angle, direction).mul(radius * angleRate);
            tangent.add(radialDerivative);
            if (tangent.lengthSquared() <= EPSILON) {
                continue;
            }
            tangent.normalize();

            Vector3d up = new Vector3d(axis);
            Vector3d side = new Vector3d(radial).normalize();
            Vector3d halfExtents = new Vector3d(stepRun / 2.0d, stepRise / 2.0d, width / 2.0d);
            Matrix3d orientation = new Matrix3d(
                tangent.x, up.x, side.x,
                tangent.y, up.y, side.y,
                tangent.z, up.z, side.z
            );
            results.add(new BoxGeometryData(center, halfExtents, orientation, true));
        }

        return List.copyOf(results);
    }

    private Vector3d spiralRadial(ArchitecturalPrimitiveSupport.LineFrame frame, double angle) {
        Vector3d side = new Vector3d(frame.sideAxis()).normalize();
        Vector3d up = new Vector3d(frame.upAxis()).normalize();
        return new Vector3d(side).mul(Math.cos(angle)).add(new Vector3d(up).mul(Math.sin(angle)));
    }

    private Vector3d spiralTangentRadial(ArchitecturalPrimitiveSupport.LineFrame frame, double angle, double direction) {
        Vector3d side = new Vector3d(frame.sideAxis()).normalize();
        Vector3d up = new Vector3d(frame.upAxis()).normalize();
        return new Vector3d(side).mul(-Math.sin(angle) * direction).add(new Vector3d(up).mul(Math.cos(angle) * direction));
    }

    private String resolveLayout(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim().toLowerCase(Locale.ROOT);
        }
        return "straight";
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
}