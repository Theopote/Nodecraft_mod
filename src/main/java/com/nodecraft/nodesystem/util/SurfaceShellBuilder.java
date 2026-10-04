package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for thickening a SurfaceStripData into offset layers and cap strips.
 */
public final class SurfaceShellBuilder {

    private static final double EPSILON = 1.0e-9d;

    public enum OffsetMode {
        OUTSIDE,
        INSIDE,
        CENTERED
    }

    public record ShellResult(
        SurfaceStripData outerSurface,
        SurfaceStripData innerSurface,
        List<SurfaceStripData> capSurfaces,
        List<SurfaceStripData> allSurfaces,
        double thickness,
        int sectionCount
    ) {
    }

    private SurfaceShellBuilder() {
    }

    public static @Nullable ShellResult buildShell(SurfaceStripData surfaceStrip,
                                                   double thickness,
                                                   OffsetMode offsetMode) {
        if (surfaceStrip == null || thickness <= EPSILON) {
            return null;
        }

        List<List<Vector3d>> sourceSections = surfaceStrip.sections();
        List<Boolean> closedFlags = surfaceStrip.sectionClosedFlags();
        if (sourceSections.size() < 2 || sourceSections.getFirst().size() < 2) {
            return null;
        }

        if (offsetMode == null) {
            return null;
        }
        if (hasCollapsedSourceQuad(sourceSections, closedFlags)) {
            return null;
        }

        double outerDistance = resolveOuterDistance(thickness, offsetMode);
        double innerDistance = resolveInnerDistance(thickness, offsetMode);
        if (!Double.isFinite(outerDistance) || !Double.isFinite(innerDistance)) {
            return null;
        }

        List<List<Vector3d>> outerSections = new ArrayList<>(sourceSections.size());
        List<List<Vector3d>> innerSections = new ArrayList<>(sourceSections.size());

        for (int sectionIndex = 0; sectionIndex < sourceSections.size(); sectionIndex++) {
            List<Vector3d> sourceSection = sourceSections.get(sectionIndex);
            List<Vector3d> outerSection = new ArrayList<>(sourceSection.size());
            List<Vector3d> innerSection = new ArrayList<>(sourceSection.size());

            for (int pointIndex = 0; pointIndex < sourceSection.size(); pointIndex++) {
                Vector3d point = sourceSection.get(pointIndex);
                Vector3d normal = computeShellNormal(sourceSections, closedFlags, sectionIndex, pointIndex);
                if (normal == null) {
                    return null;
                }
                Vector3d outerOffset = VectorUtils.safeScale(normal, outerDistance);
                Vector3d innerOffset = VectorUtils.safeScale(normal, -innerDistance);
                Vector3d outerPoint = VectorUtils.safeAdd(point, outerOffset);
                Vector3d innerPoint = VectorUtils.safeAdd(point, innerOffset);
                if (outerPoint == null || innerPoint == null) {
                    return null;
                }
                outerSection.add(outerPoint);
                innerSection.add(innerPoint);
            }

            outerSections.add(List.copyOf(outerSection));
            innerSections.add(List.copyOf(innerSection));
        }

        SurfaceStripData outerSurface = new SurfaceStripData(outerSections, closedFlags);
        SurfaceStripData innerSurface = new SurfaceStripData(innerSections, closedFlags);
        List<SurfaceStripData> capSurfaces = createCapSurfaces(outerSections, innerSections, closedFlags);

        List<SurfaceStripData> allSurfaces = new ArrayList<>(2 + capSurfaces.size());
        allSurfaces.add(outerSurface);
        allSurfaces.add(innerSurface);
        allSurfaces.addAll(capSurfaces);

        return new ShellResult(
            outerSurface,
            innerSurface,
            List.copyOf(capSurfaces),
            List.copyOf(allSurfaces),
            thickness,
            sourceSections.size()
        );
    }

    public static @Nullable GeometryData buildGeometry(List<? extends SurfaceStripData> shellSurfaces,
                                                       int longitudinalSteps,
                                                       double geometryRadius) {
        if (shellSurfaces == null || shellSurfaces.isEmpty() || geometryRadius <= EPSILON) {
            return null;
        }

        List<GeometryData> geometries = new ArrayList<>();
        for (SurfaceStripData surfaceStrip : shellSurfaces) {
            if (surfaceStrip == null) {
                continue;
            }
            GeometryData geometry = SurfaceStripBridge.toGeometry(
                surfaceStrip,
                longitudinalSteps,
                SurfaceStripBridge.BridgeMode.LATTICE,
                geometryRadius
            );
            if (geometry != null) {
                geometries.add(geometry);
            }
        }

        if (geometries.isEmpty()) {
            return null;
        }
        return geometries.size() == 1 ? geometries.getFirst() : new CompositeGeometryData(geometries);
    }

