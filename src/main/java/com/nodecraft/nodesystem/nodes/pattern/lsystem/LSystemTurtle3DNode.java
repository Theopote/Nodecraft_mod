package com.nodecraft.nodesystem.nodes.pattern.lsystem;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.LSystemTurtle3DInterpreter;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.lsystem.turtle_3d",
    displayName = "L-System Turtle 3D",
    description = "Interprets L-system commands as independent 3D draw segments (PATH_LIST). F draws, f moves without drawing, +- yaw, &/^ pitch, / \\ roll, [] stack",
    category = "pattern.lsystem",
    order = 3
)
public class LSystemTurtle3DNode extends BaseNode {

    /** @deprecated Use {@link GenerationLimits#MAX_LSYSTEM_COMMAND_LENGTH}. */
    @Deprecated
    public static final int MAX_COMMAND_LENGTH = GenerationLimits.MAX_LSYSTEM_COMMAND_LENGTH;

    /** @deprecated Use {@link GenerationLimits#MAX_LSYSTEM_TURTLE_SEGMENTS}. */
    @Deprecated
    public static final int MAX_POLYLINE_POINTS = GenerationLimits.MAX_LSYSTEM_TURTLE_SEGMENTS;

    @NodeProperty(displayName = "Step", category = "Turtle", order = 1)
    private double step = 1.0d;

    @NodeProperty(displayName = "Angle", category = "Turtle", order = 2,
        description = "Turn amount in degrees for +/-/&/^/\\/ ")
    private double angleDegrees = 25.0d;

    private static final String INPUT_COMMANDS_ID = "input_commands";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_ANGLE_ID = "input_angle";
    private static final String INPUT_ORIGIN_ID = "input_origin";

    private static final String OUTPUT_PATHS_ID = "output_paths";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_SEGMENT_COUNT_ID = "output_segment_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";

    public LSystemTurtle3DNode() {
        super(UUID.randomUUID(), "pattern.lsystem.turtle_3d");

        addInputPort(new BasePort(INPUT_COMMANDS_ID, "Commands", "Command string (e.g. expanded L-system)", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Forward step length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Turn angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Optional start point", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_PATHS_ID, "Paths", "One line path per draw segment", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Draw endpoints from segment runs (not one polyline)", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SEGMENT_COUNT_ID, "Segment Count", "Number of drawn segments", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least one segment was emitted without bracket errors", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when command length, segment cap, or stack depth was reached", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Interprets L-system commands as independent 3D draw segments (PATH_LIST). F draws, f moves without drawing, +- yaw, &/^ pitch, / \\ roll, [] stack";
    }

    @Override
    public String getDisplayName() {
        return "L-System Turtle 3D";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String commands = inputValues.get(INPUT_COMMANDS_ID) instanceof String s ? s : "";
        double st = readDouble(inputValues.get(INPUT_STEP_ID), step);
        double angDeg = readDouble(inputValues.get(INPUT_ANGLE_ID), angleDegrees);
        Vector3d origin = resolveOrigin(inputValues.get(INPUT_ORIGIN_ID));

        if (origin == null) {
            writeInvalid(false);
            return;
        }

        LSystemTurtle3DInterpreter.TurtleResult result = LSystemTurtle3DInterpreter.interpret(
                commands,
                origin,
                st,
                angDeg,
                GenerationLimits.MAX_LSYSTEM_COMMAND_LENGTH,
                GenerationLimits.MAX_LSYSTEM_TURTLE_SEGMENTS,
                GenerationLimits.MAX_LSYSTEM_TURTLE_STACK_DEPTH
        );

        boolean valid = result.segmentCount() > 0 && !result.bracketError();
        outputValues.put(OUTPUT_PATHS_ID, valid ? result.paths() : List.<PathData>of());
        outputValues.put(OUTPUT_POINTS_ID, valid ? SpatialValueResolver.toPointDataList(result.drawPoints()) : List.of());
        outputValues.put(OUTPUT_SEGMENT_COUNT_ID, result.segmentCount());
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, result.hitLimit());
    }

    private void writeInvalid(boolean hitLimit) {
        outputValues.put(OUTPUT_PATHS_ID, List.of());
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SEGMENT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
    }

    private static @Nullable Vector3d resolveOrigin(@Nullable Object value) {
        Vector3d resolved = SpatialValueResolver.resolvePoint(value);
        if (resolved == null) {
            return new Vector3d();
        }
        if (!Double.isFinite(resolved.x) || !Double.isFinite(resolved.y) || !Double.isFinite(resolved.z)) {
            return null;
        }
        return resolved;
    }

    private static double readDouble(@Nullable Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}
