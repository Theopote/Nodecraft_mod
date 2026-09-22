package com.nodecraft.gui.components.search;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.nodes.math.list_sequence.CreateListNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeSearchMatcherTest {

    @Test
    void emptyTermMatchesEverything() {
        assertTrue(NodeSearchMatcher.matches("math.trig.sine", "Sine", "desc", "math.trig", ""));
        assertTrue(NodeSearchMatcher.matches("math.trig.sine", "Sine", "desc", "math.trig", null));
        assertTrue(NodeSearchMatcher.matchesNode(
                new NodeInfo("math.trig.sine", "Sine", "desc", "math.trig", 0, CreateListNode.class),
                "   "));
    }

    @Test
    void singleTokenMatchesNameIdDescriptionOrCategory() {
        assertTrue(NodeSearchMatcher.matches(
                "geometry.primitives.box", "Box", "solid box", "geometry.primitives", "box"));
        assertTrue(NodeSearchMatcher.matches(
                "geometry.primitives.box", "Solid", "a cube", "geometry.primitives", "cube"));
        assertTrue(NodeSearchMatcher.matches(
                "geometry.primitives.box", "Solid", "desc", "geometry.primitives", "primitives"));
        assertFalse(NodeSearchMatcher.matches(
                "geometry.primitives.box", "Solid", "desc", "geometry.primitives", "wall"));
    }

    @Test
    void multiTokenRequiresAllTokens() {
        assertTrue(NodeSearchMatcher.matches(
                "geometry.architectural_primitives.wall",
                "Wall With Openings",
                "Architectural wall",
                "geometry.architectural_primitives",
                "wall arch"));
        assertFalse(NodeSearchMatcher.matches(
                "geometry.architectural_primitives.wall",
                "Wall With Openings",
                "Architectural wall",
                "geometry.architectural_primitives",
                "wall stair"));
    }

    @Test
    void categoryMatcherUsesIdAndDisplayName() {
        assertTrue(NodeSearchMatcher.matchesCategory("math.trig", "Trigonometry", "trig"));
        assertTrue(NodeSearchMatcher.matchesCategory("math.trig", "Trigonometry", "trigo"));
        assertFalse(NodeSearchMatcher.matchesCategory("math.trig", "Trigonometry", "list"));
    }
}