    public static @Nullable RegionData createBoundingRegion(List<? extends SurfaceStripData> shellSurfaces) {
        if (shellSurfaces == null || shellSurfaces.isEmpty()) {
            return null;
        }

        boolean hasPoint = false;
        double minX = 0.0d;
        double minY = 0.0d;
        double minZ = 0.0d;
        double maxX = 0.0d;
        double maxY = 0.0d;
        double maxZ = 0.0d;

        for (SurfaceStripData surfaceStrip : shellSurfaces) {
            if (surfaceStrip == null) {
                continue;
            }
            for (List<Vector3d> section : surfaceStrip.sections()) {
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
        }

        if (!hasPoint) {
            return null;
        }
        return new RegionData(
            BlockPos.ofFloored(minX, minY, minZ),
            BlockPos.ofFloored(maxX, maxY, maxZ)
        );
    }

    private static double resolveOuterDistance(double thickness, OffsetMode offsetMode) {
        return switch (offsetMode) {
            case OUTSIDE -> thickness;
            case INSIDE -> 0.0d;
            case CENTERED -> thickness * 0.5d;
        };
    }

    private static double resolveInnerDistance(double thickness, OffsetMode offsetMode) {
        return switch (offsetMode) {
            case OUTSIDE -> 0.0d;
            case INSIDE -> thickness;
            case CENTERED -> thickness * 0.5d;
        };
    }

    private static @Nullable Vector3d computeShellNormal(List<List<Vector3d>> sections,
                                                         List<Boolean> closedFlags,
                                                         int sectionIndex,
                                                         int pointIndex) {
        Vector3d sectionTangent = computeSectionTangent(
            sections.get(sectionIndex),
            Boolean.TRUE.equals(closedFlags.get(sectionIndex)),
            pointIndex
        );
        Vector3d railTangent = computeRailTangent(sections, sectionIndex, pointIndex);
        Vector3d crossed = VectorUtils.safeNormalize(VectorUtils.safeCross(sectionTangent, railTangent));
        if (crossed != null) {
            return crossed;
        }
        return averageNeighborFaceNormals(sections, closedFlags, sectionIndex, pointIndex);
    }

    private static @Nullable Vector3d computeSectionTangent(List<Vector3d> section, boolean closed, int pointIndex) {
        int size = section.size();
        Vector3d previous = section.get(clampSectionIndex(pointIndex - 1, size, closed));
        Vector3d next = section.get(clampSectionIndex(pointIndex + 1, size, closed));

        if (!closed) {
            if (pointIndex == 0) {
                previous = section.getFirst();
            } else if (pointIndex == size - 1) {
                next = section.get(size - 1);
            }
        }

        Vector3d tangent = VectorUtils.safeNormalize(VectorUtils.safeSubtract(next, previous));
        if (tangent != null) {
            return tangent;
        }
        if (pointIndex < size - 1) {
            tangent = VectorUtils.safeNormalize(
                VectorUtils.safeSubtract(section.get(pointIndex + 1), section.get(pointIndex))
            );
            if (tangent != null) {
                return tangent;
            }
        }
        if (pointIndex > 0) {
            return VectorUtils.safeNormalize(
                VectorUtils.safeSubtract(section.get(pointIndex), section.get(pointIndex - 1))
            );
        }
        return null;
    }

    private static int clampSectionIndex(int index, int size, boolean closed) {
        if (closed) {
            int resolved = index % size;
            return resolved < 0 ? resolved + size : resolved;
        }
        return Math.max(0, Math.min(size - 1, index));
    }

    private static @Nullable Vector3d computeRailTangent(List<List<Vector3d>> sections, int sectionIndex, int pointIndex) {
        Vector3d from;
        Vector3d to;
        if (sectionIndex == 0) {
            from = sections.get(0).get(pointIndex);
            to = sections.get(1).get(pointIndex);
        } else if (sectionIndex == sections.size() - 1) {
            from = sections.get(sectionIndex - 1).get(pointIndex);
            to = sections.get(sectionIndex).get(pointIndex);
        } else {
            from = sections.get(sectionIndex - 1).get(pointIndex);
            to = sections.get(sectionIndex + 1).get(pointIndex);
        }
        return VectorUtils.safeNormalize(VectorUtils.safeSubtract(to, from));
    }

    private static boolean hasCollapsedSourceQuad(List<List<Vector3d>> sections, List<Boolean> closedFlags) {
        int sectionCount = sections.size();
        int pointCount = sections.getFirst().size();
        for (int u = 0; u < sectionCount - 1; u++) {
            boolean wrap = Boolean.TRUE.equals(closedFlags.get(u))
                && Boolean.TRUE.equals(closedFlags.get(u + 1));
            int segCount = wrap ? pointCount : pointCount - 1;
            for (int j = 0; j < segCount; j++) {
                Vector3d a = sections.get(u).get(j);
                Vector3d b = sections.get(u).get((j + 1) % pointCount);
                Vector3d c = sections.get(u + 1).get(j);
                Vector3d d = sections.get(u + 1).get((j + 1) % pointCount);
                if (quadArea(a, b, c, d) <= EPSILON) {
                    return true;
                }
            }
        }
        return false;
    }

    private static @Nullable Vector3d averageNeighborFaceNormals(
        List<List<Vector3d>> sections,
        List<Boolean> closedFlags,
        int sectionIndex,
        int pointIndex
    ) {
        int sectionCount = sections.size();
        int pointCount = sections.getFirst().size();
        Vector3d sum = null;
        for (int du = -1; du <= 0; du++) {
            int u = sectionIndex + du;
            if (u < 0 || u >= sectionCount - 1) {
                continue;
            }
            boolean wrap = Boolean.TRUE.equals(closedFlags.get(u))
                && Boolean.TRUE.equals(closedFlags.get(u + 1));
            for (int dj = -1; dj <= 0; dj++) {
                int j = pointIndex + dj;
                if (wrap) {
                    j = clampSectionIndex(j, pointCount, true);
                } else if (j < 0 || j >= pointCount - 1) {
                    continue;
                }
                Vector3d a = sections.get(u).get(j);
                Vector3d b = sections.get(u).get((j + 1) % pointCount);
                Vector3d c = sections.get(u + 1).get(j);
                Vector3d d = sections.get(u + 1).get((j + 1) % pointCount);
                Vector3d face = quadUnitNormal(a, b, c, d);
                if (face == null) {
                    continue;
                }
                sum = sum == null ? new Vector3d(face) : VectorUtils.safeAdd(sum, face);
                if (sum == null) {
                    return null;
                }
            }
        }
        return VectorUtils.safeNormalize(sum);
    }

    private static @Nullable Vector3d quadUnitNormal(Vector3d a, Vector3d b, Vector3d c, Vector3d d) {
        Vector3d n1 = VectorUtils.safeNormalize(
            VectorUtils.safeCross(VectorUtils.safeSubtract(b, a), VectorUtils.safeSubtract(c, a))
        );
        Vector3d n2 = VectorUtils.safeNormalize(
            VectorUtils.safeCross(VectorUtils.safeSubtract(d, b), VectorUtils.safeSubtract(c, b))
        );
        Vector3d sum = null;
        if (n1 != null) {
            sum = n1;
        }
        if (n2 != null) {
            sum = sum == null ? n2 : VectorUtils.safeAdd(sum, n2);
        }
        return VectorUtils.safeNormalize(sum);
    }

    private static double quadArea(Vector3d a, Vector3d b, Vector3d c, Vector3d d) {
        double area1 = triangleArea(a, b, c);
        double area2 = triangleArea(b, d, c);
        double sum = area1 + area2;
        return Double.isFinite(sum) ? sum : 0.0d;
    }

    private static double triangleArea(Vector3d a, Vector3d b, Vector3d c) {
        Vector3d cross = VectorUtils.safeCross(VectorUtils.safeSubtract(b, a), VectorUtils.safeSubtract(c, a));
        double length = VectorUtils.safeLength(cross);
        if (!Double.isFinite(length)) {
            return 0.0d;
        }
        return length * 0.5d;
    }

    private static List<SurfaceStripData> createCapSurfaces(List<List<Vector3d>> outerSections,
                                                            List<List<Vector3d>> innerSections,
                                                            List<Boolean> closedFlags) {
        List<SurfaceStripData> caps = new ArrayList<>(2);
        if (outerSections.isEmpty()) {
            return caps;
        }

        boolean firstClosed = !closedFlags.isEmpty() && Boolean.TRUE.equals(closedFlags.getFirst());
        caps.add(new SurfaceStripData(
            List.of(outerSections.getFirst(), innerSections.getFirst()),
            List.of(firstClosed, firstClosed)
        ));

        boolean lastClosed = !closedFlags.isEmpty() && Boolean.TRUE.equals(closedFlags.getLast());
        caps.add(new SurfaceStripData(
            List.of(outerSections.getLast(), innerSections.getLast()),
            List.of(lastClosed, lastClosed)
        ));
        return caps;
    }
}
