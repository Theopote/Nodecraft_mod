package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared bridge utilities for converting SurfaceStripData into block and geometry outputs.
 */
public final class SurfaceStripBridge {
    private static final double EPSILON = 1.0e-9d;

    public enum BridgeMode {
        RAILS_ONLY,
        SECTIONS_ONLY,
        LATTICE;

        public boolean includeRails() {
            return this == RAILS_ONLY || this == LATTICE;
        }

        public boolean includeSectionEdges() {
            return this == SECTIONS_ONLY || this == LATTICE;
        }
    }

    private SurfaceStripBridge() {
    }

    public static BlockPosList voxelize(SurfaceStripData surfaceStrip,
                                        int longitudinalSteps,
                                        BridgeMode mode) {
        if (surfaceStrip == null) {
            return new BlockPosList();
        }

        Set<BlockPos> blockSet = new LinkedHashSet<>();
        List<List<Vector3d>> sections = surfaceStrip.getSections();
        List<Boolean> closedFlags = surfaceStrip.getSectionClosedFlags();
        int resolvedSteps = Math.max(1, longitudinalSteps);
        BridgeMode resolvedMode = mode == null ? BridgeMode.LATTICE : mode;
        boolean includeSectionEdges = resolvedMode.includeSectionEdges();
        boolean includeRails = resolvedMode.includeRails();

        if (includeSectionEdges) {
            for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                addPolylineBlocks(blockSet, sections.get(sectionIndex), closedFlags.get(sectionIndex));
            }
        }

        for (int sectionIndex = 0; sectionIndex < sections.size() - 1; sectionIndex++) {
            List<Vector3d> current = sections.get(sectionIndex);
            List<Vector3d> next = sections.get(sectionIndex + 1);
            List<Vector3d> previousSample = current;

            for (int step = 1; step <= resolvedSteps; step++) {
                double t = (double) step / (double) resolvedSteps;
                List<Vector3d> sample = interpolateSection(current, next, t);

                if (includeRails) {
                    addRailBlocks(blockSet, previousSample, sample);
                }
                if (includeSectionEdges && step < resolvedSteps) {
                    addPolylineBlocks(blockSet, sample, closedFlags.get(sectionIndex) || closedFlags.get(sectionIndex + 1));
                }

                previousSample = sample;
            }
        }

