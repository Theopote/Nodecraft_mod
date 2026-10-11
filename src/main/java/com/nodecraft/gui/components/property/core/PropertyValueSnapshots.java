package com.nodecraft.gui.components.property.core;

import com.google.gson.Gson;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.Vec3;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep copies property values for undo/redo snapshots.
 */
public final class PropertyValueSnapshots {

    private static final Gson GSON = new Gson();

    private PropertyValueSnapshots() {
    }

    public static Object copy(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String || value instanceof Character) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue;
        }
        if (value instanceof Vec3 vec3) {
            return new Vec3(vec3.getX(), vec3.getY(), vec3.getZ());
        }
        if (value instanceof Vector3d vector3d) {
            return new Vector3d(vector3d);
        }
        if (value instanceof BlockPos blockPos) {
            return new BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        if (value instanceof PolylineData polyline) {
            return new PolylineData(polyline.points().stream()
                    .map(point -> new net.minecraft.util.math.Vec3d(point.x, point.y, point.z))
                    .toList());
        }
        if (value instanceof RegionData region) {
            BlockPos corner1 = region.corner1();
            BlockPos corner2 = region.corner2();
            return new RegionData(
                    corner1 == null ? null : new BlockPos(corner1.getX(), corner1.getY(), corner1.getZ()),
                    corner2 == null ? null : new BlockPos(corner2.getX(), corner2.getY(), corner2.getZ())
            );
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>(list.size());
            for (Object element : list) {
                copied.add(copy(element));
            }
            return copied;
        }
        try {
            return GSON.fromJson(GSON.toJson(value), value.getClass());
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("Gson snapshot failed for {}, attempting typed fallback", value.getClass().getName(), e);
            return typedFallbackCopy(value);
        }
    }

    private static Object typedFallbackCopy(Object value) {
        if (value instanceof Curve curve) {
            try {
                return GSON.fromJson(GSON.toJson(curve), Curve.class);
            } catch (Exception ignored) {
                NodeCraft.LOGGER.warn("Unable to isolate Curve snapshot; undo may alias live data");
            }
        }
        if (value instanceof PolylineData polyline) {
            try {
                return GSON.fromJson(GSON.toJson(polyline), PolylineData.class);
            } catch (Exception ignored) {
                NodeCraft.LOGGER.warn("Unable to isolate PolylineData snapshot; undo may alias live data");
            }
        }
        if (value instanceof RegionData region) {
            try {
                return GSON.fromJson(GSON.toJson(region), RegionData.class);
            } catch (Exception ignored) {
                NodeCraft.LOGGER.warn("Unable to isolate RegionData snapshot; undo may alias live data");
            }
        }
        return value;
    }
}
