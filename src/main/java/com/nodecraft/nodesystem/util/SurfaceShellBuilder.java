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

        OffsetMode resolvedMode = offsetMode == null ? OffsetMode.CENTERED : offsetMode;
        double outerDistance = resolveOuterDistance(thickness, resolvedMode);
        double innerDistance = resolveInnerDistance(thickness, resolvedMode);

        List<List<Vector3d>> outerSections = new ArrayList<>(sourceSections.size());
        List<List<Vector3d>> innerSections = new ArrayList<>(sourceSections.size());

        for (int sectionIndex = 0; sectionIndex < sourceSections.size(); sectionIndex++) {
            List<Vector3d> sourceSection = sourceSections.get(sectionIndex);
            List<Vector3d> outerSection = new ArrayList<>(sourceSection.size());
            List<Vector3d> innerSection = new ArrayList<>(sourceSection.size());

            for (int pointIndex = 0; pointIndex < sourceSection.size(); pointIndex++) {
                Vector3d point = sourceSection.get(pointIndex);
                Vector3d normal = computeShellNormal(sourceSections, closedFlags, sectionIndex, pointIndex);

                outerSection.add(new Vector3d(point).fma(outerDistance, normal));
                innerSection.add(new Vector3d(point).fma(-innerDistance, normal));
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

    private static Vector3d computeShellNormal(List<List<Vector3d>> sections,
                                               List<Boolean> closedFlags,
                                               int sectionIndex,
                                               int pointIndex) {
        List<Vector3d> section = sections.get(sectionIndex);
        Vector3d point = section.get(pointIndex);

        Vector3d sectionTangent = computeSectionTangent(section, closedFlags.get(sectionIndex), pointIndex);
        Vector3d railTangent = computeRailTangent(sections, sectionIndex, pointIndex);

        Vector3d normal = new Vector3d(sectionTangent).cross(railTangent);
        if (normal.lengthSquared() <= EPSILON) {
            normal = fallbackNormal(sectionTangent, railTangent, point);
        }
        if (normal.lengthSquared() <= EPSILON) {
            normal = new Vector3d(0.0d, 1.0d, 0.0d);
        }
        return normal.normalize();
    }

    private static Vector3d computeSectionTangent(List<Vector3d> section, boolean closed, int pointIndex) {
        int size = section.size();
        Vector3d previous = section.get(clampSectionIndex(pointIndex - 1, size, closed));
        Vector3d next = section.get(clampSectionIndex(pointIndex + 1, size, closed));

        if (!closed) {
            if (pointIndex == 0) {
                previous = section.get(0);
            } else if (pointIndex == size - 1) {
                next = section.get(size - 1);
            }
        }

        Vector3d tangent = new Vector3d(next).sub(previous);
        if (tangent.lengthSquared() <= EPSILON) {
            if (pointIndex < size - 1) {
                tangent = new Vector3d(section.get(pointIndex + 1)).sub(section.get(pointIndex));
            } else if (pointIndex > 0) {
                tangent = new Vector3d(section.get(pointIndex)).sub(section.get(pointIndex - 1));
            }
        }
        return tangent.lengthSquared() <= EPSILON ? new Vector3d(1.0d, 0.0d, 0.0d) : tangent.normalize();
    }

    private static int clampSectionIndex(int index, int size, boolean closed) {
        if (closed) {
            int resolved = index % size;
            return resolved < 0 ? resolved + size : resolved;
        }
        return Math.max(0, Math.min(size - 1, index));
    }

    private static Vector3d computeRailTangent(List<List<Vector3d>> sections, int sectionIndex, int pointIndex) {
        Vector3d tangent;
        if (sectionIndex == 0) {
            tangent = new Vector3d(sections.get(1).get(pointIndex)).sub(sections.get(0).get(pointIndex));
        } else if (sectionIndex == sections.size() - 1) {
            tangent = new Vector3d(sections.get(sectionIndex).get(pointIndex)).sub(sections.get(sectionIndex - 1).get(pointIndex));
        } else {
            tangent = new Vector3d(sections.get(sectionIndex + 1).get(pointIndex)).sub(sections.get(sectionIndex - 1).get(pointIndex));
        }
        return tangent.lengthSquared() <= EPSILON ? new Vector3d(0.0d, 0.0d, 1.0d) : tangent.normalize();
    }

    private static Vector3d fallbackNormal(Vector3d sectionTangent, Vector3d railTangent, Vector3d point) {
        Vector3d reference = Math.abs(railTangent.y) < 0.95d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d candidate = new Vector3d(sectionTangent).cross(reference);
        if (candidate.lengthSquared() <= EPSILON) {
            candidate = new Vector3d(railTangent).cross(reference);
        }
        if (candidate.lengthSquared() <= EPSILON) {
            candidate = new Vector3d(point).cross(reference);
        }
        return candidate;
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
