package com.nodecraft.nodesystem.datatypes;

import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable ENTITY_INFO snapshot captured at hit / query time.
 * Never carries a live {@link Entity} reference.
 */
public record EntityInfoData(
    UUID uuid,
    String entityTypeId,
    PointData position,
    PointData boundingBoxMin,
    PointData boundingBoxMax,
    @Nullable String displayName
) {

    public EntityInfoData {
        Objects.requireNonNull(uuid, "uuid");
        entityTypeId = entityTypeId == null ? "" : entityTypeId;
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(boundingBoxMin, "boundingBoxMin");
        Objects.requireNonNull(boundingBoxMax, "boundingBoxMax");
        if (displayName != null && displayName.isBlank()) {
            displayName = null;
        }
    }

    /**
     * Builds a snapshot from a live entity. Returns {@code null} when {@code entity} is null
     * or position is non-finite.
     */
    public static @Nullable EntityInfoData fromEntity(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        Box box = entity.getBoundingBox();
        String typeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
        String name = null;
        try {
            String raw = entity.getName().getString();
            if (raw != null && !raw.isBlank()) {
                name = raw;
            }
        } catch (RuntimeException ignored) {
            // Display name is optional.
        }
        return new EntityInfoData(
            entity.getUuid(),
            typeId,
            new PointData(x, y, z),
            new PointData(box.minX, box.minY, box.minZ),
            new PointData(box.maxX, box.maxY, box.maxZ),
            name
        );
    }
}
