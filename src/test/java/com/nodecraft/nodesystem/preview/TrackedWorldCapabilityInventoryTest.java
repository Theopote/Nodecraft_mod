package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import com.nodecraft.nodesystem.preview.TrackedWorldCapabilityInventory.Entry;
import com.nodecraft.nodesystem.preview.TrackedWorldCapabilityInventory.Role;
import com.nodecraft.nodesystem.preview.TrackedWorldCapabilityInventory.Verdict;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the P2 TRACKED_WORLD capability inventory against silent expansion.
 */
class TrackedWorldCapabilityInventoryTest {

    @Test
    void ghostGapsAndOwnersAreDocumented() {
        assertTrue(TrackedWorldCapabilityInventory.FEATURE_FROZEN);
        assertFalse(TrackedWorldCapabilityInventory.GHOST_GAPS.isEmpty());
        assertTrue(TrackedWorldCapabilityInventory.ENTRIES.size() >= 5);

        List<Entry> product = TrackedWorldCapabilityInventory.productEntries();
        assertEquals(1, product.stream().filter(e -> e.verdict() == Verdict.KEEP_FOR_GAP).count(),
                "Only GeometryViewer should be the KEEP_FOR_GAP product entry");
        assertTrue(product.stream().anyMatch(e -> e.owner().startsWith("GeometryViewerNode")));
        assertTrue(product.stream().anyMatch(e -> e.verdict() == Verdict.ALREADY_GHOST));
    }

    @Test
    void geometryViewerDefaultsGhostAndIsSoleOptInSurface() {
        assertEquals(PreviewBackend.GHOST, new GeometryViewerNode().getPreviewBackend());

        long keepForGapOwners = TrackedWorldCapabilityInventory.keepForGapEntries().stream()
                .filter(e -> e.role() == Role.PRODUCT_ENTRY)
                .count();
        assertEquals(1, keepForGapOwners);
    }

    @Test
    void productionSourcesDoNotHardcodeTrackedWorldOutsideAllowlist() throws Exception {
        Path root = Path.of("src/main/java");
        assertTrue(Files.isDirectory(root), "expected src/main/java relative to module root");

        List<String> offenders;
        try (Stream<Path> stream = Files.walk(root)) {
            offenders = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.endsWith("PreviewBackend.java"))
                    .filter(p -> !p.endsWith("TrackedWorldCapabilityInventory.java"))
                    .filter(p -> !p.endsWith("TrackedWorldCompatGate.java"))
                    .filter(p -> !p.endsWith("TrackedPreviewPlacementService.java"))
                    .filter(p -> !p.endsWith("PreviewManager.java"))
                    .filter(p -> !p.endsWith("GeometryViewerNode.java"))
                    .filter(p -> !p.endsWith("GeometryViewerPropertySupport.java"))
                    .filter(p -> !p.endsWith("NodecraftLifecycleManager.java"))
                    .map(p -> {
                        try {
                            String text = Files.readString(p, StandardCharsets.UTF_8);
                            if (text.contains("TRACKED_WORLD") || text.contains("TrackedPreviewPlacementService")) {
                                return root.relativize(p).toString().replace('\\', '/');
                            }
                            return null;
                        } catch (Exception e) {
                            return p + ":" + e.getMessage();
                        }
                    })
                    .filter(s -> s != null)
                    .sorted()
                    .toList();
        }

        assertTrue(
                offenders.isEmpty(),
                "Unexpected TRACKED_WORLD / TrackedPreviewPlacementService references:\n"
                        + String.join("\n", offenders)
                        + "\nAdd to TrackedWorldCapabilityInventory + allowlist if intentional.");
    }
}
