package com.nodecraft.gui.components.port;

import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.Vec3;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Converts port-data preview targets into world preview coordinates.
 */
public final class PortHighlightSupport {

    private PortHighlightSupport() {
    }

    public static String previewOwnerId(UUID nodeId) {
        return nodeId + ":property-panel-preview";
    }

    public static Coordinate vec3ToCoordinate(Vec3 vec) {
        return new Coordinate(
                (int) Math.floor(vec.getX()),
                (int) Math.floor(vec.getY()),
                (int) Math.floor(vec.getZ())
        );
    }

    public static List<Coordinate> vec3ListToCoordinates(List<?> points) {
        List<Coordinate> coordinates = new ArrayList<>(points.size());
        for (Object point : points) {
            if (point instanceof Vec3 vec) {
                coordinates.add(vec3ToCoordinate(vec));
            }
        }
        return coordinates;
    }

    public record RegionBounds(Vec3d min, Vec3d maxExclusive) {
    }

    /**
     * Extracts region corners via {@code getMin()} / {@code getMax()} when present.
     */
    public static Optional<RegionBounds> extractRegionBounds(Object region) {
        if (region == null) {
            return Optional.empty();
        }
        try {
            Method getMinMethod = region.getClass().getMethod("getMin");
            Method getMaxMethod = region.getClass().getMethod("getMax");
            Object min = getMinMethod.invoke(region);
            Object max = getMaxMethod.invoke(region);
            if (!(min instanceof Vec3 minVec) || !(max instanceof Vec3 maxVec)) {
                return Optional.empty();
            }
            Vec3d minCorner = new Vec3d(minVec.getX(), minVec.getY(), minVec.getZ());
            // PreviewManager expects max-exclusive; treat getMax as inclusive block corner.
            Vec3d maxExclusive = new Vec3d(
                    maxVec.getX() + 1.0d,
                    maxVec.getY() + 1.0d,
                    maxVec.getZ() + 1.0d
            );
            return Optional.of(new RegionBounds(minCorner, maxExclusive));
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }

    public static @Nullable Vec3d vec3ToVec3d(Vec3 vec) {
        if (vec == null) {
            return null;
        }
        return new Vec3d(vec.getX(), vec.getY(), vec.getZ());
    }
}
