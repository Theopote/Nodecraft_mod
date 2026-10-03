package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Exact k nearest neighbors (excluding self) via a median-split 3D KD-tree.
 */
public final class PointListKnn3d {

    private PointListKnn3d() {
    }

    /**
     * Spatial index over a point cloud. Rebuild after positions change (once per Relax iteration).
     */
    public static final class Index {
        private final List<Vector3d> points;
        private final @Nullable KdNode root;

        private Index(List<Vector3d> points, @Nullable KdNode root) {
            this.points = points;
            this.root = root;
        }

        public static Index build(List<Vector3d> points) {
            int n = points.size();
            int[] ids = new int[n];
            for (int i = 0; i < n; i++) {
                ids[i] = i;
            }
            return new Index(points, buildNode(points, ids, 0, n, 0));
        }

        /**
         * Fills {@code outIdx} with up to {@code k} neighbor indices closest to {@code points.get(query)},
         * never including {@code query}. Unused slots remain {@code -1}.
         */
        public void queryKNearest(int query, int k, int[] outIdx) {
            Arrays.fill(outIdx, -1);
            if (k < 1 || points.size() < 2 || query < 0 || query >= points.size()) {
                return;
            }
            PriorityQueue<Neighbor> worstFirst = new PriorityQueue<>((a, b) -> Double.compare(b.distance, a.distance));
            search(root, query, k, worstFirst);
            int i = 0;
            for (Neighbor neighbor : worstFirst) {
                if (i >= k) {
                    break;
                }
                outIdx[i++] = neighbor.index;
            }
        }

        private void search(@Nullable KdNode node, int query, int k, PriorityQueue<Neighbor> worstFirst) {
            if (node == null) {
                return;
            }
            if (node.pointIndex != query) {
                double distance = PointUtils.safeDistance(points.get(query), points.get(node.pointIndex));
                if (Double.isFinite(distance)) {
                    if (worstFirst.size() < k) {
                        worstFirst.add(new Neighbor(node.pointIndex, distance));
                    } else if (worstFirst.peek() != null && distance < worstFirst.peek().distance) {
                        worstFirst.poll();
                        worstFirst.add(new Neighbor(node.pointIndex, distance));
                    }
                }
            }

            double queryCoord = coord(points.get(query), node.axis);
            double splitCoord = coord(points.get(node.pointIndex), node.axis);
            KdNode nearer = queryCoord <= splitCoord ? node.left : node.right;
            KdNode farther = queryCoord <= splitCoord ? node.right : node.left;
            search(nearer, query, k, worstFirst);

            double planeDist = Math.abs(queryCoord - splitCoord);
            if (worstFirst.peek() != null && Double.isFinite(planeDist) && (worstFirst.size() < k || planeDist < worstFirst.peek().distance)) {
                search(farther, query, k, worstFirst);
            }
        }

        private static @Nullable KdNode buildNode(List<Vector3d> points, int[] ids, int lo, int hi, int depth) {
            int count = hi - lo;
            if (count <= 0) {
                return null;
            }
            int axis = depth % 3;
            Integer[] slice = new Integer[count];
            for (int i = 0; i < count; i++) {
                slice[i] = ids[lo + i];
            }
            Arrays.sort(slice, Comparator.comparingDouble(a -> coord(points.get(a), axis)));
            for (int i = 0; i < count; i++) {
                ids[lo + i] = slice[i];
            }
            int mid = lo + count / 2;
            return new KdNode(
                ids[mid],
                axis,
                buildNode(points, ids, lo, mid, depth + 1),
                buildNode(points, ids, mid + 1, hi, depth + 1)
            );
        }

        private static double coord(Vector3d point, int axis) {
            return switch (axis) {
                case 0 -> point.x;
                case 1 -> point.y;
                default -> point.z;
            };
        }

        private record KdNode(int pointIndex, int axis, @Nullable KdNode left, @Nullable KdNode right) {
        }

        private record Neighbor(int index, double distance) {
        }
    }
}
