package com.nodecraft.gui.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeIconPathResolverTest {

    @Test
    void candidatesPreferPngThenSvgForEachLogicalResource() {
        List<NodeIconPathResolver.Candidate> candidates = NodeIconPathResolver.candidates(
                "geometry.combine.geometry",
                "geometry.combine",
                "geometry/combine/custom"
        );

        assertEquals(NodeIconPathResolver.CandidateKind.EXPLICIT, candidates.get(0).kind());
        assertEquals(
                "textures/icons/nodes/geometry/combine/custom.png",
                candidates.get(0).resourcePath()
        );
        assertEquals(NodeIconPathResolver.CandidateKind.EXPLICIT, candidates.get(1).kind());
        assertEquals(
                "textures/icons/nodes/geometry/combine/custom.svg",
                candidates.get(1).resourcePath()
        );
        assertEquals(NodeIconPathResolver.CandidateKind.NODE_ID, candidates.get(2).kind());
        assertEquals(
                "textures/icons/nodes/geometry/combine/geometry.png",
                candidates.get(2).resourcePath()
        );
        assertEquals(
                "textures/icons/nodes/geometry/combine/geometry.svg",
                candidates.get(3).resourcePath()
        );
        assertEquals(NodeIconPathResolver.CandidateKind.FALLBACK, candidates.getLast().kind());
        assertEquals("geometry", candidates.getLast().fallbackCategory());
        assertNull(candidates.getLast().resourcePath());
    }

    @Test
    void buildNodePathHandlesDottedIdsAsPng() {
        assertEquals(
                "textures/icons/nodes/math/scalar_math/add.png",
                NodeIconPathResolver.buildNodePath("math.scalar_math.add")
        );
    }

    @Test
    void normalizeIconPathAcceptsNamespaceAndCatShortcut() {
        assertEquals(
                "textures/icons/nodes/geometry/geometry.png",
                NodeIconPathResolver.normalizeIconPath("cat:geometry")
        );
        assertEquals(
                "textures/icons/nodes/output/preview/blocks.svg",
                NodeIconPathResolver.normalizeIconPath("nodecraft:textures/icons/nodes/output/preview/blocks.svg")
        );
        assertEquals(
                "textures/icons/nodes/output/preview/blocks.png",
                NodeIconPathResolver.normalizeIconPath("nodecraft:textures/icons/nodes/output/preview/blocks.png")
        );
        assertNull(NodeIconPathResolver.normalizeIconPath("otherns:textures/icons/nodes/x.svg"));
    }

    @Test
    void logicalKeyIsStableAndNormalized() {
        String a = NodeIconPathResolver.logicalKey("Geometry.Combine.Geometry", "Geometry.Combine", " Foo ");
        String b = NodeIconPathResolver.logicalKey("geometry.combine.geometry", "geometry.combine", "foo");
        assertEquals(a, b);
        assertTrue(a.contains("geometry.combine.geometry"));
    }

    @Test
    void stripExtensionRemovesPngAndSvg() {
        assertEquals(
                "textures/icons/nodes/math/add",
                NodeIconPathResolver.stripExtension("textures/icons/nodes/math/add.png")
        );
        assertEquals(
                "textures/icons/nodes/math/add",
                NodeIconPathResolver.stripExtension("textures/icons/nodes/math/add.svg")
        );
    }
}
