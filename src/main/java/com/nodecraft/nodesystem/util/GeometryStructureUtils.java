package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry structure helpers for placement workload budgeting (Graph V76).
 */
public final class GeometryStructureUtils {

    private GeometryStructureUtils() {
    }

    /**
     * Counts leaf geometries: composites are flattened; any other non-null geometry counts as one.
     */
    public static long countLeaves(@Nullable GeometryData geometry) {
        if (geometry == null) {
            return 0;
        }
        List<GeometryData> leaves = new ArrayList<>();
        CompositeGeometryData.appendLeaves(leaves, geometry);
        return leaves.size();
    }
}