        return new BlockPosList(blockSet);
    }

    public static @Nullable GeometryData toGeometry(SurfaceStripData surfaceStrip,
                                                    int longitudinalSteps,
                                                    BridgeMode mode,
                                                    double radius) {
        if (surfaceStrip == null || radius <= 0.0d) {
            return null;
        }

        List<GeometryData> geometries = new ArrayList<>();
        List<List<Vector3d>> sections = surfaceStrip.getSections();
        List<Boolean> closedFlags = surfaceStrip.getSectionClosedFlags();
        int resolvedSteps = Math.max(1, longitudinalSteps);
        BridgeMode resolvedMode = mode == null ? BridgeMode.LATTICE : mode;
        boolean includeSectionEdges = resolvedMode.includeSectionEdges();
        boolean includeRails = resolvedMode.includeRails();

        if (includeSectionEdges) {
            for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                addPolylineGeometry(geometries, sections.get(sectionIndex), closedFlags.get(sectionIndex), radius);
            }
        }

        for (int sectionIndex = 0; sectionIndex < sections.size() - 1; sectionIndex++) {
            List<Vector3d> current = sections.get(sectionIndex);
            List<Vector3d> next = sections.get(sectionIndex + 1);
            List<Vector3d> previousSample = current;

            for (int step = 1; step <= resolvedSteps; step++) {
                double t = (double) step / (double) resolvedSteps;
                List<Vector3d> sample = interpolateSection(current, next, t);

                if (includeRails) {
                    addRailGeometry(geometries, previousSample, sample, radius);
                }
                if (includeSectionEdges && step < resolvedSteps) {
                    addPolylineGeometry(geometries, sample, closedFlags.get(sectionIndex) || closedFlags.get(sectionIndex + 1), radius);
                }

                previousSample = sample;
            }
        }

        if (geometries.isEmpty()) {
            return null;
        }
        return geometries.size() == 1 ? geometries.get(0) : new CompositeGeometryData(geometries);
    }

    public static @Nullable RegionData createBoundingRegion(SurfaceStripData surfaceStrip) {
        if (surfaceStrip == null) {
            return null;
        }

        boolean hasPoint = false;
        double minX = 0.0d;
        double minY = 0.0d;
        double minZ = 0.0d;
        double maxX = 0.0d;
        double maxY = 0.0d;
        double maxZ = 0.0d;

        for (List<Vector3d> section : surfaceStrip.getSections()) {
            for (Vector3d point : section) {
                if (!hasPoint) {
                    minX = maxX = point.x;
                    minY = maxY = point.y;
                    minZ = maxZ = point.z;
                    hasPoint = true;
                    continue;
                }

                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
                maxZ = Math.max(maxZ, point.z);
            }
        }

        if (!hasPoint) {
            return null;
        }

        return new RegionData(
            BlockPos.ofFloored(minX, minY, minZ),
            BlockPos.ofFloored(maxX, maxY, maxZ)
        );
    }

    public static int estimateGeometrySegmentCount(SurfaceStripData surfaceStrip,
                                                   int longitudinalSteps,
                                                   BridgeMode mode) {
        if (surfaceStrip == null) {
            return 0;
        }

        int sectionCount = surfaceStrip.getSectionCount();
        int pointsPerSection = surfaceStrip.getPointsPerSection();
        if (sectionCount < 2 || pointsPerSection < 2) {
            return 0;
        }

        int resolvedSteps = Math.max(1, longitudinalSteps);
        BridgeMode resolvedMode = mode == null ? BridgeMode.LATTICE : mode;
        boolean includeSectionEdges = resolvedMode.includeSectionEdges();
        boolean includeRails = resolvedMode.includeRails();
        int total = 0;

        if (includeSectionEdges) {
            for (Boolean closed : surfaceStrip.getSectionClosedFlags()) {
                total += Boolean.TRUE.equals(closed) ? pointsPerSection : pointsPerSection - 1;
            }
            for (int sectionIndex = 0; sectionIndex < sectionCount - 1; sectionIndex++) {
                boolean closed = Boolean.TRUE.equals(surfaceStrip.getSectionClosedFlags().get(sectionIndex))
                    || Boolean.TRUE.equals(surfaceStrip.getSectionClosedFlags().get(sectionIndex + 1));
                total += (resolvedSteps - 1) * (closed ? pointsPerSection : pointsPerSection - 1);
            }
        }

        if (includeRails) {
            total += (sectionCount - 1) * resolvedSteps * pointsPerSection;
        }

        return total;
    }

    private static List<Vector3d> interpolateSection(List<Vector3d> current, List<Vector3d> next, double t) {
        int count = Math.min(current.size(), next.size());
        List<Vector3d> sample = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vector3d start = current.get(i);
            Vector3d end = next.get(i);
            sample.add(new Vector3d(
                lerp(start.x, end.x, t),
                lerp(start.y, end.y, t),
                lerp(start.z, end.z, t)
            ));
        }
        return sample;
    }

    private static void addRailBlocks(Set<BlockPos> blockSet, List<Vector3d> from, List<Vector3d> to) {
        int pairCount = Math.min(from.size(), to.size());
        for (int i = 0; i < pairCount; i++) {
            addLineBlocks(blockSet, from.get(i), to.get(i));
        }
    }

    private static void addPolylineBlocks(Set<BlockPos> blockSet, List<Vector3d> points, boolean closed) {
        if (points.size() < 2) {
            return;
        }
        for (int i = 0; i < points.size() - 1; i++) {
            addLineBlocks(blockSet, points.get(i), points.get(i + 1));
        }
        if (closed) {
            addLineBlocks(blockSet, points.get(points.size() - 1), points.get(0));
        }
    }

    private static void addLineBlocks(Set<BlockPos> blockSet, Vector3d start, Vector3d end) {
        Vector3d delta = new Vector3d(end).sub(start);
        int steps = (int) Math.ceil(Math.max(Math.max(Math.abs(delta.x), Math.abs(delta.y)), Math.abs(delta.z)));
        if (steps <= 0) {
            blockSet.add(BlockPos.ofFloored(start.x, start.y, start.z));
            return;
        }

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            blockSet.add(BlockPos.ofFloored(
                lerp(start.x, end.x, t),
                lerp(start.y, end.y, t),
                lerp(start.z, end.z, t)
            ));
        }
    }

    private static void addRailGeometry(List<GeometryData> geometries, List<Vector3d> from, List<Vector3d> to, double radius) {
        int pairCount = Math.min(from.size(), to.size());
        for (int i = 0; i < pairCount; i++) {
            addCylinder(geometries, from.get(i), to.get(i), radius);
        }
    }

    private static void addPolylineGeometry(List<GeometryData> geometries, List<Vector3d> points, boolean closed, double radius) {
        if (points.size() < 2) {
            return;
        }
        for (int i = 0; i < points.size() - 1; i++) {
            addCylinder(geometries, points.get(i), points.get(i + 1), radius);
        }
        if (closed) {
            addCylinder(geometries, points.get(points.size() - 1), points.get(0), radius);
        }
    }

    private static void addCylinder(List<GeometryData> geometries, Vector3d start, Vector3d end, double radius) {
        if (new Vector3d(end).sub(start).lengthSquared() <= EPSILON) {
            return;
        }
        geometries.add(new CylinderGeometryData(start, end, radius));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
