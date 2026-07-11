package com.nodecraft.nodesystem.nodes.pattern.lsystem;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.LSystemStringExpander;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Interprets a command string with a simple 3D turtle: F/f move, +- yaw, &, ^ pitch, / and \\ roll, [] stack.
 */
@NodeInfo(
    id = "pattern.lsystem.turtle_3d",
    displayName = "L-System Turtle 3D",
    description = "Traces a 3D polyline from L-system commands: F/f forward, +- yaw, & and ^ pitch, / and \\ roll, [] stack (local turns; angle in degrees)",
    category = "pattern.lsystem",
    order = 2
)
public class LSystemTurtle3DNode extends BaseNode {

    public static final int MAX_COMMAND_LENGTH = LSystemStringExpander.DEFAULT_MAX_EXPANDED_LENGTH;
    public static final int MAX_POLYLINE_POINTS = LSystemStringExpander.DEFAULT_MAX_EXPANDED_LENGTH;

    private static final Vector3d LOCAL_FORWARD = new Vector3d(0.0d, 0.0d, 1.0d);

    @NodeProperty(displayName = "Step", category = "Turtle", order = 1)
    private double step = 1.0d;

    @NodeProperty(displayName = "Angle", category = "Turtle", order = 2,
        description = "Turn amount in degrees for +/-/&/^/\\/ ")
    private double angleDegrees = 25.0d;

    private static final String INPUT_COMMANDS_ID = "input_commands";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_ANGLE_ID = "input_angle";
    private static final String INPUT_ORIGIN_ID = "input_origin";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";

    public LSystemTurtle3DNode() {
        super(UUID.randomUUID(), "pattern.lsystem.turtle_3d");

        addInputPort(new BasePort(INPUT_COMMANDS_ID, "Commands", "Command string (e.g. expanded L-system)", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Forward step length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Turn angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Optional start point", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Polyline vertices in world space", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least one segment was emitted", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when command length or polyline point cap was reached", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Traces a 3D polyline from an L-system command string using local yaw/pitch/roll turns (+/-/&/^/\\/ ) and a bracket stack";
    }

    @Override
    public String getDisplayName() {
        return "L-System Turtle 3D";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object cmdObj = inputValues.get(INPUT_COMMANDS_ID);
        String commands = cmdObj instanceof String s ? s : "";
        if (commands.length() > MAX_COMMAND_LENGTH) {
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_HIT_LIMIT_ID, true);
            return;
        }

        double st = getInputDouble(INPUT_STEP_ID, step);
        st = Math.max(1.0e-6d, st);
        double angDeg = getInputDouble(INPUT_ANGLE_ID, angleDegrees);
        double angRad = Math.toRadians(angDeg);

        Vector3d pos = resolveOrigin(inputValues.get(INPUT_ORIGIN_ID));
        Quaterniond q = new Quaterniond();

        List<Vector3d> points = new ArrayList<>();
        points.add(new Vector3d(pos));

        Deque<TurtleState> stack = new ArrayDeque<>();
        boolean drew = false;
        boolean hitLimit = false;

        for (int i = 0; i < commands.length(); i++) {
            char c = commands.charAt(i);
            switch (c) {
                case 'F' -> {
                    if (points.size() >= MAX_POLYLINE_POINTS) {
                        hitLimit = true;
                        i = commands.length();
                        break;
                    }
                    moveForward(pos, q, st);
                    points.add(new Vector3d(pos));
                    drew = true;
                }
                case 'f' -> moveForward(pos, q, st);
                case '+' -> q.rotateLocalY(angRad);
                case '-' -> q.rotateLocalY(-angRad);
                case '&' -> q.rotateLocalX(angRad);
                case '^' -> q.rotateLocalX(-angRad);
                case '/' -> q.rotateLocalZ(-angRad);
                case '\\' -> q.rotateLocalZ(angRad);
                case '[' -> stack.push(new TurtleState(new Vector3d(pos), new Quaterniond(q)));
                case ']' -> {
                    TurtleState stt = stack.poll();
                    if (stt != null) {
                        pos.set(stt.position);
                        q.set(stt.orientation);
                    }
                }
                default -> {
                    // ignore other symbols (e.g. leaves X)
                }
            }
        }

        boolean valid = drew && points.size() >= 2;
        outputValues.put(OUTPUT_POINTS_ID, valid ? List.copyOf(points) : List.of());
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
    }

    private static void moveForward(Vector3d pos, Quaterniond q, double stepLen) {
        Vector3d dir = q.transform(new Vector3d(LOCAL_FORWARD), new Vector3d());
        pos.fma(stepLen, dir);
    }

    private static Vector3d resolveOrigin(Object value) {
        if (value instanceof PointData pd) {
            return new Vector3d(pd.getPosition());
        }
        if (value instanceof Vector3d v) {
            return new Vector3d(v);
        }
        if (value instanceof BlockPos bp) {
            return new Vector3d(bp.getX(), bp.getY(), bp.getZ());
        }
        return new Vector3d();
    }

    private double getInputDouble(String portId, double fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    private record TurtleState(Vector3d position, Quaterniond orientation) {
    }
}
