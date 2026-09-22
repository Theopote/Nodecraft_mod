package com.nodecraft.gui.recommendation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NodeRecommendationRulesLoaderTest {

    @Test
    void loadsRulesWithDefaults() {
        NodeRecommendationRules rules = NodeRecommendationRulesLoader.load();
        assertNotNull(rules);
        assertEquals(2, rules.version);
        assertNotNull(rules.defaults);
        assertFalse(rules.defaults.workflowOrder.isEmpty());
    }

    @Test
    void coalescesStringListOutputTypeRules() {
        NodeRecommendationRules rules = NodeRecommendationRulesLoader.load();
        NodeRecommendationRules.OutputTypeRule geometryRule = rules.outputTypes.get("geometry");
        assertNotNull(geometryRule);
        assertNotNull(geometryRule.downstream);
        assertFalse(geometryRule.downstream.isEmpty());
        assertEquals(
                "transform.basic_transforms.transform_geometry",
                geometryRule.downstream.get(0).nodeId);
    }
}
