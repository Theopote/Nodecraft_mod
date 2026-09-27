package com.nodecraft.nodesystem.nodes.pattern.lsystem;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.LSystemTurtle3DInterpreter;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
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
    order = 2
)
public class LSystemTurtle3DNode extends BaseNode {

    private static final Vector3d DEFAULT_ORIGIN = new Vector3d();

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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public LSystemTurtle3DNode() {
        super(UUID.randomUUID(), "pattern.lsystem.turtle_3d");

        addInputPort(new BasePort(INPUT_COMMANDS_ID, "Commands", "Command string (e.g. expanded L-system)", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Forward step length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Turn angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Optional start point", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_PATHS_ID, "Paths", "One line path per draw segment", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Draw endpoints from segment runs (not one polyline)", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SEGMENT_COUNT_ID, "Segment Count", "Number of drawn segments", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when interpretation succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when command length, segment cap, or stack depth was reached", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
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

        Double st = OptionalPortDrive.resolveOptionalDouble(this, INPUT_STEP_ID, step);
        if (st == null) {
            writeInvalid(false, "Step connected but invalid");
            return;
        }
        if (!Double.isFinite(st) || st <= 0.0d) {
            writeInvalid(false, "Step must be finite and > 0");
            return;
        }

        Double angDeg = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ANGLE_ID, angleDegrees);
        if (angDeg == null) {
            writeInvalid(false, "Angle connected but invalid");
            return;
        }
        if (!Double.isFinite(angDeg)) {
            writeInvalid(false, "Angle must be finite");
            return;
        }

        Vector3d origin = OptionalPortDrive.resolveOptionalPoint(this, INPUT_ORIGIN_ID, DEFAULT_ORIGIN);
        if (origin == null) {
            writeInvalid(false, "Origin connected but invalid");
            return;
        }

        LSystemTurtle3DInterpreter.TurtleResult result = LSystemTurtle3DInterpreter.interpret(
                commands,
                origin,
                st,
                angDeg,
                GenerationLimits.MAX_LSYSTEM_COMMAND_LENGTH,
                GenerationLimits.maxLSystemTurtleSegments(),
                GenerationLimits.MAX_LSYSTEM_TURTLE_STACK_DEPTH
        );

        if (!result.valid()) {
            writeInvalid(result.hitLimit(), result.error());
            return;
        }

        outputValues.put(OUTPUT_PATHS_ID, result.paths());
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(result.drawPoints()));
        outputValues.put(OUTPUT_SEGMENT_COUNT_ID, result.segmentCount());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, result.hitLimit());
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(boolean hitLimit, String error) {
        outputValues.put(OUTPUT_PATHS_ID, List.of());
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SEGMENT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
