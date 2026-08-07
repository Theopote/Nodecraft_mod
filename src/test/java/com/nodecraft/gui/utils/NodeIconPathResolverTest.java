package com.nodecraft.gui.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeIconPathResolverTest {

    @Test
    void candidatesPreferExplicitThenNodeIdThenCategoryThenFallback() {
        List<NodeIconPathResolver.Candidate> candidates = NodeIconPathResolver.candidates(
                "geometry.boolean.union",
                "geometry.boolean",
                "geometry/boolean/custom"
        );

        assertEquals(NodeIconPathResolver.CandidateKind.EXPLICIT, candidates.get(0).kind());
        assertEquals(
                "textures/icons/nodes/geometry/boolean/custom.svg",
                candidates.get(0).resourcePath()
        );
        assertEquals(NodeIconPathResolver.CandidateKind.NODE_ID, candidates.get(1).kind());
        assertEquals(
                "textures/icons/nodes/geometry/boolean/union.svg",
                candidates.get(1).resourcePath()
        );
        assertEquals(NodeIconPathResolver.CandidateKind.SUBCATEGORY, candidates.get(2).kind());
        assertEquals(NodeIconPathResolver.CandidateKind.MAIN_CATEGORY, candidates.get(3).kind());
        assertEquals(NodeIconPathResolver.CandidateKind.FALLBACK, candidates.getLast().kind());
        assertEquals("geometry", candidates.getLast().fallbackCategory());
        assertNull(candidates.getLast().resourcePath());
    }

    @Test
    void buildNodePathHandlesDottedIds() {
        assertEquals(
                "textures/icons/nodes/math/scalar_math/add.svg",
                NodeIconPathResolver.buildNodePath("math.scalar_math.add")
        );
    }

    @Test
    void normalizeIconPathAcceptsNamespaceAndCatShortcut() {
        assertEquals(
                "textures/icons/nodes/geometry/geometry.svg",
                NodeIconPathResolver.normalizeIconPath("cat:geometry")
        );
        assertEquals(
                "textures/icons/nodes/output/preview/blocks.svg",
                NodeIconPathResolver.normalizeIconPath("nodecraft:textures/icons/nodes/output/preview/blocks.svg")
        );
        assertNull(NodeIconPathResolver.normalizeIconPath("otherns:textures/icons/nodes/x.svg"));
    }

    @Test
    void logicalKeyIsStableAndNormalized() {
        String a = NodeIconPathResolver.logicalKey("Geometry.Boolean.Union", "Geometry.Boolean", " Foo ");
        String b = NodeIconPathResolver.logicalKey("geometry.boolean.union", "geometry.boolean", "foo");
        assertEquals(a, b);
        assertTrue(a.contains("geometry.boolean.union"));
    }
}
