package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Interprets L-system turtle commands as independent draw segments ({@link PathData} lines).
 * Failures are transactional: no partial geometry is returned.
 */
public final class LSystemTurtle3DInterpreter {

    private static final Vector3d LOCAL_FORWARD = new Vector3d(0.0d, 0.0d, 1.0d);

    private LSystemTurtle3DInterpreter() {
    }

    public record TurtleResult(
            boolean valid,
            String error,
            List<PathData> paths,
            List<Vector3d> drawPoints,
            int segmentCount,
            boolean hitLimit
    ) {
        public static TurtleResult success(List<PathData> paths, List<Vector3d> drawPoints) {
            return new TurtleResult(true, "", List.copyOf(paths), List.copyOf(drawPoints), paths.size(), false);
        }

        public static TurtleResult failure(String error, boolean hitLimit) {
            return new TurtleResult(false, error == null ? "" : error, List.of(), List.of(), 0, hitLimit);
        }
    }

    public static TurtleResult interpret(
            String commands,
            Vector3d origin,
            double step,
            double angleDegrees,
            int maxCommandLength,
            int maxSegments,
            int maxStackDepth
    ) {
        if (commands == null) {
            commands = "";
        }
        if (commands.length() > maxCommandLength) {
            return TurtleResult.failure("Command length exceeds MAX_LSYSTEM_COMMAND_LENGTH", true);
        }
        if (!Double.isFinite(step) || step <= 0.0d) {
            return TurtleResult.failure("Step must be finite and > 0", false);
        }
        if (!Double.isFinite(angleDegrees)) {
            return TurtleResult.failure("Angle must be finite", false);
        }
        if (origin == null || !isFinite(origin)) {
            return TurtleResult.failure("Origin must be finite", false);
        }
        if (maxSegments <= 0) {
            return TurtleResult.failure("Segment budget must be positive", true);
        }

        double angRad = Math.toRadians(angleDegrees);
        if (!Double.isFinite(angRad)) {
            return TurtleResult.failure("Angle must be finite", false);
        }
        Vector3d pos = new Vector3d(origin);
        Quaterniond orientation = new Quaterniond();

        List<PathData> paths = new ArrayList<>();
        List<Vector3d> drawPoints = new ArrayList<>();
        Deque<TurtleState> stack = new ArrayDeque<>();

        for (int i = 0; i < commands.length(); i++) {
            char command = commands.charAt(i);
            switch (command) {
                case 'F' -> {
                    if (paths.size() >= maxSegments) {
                        return TurtleResult.failure("Turtle segment limit exceeded", true);
                    }
                    Vector3d before = new Vector3d(pos);
                    moveForward(pos, orientation, step);
                    if (!isFinite(pos) || !isFinite(orientation)) {
                        return TurtleResult.failure("Turtle state became non-finite", false);
                    }
                    paths.add(segmentPath(before, pos));
                    drawPoints.add(new Vector3d(before));
                    drawPoints.add(new Vector3d(pos));
                }
                case 'f' -> {
                    moveForward(pos, orientation, step);
                    if (!isFinite(pos) || !isFinite(orientation)) {
                        return TurtleResult.failure("Turtle state became non-finite", false);
                    }
                }
                case '+' -> {
                    if (!rotateLocalY(orientation, angRad)) {
                        return TurtleResult.failure("Turtle orientation became non-finite", false);
                    }
                }
                case '-' -> {
                    if (!rotateLocalY(orientation, -angRad)) {
                        return TurtleResult.failure("Turtle orientation became non-finite", false);
                    }
                }
                case '&' -> {
                    if (!rotateLocalX(orientation, angRad)) {
                        return TurtleResult.failure("Turtle orientation became non-finite", false);
                    }
                }
                case '^' -> {
                    if (!rotateLocalX(orientation, -angRad)) {
                        return TurtleResult.failure("Turtle orientation became non-finite", false);
                    }
                }
                case '/' -> {
                    if (!rotateLocalZ(orientation, -angRad)) {
                        return TurtleResult.failure("Turtle orientation became non-finite", false);
                    }
                }
                case '\\' -> {
                    if (!rotateLocalZ(orientation, angRad)) {
                        return TurtleResult.failure("Turtle orientation became non-finite", false);
                    }
                }
                case '[' -> {
                    if (stack.size() >= maxStackDepth) {
                        return TurtleResult.failure("Turtle stack depth limit exceeded", true);
                    }
                    stack.push(new TurtleState(new Vector3d(pos), new Quaterniond(orientation)));
                }
                case ']' -> {
                    TurtleState restored = stack.poll();
                    if (restored == null) {
                        return TurtleResult.failure("Unmatched closing bracket", false);
                    }
                    pos.set(restored.position);
                    orientation.set(restored.orientation);
                }
                default -> {
                    // ignore other symbols (e.g. leaves / grammar markers)
                }
            }
        }

        if (!stack.isEmpty()) {
            return TurtleResult.failure("Unclosed opening bracket", false);
        }

        return TurtleResult.success(paths, drawPoints);
    }

    private static PathData segmentPath(Vector3d before, Vector3d after) {
        return PathData.fromLine(new LineData(
                new Vec3d(before.x, before.y, before.z),
                new Vec3d(after.x, after.y, after.z)
        ));
    }

    private static void moveForward(Vector3d pos, Quaterniond orientation, double stepLen) {
        Vector3d direction = orientation.transform(new Vector3d(LOCAL_FORWARD), new Vector3d());
        pos.fma(stepLen, direction);
    }

    private static boolean rotateLocalX(Quaterniond orientation, double radians) {
        orientation.rotateLocalX(radians);
        orientation.normalize();
        return isFinite(orientation);
    }

    private static boolean rotateLocalY(Quaterniond orientation, double radians) {
        orientation.rotateLocalY(radians);
        orientation.normalize();
        return isFinite(orientation);
    }

    private static boolean rotateLocalZ(Quaterniond orientation, double radians) {
        orientation.rotateLocalZ(radians);
        orientation.normalize();
        return isFinite(orientation);
    }

    private static boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static boolean isFinite(Quaterniond quaternion) {
        return Double.isFinite(quaternion.x)
                && Double.isFinite(quaternion.y)
                && Double.isFinite(quaternion.z)
                && Double.isFinite(quaternion.w);
    }

    private record TurtleState(Vector3d position, Quaterniond orientation) {
    }
}
