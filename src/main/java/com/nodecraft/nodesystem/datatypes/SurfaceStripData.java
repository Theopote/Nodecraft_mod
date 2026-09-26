package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a lightweight ordered strip surface made of corresponding section point lists.
 * This is intended as an intermediate modeling datatype for loft/extrude/sweep workflows.
 */
public record SurfaceStripData(List<List<Vector3d>> sections, List<Boolean> sectionClosedFlags) {
    public SurfaceStripData(List<List<Vector3d>> sections, List<Boolean> sectionClosedFlags) {
        if (sections == null || sections.size() < 2) {
            throw new IllegalArgumentException("Surface strip requires at least two sections");
        }

        List<List<Vector3d>> copiedSections = new ArrayList<>(sections.size());
        Integer expectedPointsPerSection = null;
        for (List<Vector3d> section : sections) {
            if (section == null || section.size() < 2) {
                throw new IllegalArgumentException("Each surface strip section requires at least two points");
            }

            if (expectedPointsPerSection == null) {
                expectedPointsPerSection = section.size();
            } else if (expectedPointsPerSection != section.size()) {
                throw new IllegalArgumentException("All surface strip sections must have the same point count");
            }

            List<Vector3d> copiedSection = new ArrayList<>(section.size());
            for (Vector3d point : section) {
                copiedSection.add(new Vector3d(point));
            }
            copiedSections.add(List.copyOf(copiedSection));
        }

        if (sectionClosedFlags == null || sectionClosedFlags.size() != sections.size()) {
            throw new IllegalArgumentException("Surface strip section closed flags must match section count");
        }

        List<Boolean> copiedFlags = new ArrayList<>(sectionClosedFlags.size());
        for (Boolean flag : sectionClosedFlags) {
            copiedFlags.add(Boolean.TRUE.equals(flag));
        }

        this.sections = List.copyOf(copiedSections);
        this.sectionClosedFlags = List.copyOf(copiedFlags);
    }

    @Override
    public List<List<Vector3d>> sections() {
        List<List<Vector3d>> copiedSections = new ArrayList<>(sections.size());
        for (List<Vector3d> section : sections) {
            List<Vector3d> copiedSection = new ArrayList<>(section.size());
            for (Vector3d point : section) {
                copiedSection.add(new Vector3d(point));
            }
            copiedSections.add(List.copyOf(copiedSection));
        }
        return List.copyOf(copiedSections);
    }

    public int getSectionCount() {
        return sections.size();
    }

    public int getPointsPerSection() {
        return sections.isEmpty() ? 0 : sections.getFirst().size();
    }

    public boolean areAllSectionsClosed() {
        for (Boolean closed : sectionClosedFlags) {
            if (!Boolean.TRUE.equals(closed)) {
                return false;
            }
        }
        return true;
    }

    public List<Vector3d> getFlattenedPoints() {
        List<Vector3d> flattened = new ArrayList<>(getSectionCount() * getPointsPerSection());
        for (List<Vector3d> section : sections) {
            for (Vector3d point : section) {
                flattened.add(new Vector3d(point));
            }
        }
        return List.copyOf(flattened);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SurfaceStripData that)) return false;
        return Objects.equals(sections, that.sections)
                && Objects.equals(sectionClosedFlags, that.sectionClosedFlags);
    }

    @Override
    public @NonNull String toString() {
        return "SurfaceStripData{sectionCount=" + getSectionCount()
                + ", pointsPerSection=" + getPointsPerSection()
                + ", allClosed=" + areAllSectionsClosed() + "}";
    }
}
