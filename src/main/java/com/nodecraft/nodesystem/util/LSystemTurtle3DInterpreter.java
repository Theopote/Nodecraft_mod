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
 */
public final class LSystemTurtle3DInterpreter {

    private static final Vector3d LOCAL_FORWARD = new Vector3d(0.0d, 0.0d, 1.0d);

    private LSystemTurtle3DInterpreter() {
    }

    public record TurtleResult(
            List<PathData> paths,
            List<Vector3d> drawPoints,
            int segmentCount,
            boolean hitLimit,
            boolean bracketError
    ) {
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
            return emptyResult(true, false);
        }
        if (!Double.isFinite(step) || step <= 0.0d || !Double.isFinite(angleDegrees)) {
            return emptyResult(false, true);
        }
        if (origin == null || !isFinite(origin)) {
            return emptyResult(false, true);
        }

        double angRad = Math.toRadians(angleDegrees);
        Vector3d pos = new Vector3d(origin);
        Quaterniond orientation = new Quaterniond();

        List<PathData> paths = new ArrayList<>();
        List<Vector3d> drawPoints = new ArrayList<>();
        Deque<TurtleState> stack = new ArrayDeque<>();
        boolean hitLimit = false;
        boolean bracketError = false;

        for (int i = 0; i < commands.length(); i++) {
            char command = commands.charAt(i);
            switch (command) {
                case 'F' -> {
                    if (paths.size() >= maxSegments) {
                        hitLimit = true;
                        i = commands.length();
                        break;
                    }
                    Vector3d before = new Vector3d(pos);
                    moveForward(pos, orientation, step);
                    paths.add(segmentPath(before, pos));
                    drawPoints.add(new Vector3d(before));
                    drawPoints.add(new Vector3d(pos));
                }
                case 'f' -> moveForward(pos, orientation, step);
                case '+' -> orientation.rotateLocalY(angRad);
                case '-' -> orientation.rotateLocalY(-angRad);
                case '&' -> orientation.rotateLocalX(angRad);
                case '^' -> orientation.rotateLocalX(-angRad);
                case '/' -> orientation.rotateLocalZ(-angRad);
                case '\\' -> orientation.rotateLocalZ(angRad);
                case '[' -> {
                    if (stack.size() >= maxStackDepth) {
                        hitLimit = true;
                        i = commands.length();
                        break;
                    }
                    stack.push(new TurtleState(new Vector3d(pos), new Quaterniond(orientation)));
                }
                case ']' -> {
                    TurtleState restored = stack.poll();
                    if (restored == null) {
                        bracketError = true;
                        i = commands.length();
                    } else {
                        pos.set(restored.position);
                        orientation.set(restored.orientation);
                    }
                }
                default -> {
                    // ignore other symbols (e.g. leaves)
                }
            }
        }

        if (!stack.isEmpty()) {
            bracketError = true;
        }

        return new TurtleResult(paths, drawPoints, paths.size(), hitLimit, bracketError);
    }

    private static TurtleResult emptyResult(boolean hitLimit, boolean bracketError) {
        return new TurtleResult(List.of(), List.of(), 0, hitLimit, bracketError);
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

    private static boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private record TurtleState(Vector3d position, Quaterniond orientation) {
    }
}
