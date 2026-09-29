package com.nodecraft.nodesystem.util;

import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 2D convex hull utilities (monotone chain).
 */
public final class ConvexHull2d {

    private static final double DEDUPE_GRID = 1.0e-6d;

    private ConvexHull2d() {
    }

    /**
     * @return indices into {@code points} for the convex hull in CCW order, or empty when degenerate
     */
    public static List<Integer> convexHullIndices(List<Vector2d> points) {
        if (points == null || points.size() < 3) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<Integer> uniqueIndices = new ArrayList<>();
        List<Vector2d> uniquePoints = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Vector2d point = points.get(i);
            if (point == null) {
                continue;
            }
            String key = quant(point.x) + ":" + quant(point.y);
            if (seen.add(key)) {
                uniqueIndices.add(i);
                uniquePoints.add(new Vector2d(point));
            }
        }
        if (uniquePoints.size() < 3) {
            return List.of();
        }

        List<Integer> order = new ArrayList<>(uniquePoints.size());
        for (int i = 0; i < uniquePoints.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer index) -> uniquePoints.get(index).x)
                .thenComparingDouble(index -> uniquePoints.get(index).y));

        List<Integer> lower = new ArrayList<>();
        for (int orderIndex : order) {
            Vector2d point = uniquePoints.get(orderIndex);
            while (lower.size() >= 2
                    && cross(uniquePoints.get(lower.get(lower.size() - 2)), uniquePoints.get(lower.getLast()), point) <= 0.0d) {
                lower.removeLast();
            }
            lower.add(orderIndex);
        }

        List<Integer> upper = new ArrayList<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            int orderIndex = order.get(i);
            Vector2d point = uniquePoints.get(orderIndex);
            while (upper.size() >= 2
                    && cross(uniquePoints.get(upper.get(upper.size() - 2)), uniquePoints.get(upper.getLast()), point) <= 0.0d) {
                upper.removeLast();
            }
            upper.add(orderIndex);
        }

        lower.removeLast();
        upper.removeLast();
        lower.addAll(upper);

        List<Integer> hull = new ArrayList<>(lower.size());
        for (int localIndex : lower) {
            hull.add(uniqueIndices.get(localIndex));
        }
        return hull;
    }

    private static double cross(Vector2d origin, Vector2d a, Vector2d b) {
        return (a.x - origin.x) * (b.y - origin.y) - (a.y - origin.y) * (b.x - origin.x);
    }

    private static String quant(double value) {
        long quantized = Math.round(value / DEDUPE_GRID);
        return Long.toString(quantized);
    }
}
