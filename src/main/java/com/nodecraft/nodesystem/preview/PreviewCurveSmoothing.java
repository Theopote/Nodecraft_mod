package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.CurveMathUtils;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Reshapes preview path geometry according to the "Smooth Curves" option.
 *
 * <p>Linear mode keeps the control points untouched (only the closing seam point is appended
 * for closed paths). Smooth mode replaces the hard corners with a centripetal Catmull-Rom
 * spline so the preview reads as one continuous curve.
 */
public final class PreviewCurveSmoothing {

    /** Samples generated per polyline segment when smoothing is enabled. */
    public static final int DEFAULT_SAMPLES_PER_SEGMENT = 8;
    /** Centripetal Catmull-Rom parameterization exponent. */
    public static final double DEFAULT_ALPHA = 0.5d;
    /** Numeric tolerance used by the spline sampler. */
    public static final double DEFAULT_EPSILON = 1.0e-8d;

    private PreviewCurveSmoothing() {
    }

    /**
     * Builds the point list that should actually be rendered.
     *
     * @param controlPoints source control points (may repeat the first point at the end)
     * @param closed        whether the path is closed
     * @param smooth        whether corner smoothing is enabled
     */
    public static List<Vec3d> buildRenderPoints(List<Vec3d> controlPoints, boolean closed, boolean smooth) {
        return buildRenderPoints(controlPoints, closed, smooth,
            DEFAULT_SAMPLES_PER_SEGMENT, DEFAULT_ALPHA, DEFAULT_EPSILON);
    }

    /**
     * Builds the point list that should actually be rendered.
     *
     * @param controlPoints      source control points (may repeat the first point at the end)
     * @param closed             whether the path is closed
     * @param smooth             whether corner smoothing is enabled
     * @param samplesPerSegment  spline samples generated per control segment
     * @param alpha              Catmull-Rom parameterization exponent (0.5 = centripetal)
     * @param epsilon            numeric tolerance used by the spline sampler
     */
    public static List<Vec3d> buildRenderPoints(List<Vec3d> controlPoints, boolean closed, boolean smooth,
                                                 int samplesPerSegment, double alpha, double epsilon) {
        if (controlPoints == null || controlPoints.isEmpty()) {
            return List.of();
        }

        // A closed polyline may repeat its first point at the end; work on the unique control
        // points and re-close explicitly afterwards.
        List<Vec3d> core = controlPoints;
        boolean isClosed = closed;
        if (controlPoints.size() > 1 && controlPoints.getFirst().equals(controlPoints.getLast())) {
            core = new ArrayList<>(controlPoints.subList(0, controlPoints.size() - 1));
            isClosed = true;
        }

        if (!smooth || core.size() < 3) {
            if (isClosed && !core.isEmpty()) {
                List<Vec3d> linear = new ArrayList<>(core);
                linear.add(core.getFirst());
                return linear;
            }
            return core;
        }

        // sampleCatmullRom handles the closed wrap-around itself and emits the first point last,
        // so the loop closes without an extra duplicated vertex.
        List<Vec3d> smoothed = CurveMathUtils.sampleCatmullRom(
            core,
            Math.max(1, samplesPerSegment),
            alpha,
            isClosed,
            epsilon
        );
        if (smoothed.size() < 2) {
            return core;
        }
        return smoothed;
    }
}
