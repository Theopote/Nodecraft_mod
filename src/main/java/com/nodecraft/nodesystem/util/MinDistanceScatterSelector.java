package com.nodecraft.nodesystem.util;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Strict minimum-distance point selection for scatter nodes.
 */
public final class MinDistanceScatterSelector {

    public enum DistributionMode {
        RANDOM,
        BLUE_NOISE_APPROX
    }

    private MinDistanceScatterSelector() {
    }

    public static List<Vector3d> select(
        List<Vector3d> candidates,
        int targetCount,
        double minDistance,
        DistributionMode mode,
        Random random
    ) {
        if (candidates.isEmpty() || targetCount <= 0) {
            return List.of();
        }

        double minDistanceSq = minDistance * minDistance;
        if (minDistanceSq <= 0.0d) {
            List<Vector3d> pool = new ArrayList<>(candidates);
            Collections.shuffle(pool, random);
            if (pool.size() <= targetCount) {
                return pool;
            }
            return new ArrayList<>(pool.subList(0, targetCount));
        }

        return mode == DistributionMode.BLUE_NOISE_APPROX
            ? selectBlueNoiseApprox(candidates, targetCount, random, minDistanceSq)
            : selectRandomWithSpacing(candidates, targetCount, random, minDistanceSq);
    }

    public static boolean isFarEnough(Vector3d candidate, List<Vector3d> accepted, double minDistanceSq) {
        for (Vector3d point : accepted) {
            if (candidate.distanceSquared(point) < minDistanceSq) {
                return false;
            }
        }
        return true;
    }

    private static List<Vector3d> selectRandomWithSpacing(
        List<Vector3d> candidates,
        int targetCount,
        Random random,
        double minDistanceSq
    ) {
        List<Vector3d> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        List<Vector3d> selected = new ArrayList<>(targetCount);
        for (Vector3d candidate : shuffled) {
            if (selected.size() >= targetCount) {
                break;
            }
            if (isFarEnough(candidate, selected, minDistanceSq)) {
                selected.add(new Vector3d(candidate));
            }
        }
        return selected;
    }

    private static List<Vector3d> selectBlueNoiseApprox(
        List<Vector3d> candidates,
        int targetCount,
        Random random,
        double minDistanceSq
    ) {
        List<Vector3d> pool = new ArrayList<>(candidates);
        Collections.shuffle(pool, random);
        List<Vector3d> selected = new ArrayList<>(targetCount);
        if (pool.isEmpty()) {
            return selected;
        }

        selected.add(new Vector3d(pool.removeFirst()));
        while (!pool.isEmpty() && selected.size() < targetCount) {
            int bestIndex = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            int attempts = Math.min(24, pool.size());
            for (int i = 0; i < attempts; i++) {
                int candidateIndex = random.nextInt(pool.size());
                Vector3d candidate = pool.get(candidateIndex);
                double nearestSq = nearestDistanceSq(candidate, selected);
                if (nearestSq < minDistanceSq) {
                    continue;
                }
                if (nearestSq > bestScore) {
                    bestScore = nearestSq;
                    bestIndex = candidateIndex;
                }
            }
            if (bestIndex < 0) {
                break;
            }
            selected.add(new Vector3d(pool.remove(bestIndex)));
        }
        return selected;
    }

    private static double nearestDistanceSq(Vector3d candidate, List<Vector3d> selected) {
        double minSq = Double.POSITIVE_INFINITY;
        for (Vector3d point : selected) {
            double distanceSq = candidate.distanceSquared(point);
            if (distanceSq < minSq) {
                minSq = distanceSq;
            }
        }
        return minSq;
    }
}
