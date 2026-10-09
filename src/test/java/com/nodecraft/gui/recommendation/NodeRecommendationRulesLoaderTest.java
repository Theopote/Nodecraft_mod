package com.nodecraft.gui.recommendation;

import com.nodecraft.nodesystem.recommendation.NodeRecommendationRules;
import com.nodecraft.nodesystem.recommendation.NodeRecommendationRulesLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRecommendationRulesLoaderTest {

    @Test
    void loadsRulesWithDefaults() {
        NodeRecommendationRules rules = NodeRecommendationRulesLoader.load();
        assertNotNull(rules);
        assertEquals(3, rules.version);
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

    @Test
    void bumpRulesRevisionIsShared() {
        long before = NodeRecommendationRulesLoader.getRulesRevision();
        long after = NodeRecommendationRulesLoader.bumpRulesRevision();
        assertTrue(after > before);
        assertEquals(after, NodeRecommendationRulesLoader.getRulesRevision());
    }
}
