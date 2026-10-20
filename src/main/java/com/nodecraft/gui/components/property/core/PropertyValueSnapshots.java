package com.nodecraft.gui.components.property.core;

import com.google.gson.Gson;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.Vec3;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep copies property values for undo/redo snapshots.
 * Unsupported mutable types fail with {@link SnapshotIsolationException} instead of aliasing live data.
 */
public final class PropertyValueSnapshots {

    private static final Gson GSON = new Gson();

    private PropertyValueSnapshots() {
    }

    /**
     * Deep-copies a node state object returned by {@code BaseNode.getNodeState()}.
     * Map entries are copied individually so mutable geometry values cannot alias live node data.
     */
    public static Object copyNodeState(Object state) {
        if (state == null) {
            return null;
        }
        if (state instanceof Map<?, ?> map) {
            Map<String, Object> copied = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copied.put(key, copy(entry.getValue()));
                }
            }
            return copied;
        }
        try {
            Object copied = GSON.fromJson(GSON.toJson(state), state.getClass());
            if (copied == null) {
                throw new SnapshotIsolationException(
                        "Unable to isolate node state snapshot of type " + state.getClass().getName());
            }
            return copied;
        } catch (SnapshotIsolationException e) {
            throw e;
        } catch (Exception e) {
            throw new SnapshotIsolationException(
                    "Unable to isolate node state snapshot of type " + state.getClass().getName(), e);
        }
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
        if (value instanceof BlockState blockState) {
            // BlockState is not Gson-safe; isolate via registry id + default state.
            Identifier id = Registries.BLOCK.getId(blockState.getBlock());
            return Registries.BLOCK.get(id).getDefaultState();
        }
        if (value instanceof Identifier identifier) {
            return Identifier.of(identifier.getNamespace(), identifier.getPath());
        }
        if (value instanceof PlaneData plane) {
            PlaneData copied = PlaneData.canonical(plane.getPoint(), plane.getNormal());
            if (copied == null) {
                throw new SnapshotIsolationException("Unable to isolate PlaneData snapshot");
            }
            return copied;
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
            Object copied = GSON.fromJson(GSON.toJson(value), value.getClass());
            if (copied == null) {
                return typedFallbackCopy(value);
            }
            return copied;
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("Gson snapshot failed for {}, attempting typed fallback", value.getClass().getName(), e);
            return typedFallbackCopy(value);
        }
    }

    private static Object typedFallbackCopy(Object value) {
        if (value instanceof Curve curve) {
            try {
                Object copied = GSON.fromJson(GSON.toJson(curve), Curve.class);
                if (copied != null) {
                    return copied;
                }
            } catch (Exception ignored) {
                // fall through to failure
            }
        }
        if (value instanceof PolylineData polyline) {
            try {
                return new PolylineData(polyline.points().stream()
                        .map(point -> new net.minecraft.util.math.Vec3d(point.x, point.y, point.z))
                        .toList());
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (value instanceof RegionData region) {
            try {
                BlockPos corner1 = region.corner1();
                BlockPos corner2 = region.corner2();
                return new RegionData(
                        corner1 == null ? null : new BlockPos(corner1.getX(), corner1.getY(), corner1.getZ()),
                        corner2 == null ? null : new BlockPos(corner2.getX(), corner2.getY(), corner2.getZ())
                );
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (value instanceof BlockState blockState) {
            Identifier id = Registries.BLOCK.getId(blockState.getBlock());
            return Registries.BLOCK.get(id).getDefaultState();
        }
        throw new SnapshotIsolationException(
                "Unable to isolate snapshot for " + value.getClass().getName() + "; refusing to alias live data");
    }
}
