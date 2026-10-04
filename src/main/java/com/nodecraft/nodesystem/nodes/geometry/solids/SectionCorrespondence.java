package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared Flip / Seam Offset / optional AUTO_SEAM cyclic matching for loft and morph.
 */
final class SectionCorrespondence {

    private SectionCorrespondence() {
    }

    static List<Vector3d> apply(
        List<Vector3d> section,
        boolean flip,
        int seamOffset,
        @Nullable List<Vector3d> referenceForAutoSeam,
        MatchSeamMode seamMode
    ) {
        List<Vector3d> points = copy(section);
        if (flip) {
            Collections.reverse(points);
        }
        int extra = 0;
        if (seamMode == MatchSeamMode.AUTO_SEAM && referenceForAutoSeam != null
            && referenceForAutoSeam.size() == points.size() && points.size() >= 2) {
            Integer auto = bestCyclicShift(referenceForAutoSeam, points);
            if (auto != null) {
                extra = auto;
            }
        }
        return rotate(points, seamOffset + extra);
    }

    static @Nullable Integer bestCyclicShift(List<Vector3d> reference, List<Vector3d> candidate) {
        int n = reference.size();
        if (n != candidate.size() || n < 2) {
            return null;
        }
        double best = Double.POSITIVE_INFINITY;
        int bestShift = 0;
        for (int shift = 0; shift < n; shift++) {
            double total = 0.0d;
            for (int i = 0; i < n; i++) {
                double d = VectorUtils.safeDistance(reference.get(i), candidate.get((i + shift) % n));
                if (!Double.isFinite(d)) {
                    total = Double.NaN;
                    break;
                }
                total += d;
                if (!Double.isFinite(total)) {
                    break;
                }
            }
            if (Double.isFinite(total) && total < best) {
                best = total;
                bestShift = shift;
            }
        }
        return Double.isFinite(best) ? bestShift : null;
    }

    static List<Vector3d> rotate(List<Vector3d> points, int offset) {
        if (points.isEmpty()) {
            return points;
        }
        int shift = Math.floorMod(offset, points.size());
        if (shift == 0) {
            return points;
        }
        List<Vector3d> rotated = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            rotated.add(points.get((i + shift) % points.size()));
        }
        return rotated;
    }

    private static List<Vector3d> copy(List<Vector3d> source) {
        List<Vector3d> points = new ArrayList<>(source.size());
        for (Vector3d point : source) {
            points.add(new Vector3d(point));
        }
        return points;
    }
}
