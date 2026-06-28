package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.helix",
    displayName = "Helix Curve",
    description = "Builds a sampled helix from center, axis, radius, pitch, turns, and segment count.",
    category = "geometry.curves",
    order = 17
)
public class HelixCurveNode extends AbstractCurveNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_PITCH_ID = "input_pitch";
    private static final String INPUT_TURNS_ID = "input_turns";
    private static final String INPUT_SEGMENTS_PER_TURN_ID = "input_segments_per_turn";
    private static final String INPUT_START_ANGLE_ID = "input_start_angle";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public HelixCurveNode() {
        super(UUID.randomUUID(), "geometry.curves.helix");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Helix base center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Helix axis direction", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Helix radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PITCH_ID, "Pitch", "Vertical advance per turn", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TURNS_ID, "Turns", "Number of turns", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_PER_TURN_ID, "Segments Per Turn", "Sampling density per turn", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Initial angle in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve", "Sampled helix as curve", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Sampled helix polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Helix sample points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Approximate polyline length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when helix inputs are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveInputPoint(inputValues.get(INPUT_CENTER_ID));
        Vector3d axisIn = resolveInputPoint(inputValues.get(INPUT_AXIS_ID));
        if (center == null || axisIn == null) {
            writeInvalid();
            return;
        }

        Vector3d axis = new Vector3d(axisIn);
        if (axis.lengthSquared() <= 1.0e-12d) {
            writeInvalid();
            return;
        }
        axis.normalize();

        double radius = readDoubleInput(INPUT_RADIUS_ID, Double.NaN);
        double pitch = readDoubleInput(INPUT_PITCH_ID, Double.NaN);
        double turns = readDoubleInput(INPUT_TURNS_ID, Double.NaN);
        int segmentsPerTurn = Math.max(6, readIntInput(INPUT_SEGMENTS_PER_TURN_ID, 32));
        double startAngle = Math.toRadians(readDoubleInput(INPUT_START_ANGLE_ID, 0.0d));
        if (!Double.isFinite(radius) || radius <= 0.0d || !Double.isFinite(pitch) || !Double.isFinite(turns) || turns <= 0.0d) {
            writeInvalid();
            return;
        }

        Vector3d basisU = fallbackAxis(axis);
        Vector3d basisV = new Vector3d(axis).cross(basisU).normalize();
        basisU = new Vector3d(basisV).cross(axis).normalize();

        int totalSegments = Math.max(2, (int) Math.ceil(turns * segmentsPerTurn));
        List<Vec3d> pts = new ArrayList<>(totalSegments + 1);
        for (int i = 0; i <= totalSegments; i++) {
            double t = i / (double) totalSegments;
            double angle = startAngle + t * turns * Math.PI * 2.0d;
            double along = t * turns * pitch;
            Vector3d p = new Vector3d(center)
                .add(new Vector3d(axis).mul(along))
                .add(new Vector3d(basisU).mul(Math.cos(angle) * radius))
                .add(new Vector3d(basisV).mul(Math.sin(angle) * radius));
            pts.add(new Vec3d(p.x, p.y, p.z));
        }

        double length = 0.0d;
        for (int i = 1; i < pts.size(); i++) {
            length += pts.get(i - 1).distanceTo(pts.get(i));
        }

        Curve curve = buildLinearCurve(pts);
        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, new PolylineData(pts));
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(pts));
        outputValues.put(OUTPUT_LENGTH_ID, length);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        writeInvalidOutputs();
        putDoubleOutputs(0.0d, OUTPUT_LENGTH_ID);
    }

    private Vector3d fallbackAxis(Vector3d axis) {
        Vector3d reference = Math.abs(axis.y) < 0.99d ? new Vector3d(0.0d, 1.0d, 0.0d) : new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d u = reference.sub(new Vector3d(axis).mul(reference.dot(axis)));
        if (u.lengthSquared() <= 1.0e-12d) {
            reference = new Vector3d(0.0d, 0.0d, 1.0d);
            u = reference.sub(new Vector3d(axis).mul(reference.dot(axis)));
        }
        return u.normalize();
    }

}
