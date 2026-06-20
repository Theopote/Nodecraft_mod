package com.nodecraft.gui.ai;

/**
 * Immutable, JSON-serializable snapshot of the world state visible to the AI planner.
 * Minecraft runtime objects must not escape into this model.
 */
public record AiWorldContextSnapshot(
        boolean available,
        String unavailableReason,
        long capturedAtMs,
        PlayerContext player,
        CrosshairTarget crosshairTarget,
        SelectedRegionContext selectedRegion
) {
    public record Vec3(double x, double y, double z) {
    }

    public record BlockCoordinate(int x, int y, int z) {
    }

    public record PlayerContext(
            String dimension,
            Vec3 feet,
            Vec3 eyes,
            float yaw,
            float pitch,
            Vec3 lookDirection
    ) {
    }

    public record CrosshairTarget(
            boolean hit,
            BlockCoordinate blockPos,
            Vec3 hitPosition,
            String side,
            String blockId
    ) {
        public static CrosshairTarget miss() {
            return new CrosshairTarget(false, null, null, null, null);
        }
    }

    public record SelectedRegionContext(
            String status,
            String sourceNodeId,
            BlockCoordinate min,
            BlockCoordinate max,
            BlockCoordinate size,
            Vec3 center,
            long volume
    ) {
        public static SelectedRegionContext none() {
            return new SelectedRegionContext("none", null, null, null, null, null, 0L);
        }

        public static SelectedRegionContext ambiguous() {
            return new SelectedRegionContext("ambiguous", null, null, null, null, null, 0L);
        }
    }
}
