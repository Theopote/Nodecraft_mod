package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanNode;
import com.nodecraft.nodesystem.semantic.NodeCapability;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlanCapabilityCoverageCatalogTest {

    @Test
    void presentFromPlanUsesCatalogDerivedCaps() {
        AiGraphPlan plan = new AiGraphPlan(
                "wall",
                List.of(new AiPlanNode("n1", "geometry.architectural_primitives.wall_slab", 0, 0, null)),
                List.of(),
                List.of()
        );
        Set<NodeCapability> present = AiPlanCapabilityCoverage.presentFromPlan(plan);
        assertTrue(present.contains(NodeCapability.WALL));
        assertFalse(present.contains(NodeCapability.WINDOW));
    }

    @Test
    void capabilitiesForTypeIdDelegatesToCatalog() {
        Set<NodeCapability> caps =
                AiPlanCapabilityCoverage.capabilitiesForTypeId("output.preview.preview_geometry");
        assertTrue(caps.contains(NodeCapability.PREVIEW));
    }
}
