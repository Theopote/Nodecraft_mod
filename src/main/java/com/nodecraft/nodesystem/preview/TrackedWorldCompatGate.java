package com.nodecraft.nodesystem.preview;

/**
 * P3 freeze gate for {@link PreviewBackend#TRACKED_WORLD}.
 * <p>
 * Default: compat closed — new product selections cannot choose TRACKED_WORLD.
 * Opt-in: {@code -Dnodecraft.preview.trackedWorldCompat=true}.
 * <p>
 * Graph restore coerces TRACKED_WORLD → GHOST ({@link #sanitizeRestored}).
 * Explicit new selection still requires {@code -Dnodecraft.preview.trackedWorldCompat=true}.
 * Full deletion waits until {@link TrackedWorldCapabilityInventory#GHOST_GAPS} are closed.
 * See {@code docs/architecture/preview-world-boundary.md}.
 */
public final class TrackedWorldCompatGate {

    public static final String SYSTEM_PROPERTY = "nodecraft.preview.trackedWorldCompat";

    private TrackedWorldCompatGate() {
    }

    /** Whether users may newly select TRACKED_WORLD. */
    public static boolean isCompatSelectionEnabled() {
        return Boolean.parseBoolean(System.getProperty(SYSTEM_PROPERTY, "false"));
    }

    /**
     * Backends offered in the property editor for the current value.
     * When compat is off and the node is already on GHOST, only GHOST is listed.
     * Legacy TRACKED_WORLD nodes still see both options so they can migrate off.
     */
    public static PreviewBackend[] backendsForEditor(PreviewBackend current) {
        if (isCompatSelectionEnabled()) {
            return PreviewBackend.values();
        }
        if (current == PreviewBackend.TRACKED_WORLD) {
            return PreviewBackend.values();
        }
        return new PreviewBackend[]{PreviewBackend.GHOST};
    }

    /** Sanitize an intentional UI / API selection (not graph restore). */
    public static PreviewBackend sanitizeSelection(PreviewBackend requested) {
        PreviewBackend value = requested != null ? requested : PreviewBackend.GHOST;
        if (value == PreviewBackend.TRACKED_WORLD && !isCompatSelectionEnabled()) {
            return PreviewBackend.GHOST;
        }
        return value;
    }

    /** Sanitize a value loaded from saved node state (TRACKED_WORLD → GHOST). */
    public static PreviewBackend sanitizeRestored(PreviewBackend requested) {
        PreviewBackend value = requested != null ? requested : PreviewBackend.GHOST;
        if (value == PreviewBackend.TRACKED_WORLD) {
            return PreviewBackend.GHOST;
        }
        return value;
    }

    /**
     * Runtime note: TRACKED_WORLD dispatch stays available only when compat selection is enabled.
     */
    public static boolean allowTrackedWorldDispatch() {
        return isCompatSelectionEnabled();
    }
}
