package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * Shared validation for {@link SurfaceStripData} topology and budgets (Graph V72).
 */
public final class SurfaceStripValidator {

    private static final double EPS = 1.0e-9d;

    private SurfaceStripValidator() {
    }

    /**
     * @return null when valid; otherwise an actionable error message
     */
    public static @Nullable String validate(@Nullable SurfaceStripData strip) {
        if (strip == null) {
            return "Surface strip is missing";
        }
        List<List<Vector3d>> sections = strip.sections();
        if (sections.size() < 2) {
            return "Surface strip requires at least two sections";
        }
        if (strip.sectionClosedFlags().size() != sections.size()) {
            return "Section closed flags must match section count";
        }

        int pointsPerSection = sections.getFirst().size();
        if (pointsPerSection < 2) {
            return "Each section requires at least two points";
        }
        if (!GenerationLimits.isWithinSurfacePointsPerSection(pointsPerSection)) {
            return "Points per section exceeds limit (" + GenerationLimits.MAX_SURFACE_POINTS_PER_SECTION + ")";
        }
        if (!GenerationLimits.isWithinSurfaceSections(sections.size())) {
            return "Section count exceeds limit (" + GenerationLimits.MAX_SURFACE_SECTIONS + ")";
        }
        if (!GenerationLimits.isWithinSurfaceWorkload(sections.size(), pointsPerSection)) {
            return "Surface workload exceeds limit (" + GenerationLimits.MAX_SURFACE_TOTAL_POINTS + " total points)";
        }

        for (int s = 0; s < sections.size(); s++) {
            List<Vector3d> section = sections.get(s);
            if (section.size() != pointsPerSection) {
                return "All sections must have the same point count";
            }
            for (int p = 0; p < section.size(); p++) {
                Vector3d point = section.get(p);
                if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
                    return "Surface strip contains non-finite points";
                }
                if (p > 0) {
                    Vector3d prev = section.get(p - 1);
                    if (point.distanceSquared(prev) <= EPS * EPS) {
                        return "Surface strip contains degenerate zero-length edges";
                    }
                }
            }
            if (Boolean.TRUE.equals(strip.sectionClosedFlags().get(s)) && section.size() >= 2) {
                Vector3d first = section.getFirst();
                Vector3d last = section.getLast();
                if (first.distanceSquared(last) <= EPS * EPS && section.size() > 2) {
                    // closed section may repeat seam vertex — allowed
                }
            }
        }
        return null;
    }

    public static @Nullable String validateConstruction(
            List<List<Vector3d>> sections,
            List<Boolean> closedFlags
    ) {
        try {
            return validate(new SurfaceStripData(sections, closedFlags));
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }
}
