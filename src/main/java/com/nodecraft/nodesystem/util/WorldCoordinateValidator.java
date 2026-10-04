package com.nodecraft.nodesystem.util;

import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Shared world coordinate range checks for selection / spatial nodes.
 */
public final class WorldCoordinateValidator {

    /** Minecraft horizontal world border hard limit used across selection/query nodes. */
    public static final int MAX_XZ = 30_000_000;

    /** Fallback vertical range when no world is available (Overworld-like). */
    public static final int FALLBACK_MIN_Y = -64;

    public static final int FALLBACK_MAX_Y = 319;

    public record Result(boolean valid, String error) {
        public static Result ok() {
            return new Result(true, "");
        }

        public static Result fail(String error) {
            return new Result(false, error == null ? "" : error);
        }
    }

    private WorldCoordinateValidator() {
    }

    public static Result validate(int x, int y, int z, @Nullable World world) {
        int minY = FALLBACK_MIN_Y;
        int maxY = FALLBACK_MAX_Y;
        if (world != null) {
            try {
                minY = world.getBottomY();
                maxY = world.getTopYInclusive();
            } catch (Throwable ignored) {
                // Keep fallback when world height APIs are unavailable.
            }
        }

        if (y < minY || y > maxY) {
            return Result.fail("Y coordinate out of world range (" + minY + " to " + maxY + "): " + y);
        }
        if (x < -MAX_XZ || x > MAX_XZ) {
            return Result.fail("X coordinate out of world range (±" + MAX_XZ + "): " + x);
        }
        if (z < -MAX_XZ || z > MAX_XZ) {
            return Result.fail("Z coordinate out of world range (±" + MAX_XZ + "): " + z);
        }
        return Result.ok();
    }

    /**
     * Rounds a finite absolute world Y to an inclusive block Y in
     * {@link #FALLBACK_MIN_Y}..{@link #FALLBACK_MAX_Y}.
     * Returns {@code null} when non-finite or outside that inclusive range after rounding.
     */
    public static @Nullable Integer roundToBlockY(double y) {
        if (!Double.isFinite(y)) {
            return null;
        }
        long rounded = Math.round(y);
        if (rounded < FALLBACK_MIN_Y || rounded > FALLBACK_MAX_Y) {
            return null;
        }
        return (int) rounded;
    }
}
