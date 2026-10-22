package com.nodecraft.nodesystem.nodes.pattern.lsystem;

import com.nodecraft.gui.editor.impl.ICustomUINode;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LSystemRuleNodeDraftTest {

    @Test
    void undrivenUsesDraftValuesIncludingEmptyProduction() {
        LSystemRuleNode node = new LSystemRuleNode();
        assertInstanceOf(ICustomUINode.class, node);
        node.setDraftSymbol("X");
        node.setDraftProduction("");
        node.setWeight(2.0d);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        LSystemRule rule = assertInstanceOf(LSystemRule.class, node.getOutput("output_rule"));
        assertEquals("X", rule.symbol());
        assertEquals("", rule.production());
        assertEquals(2.0d, rule.weight(), 1e-9);
    }

    @Test
    void connectedSymbolOverridesDraft() {
        LSystemRuleNode node = new LSystemRuleNode();
        node.setDraftSymbol("DRAFT");
        node.setDraftProduction("AAA");
        node.setInput("input_symbol", "F");
        node.setInput("input_production", "F[+F]");
        node.processNode(null);

        LSystemRule rule = assertInstanceOf(LSystemRule.class, node.getOutput("output_rule"));
        assertEquals("F", rule.symbol());
        assertEquals("F[+F]", rule.production());
    }

    @Test
    void whitespaceOnlySymbolIsInvalid() {
        LSystemRuleNode node = new LSystemRuleNode();
        node.setDraftSymbol("   ");
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals("Symbol is required", node.getOutput("output_error"));
    }

    @Test
    void nodeStateRoundTripsDrafts() {
        LSystemRuleNode node = new LSystemRuleNode();
        node.setDraftSymbol("A");
        node.setDraftProduction("A+A");
        node.setWeight(0.5d);
        Object state = node.getNodeState();
        assertInstanceOf(Map.class, state);

        LSystemRuleNode restored = new LSystemRuleNode();
        restored.setNodeState(state);
        assertEquals("A", restored.getDraftSymbol());
        assertEquals("A+A", restored.getDraftProduction());
        assertEquals(0.5d, restored.getWeight(), 1e-9);
    }

    @Test
    void customUIHeightIsFinite() {
        System.clearProperty("nodecraft.customui.headlessMetrics");
        LSystemRuleNode node = new LSystemRuleNode();
        float height = ((ICustomUINode) node).getCustomUIHeight();
        assertTrue(Float.isFinite(height) && height > 0.0f);
        assertFalse(Float.isNaN(height));
    }
}
