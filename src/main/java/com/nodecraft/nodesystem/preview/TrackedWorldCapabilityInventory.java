package com.nodecraft.nodesystem.preview;

import java.util.List;

/**
 * P2 capability inventory: why {@link PreviewBackend#TRACKED_WORLD} still exists
 * and which production owners may touch it.
 * <p>
 * Source of truth companion to {@code docs/architecture/preview-world-boundary.md}.
 * New TRACKED_WORLD product features must add a row here (and justify a Ghost gap).
 * <p>
 * P3 freeze: new selections are gated by {@link TrackedWorldCompatGate}
 * ({@code -Dnodecraft.preview.trackedWorldCompat=true}). Full deletion remains
 * deferred until {@link #GHOST_GAPS} are closed.
 */
public final class TrackedWorldCapabilityInventory {

    /** Feature freeze is active; do not add new TRACKED_WORLD product surfaces. */
    public static final boolean FEATURE_FROZEN = true;

    public enum Role {
        /** User-/node-facing choice that can request TRACKED_WORLD. */
        PRODUCT_ENTRY,
        /** Routes PreviewRequest to tracked placement. */
        DISPATCH,
        /** Mutate-then-restore implementation. */
        IMPLEMENTATION,
        /** Cleanup / unload / backend-switch hygiene (not a feature). */
        LIFECYCLE_CLEANUP,
        /** Tests / gametests only. */
        TEST_ONLY
    }

    public enum Verdict {
        /** Kept because Ghost cannot cover a documented gap yet. */
        KEEP_FOR_GAP,
        /** Kept only to clean up tracked state safely. */
        KEEP_LIFECYCLE,
        /** Already Ghost-only; listed for completeness. */
        ALREADY_GHOST
    }

    /**
     * Capabilities real-block preview provides that Ghost overlays do not.
     * Retirement of TRACKED_WORLD requires closing these (or accepting Bake-only).
     */
    public static final List<String> GHOST_GAPS = List.of(
            "Collision / pathfinding / entity interaction against preview voxels",
            "Server or client world queries that read BlockState at preview cells",
            "Lighting / occlusion / screenshot parity with placed blocks",
            "Third-party or vanilla systems that only see real world blocks"
    );

    public record Entry(
            String owner,
            Role role,
            Verdict verdict,
            String notes
    ) {
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry(
                    "GeometryViewerNode.previewBackend",
                    Role.PRODUCT_ENTRY,
                    Verdict.KEEP_FOR_GAP,
                    "Only catalog opt-in (Advanced). Default GHOST. Saved TRACKED_WORLD still loads."
            ),
            new Entry(
                    "PreviewManager.showPreview(BLOCKS, TRACKED_WORLD)",
                    Role.DISPATCH,
                    Verdict.KEEP_FOR_GAP,
                    "Sole dispatch into TrackedPreviewPlacementService; non-BLOCKS kinds reject non-GHOST."
            ),
            new Entry(
                    "TrackedPreviewPlacementService",
                    Role.IMPLEMENTATION,
                    Verdict.KEEP_FOR_GAP,
                    "Owns mutate/restore, limits, world-thread affinity."
            ),
            new Entry(
                    "PreviewManager backend-switch / hideNodePreviews",
                    Role.LIFECYCLE_CLEANUP,
                    Verdict.KEEP_LIFECYCLE,
                    "Clears tracked remnants when switching to GHOST or hiding node previews."
            ),
            new Entry(
                    "NodecraftLifecycleManager",
                    Role.LIFECYCLE_CLEANUP,
                    Verdict.KEEP_LIFECYCLE,
                    "clearAllTrackedPreviews on editor/world teardown."
            ),
            new Entry(
                    "PreviewBlocksNode",
                    Role.PRODUCT_ENTRY,
                    Verdict.ALREADY_GHOST,
                    "Hardcodes PreviewBackend.GHOST — do not reintroduce TRACKED_WORLD."
            ),
            new Entry(
                    "PreviewManager showCurve/points/vectors/regions/… helpers",
                    Role.PRODUCT_ENTRY,
                    Verdict.ALREADY_GHOST,
                    "All helper entry points pass PreviewBackend.GHOST."
            )
    );

    private TrackedWorldCapabilityInventory() {
    }

    public static List<Entry> productEntries() {
        return ENTRIES.stream().filter(e -> e.role() == Role.PRODUCT_ENTRY).toList();
    }

    public static List<Entry> keepForGapEntries() {
        return ENTRIES.stream().filter(e -> e.verdict() == Verdict.KEEP_FOR_GAP).toList();
    }
}
