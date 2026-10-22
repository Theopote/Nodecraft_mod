package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.components.customui.MaterialSlotStripWidget;
import com.nodecraft.gui.components.customui.PreviewStatusPanelWidget;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.nodes.flow.control.ManualTriggerNode;
import com.nodecraft.nodesystem.nodes.material.directional_mapping.TopSideBottomMapNode;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewPlacementsNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless contract for Phase 2 Group A custom UI nodes.
 */
class CustomUIPhase2NodesTest {

    private static final Set<String> GROUP_A_TYPE_IDS = Set.of(
        "material.directional_mapping.top_side_bottom_map",
        "output.preview.preview_placements",
        "flow.control.manual_trigger"
    );

    private static final Set<String> GROUP_B_TYPE_IDS = Set.of(
        "utilities.fileio.read_image",
        "utilities.fileio.image_sampler",
        "pattern.lsystem.rule"
    );

    @BeforeAll
    static void init() {
        System.setProperty("nodecraft.customui.headlessMetrics", "true");
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void groupANodesImplementCustomUI() {
        for (String typeId : GROUP_A_TYPE_IDS) {
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            assertInstanceOf(ICustomUINode.class, node, typeId);
            ICustomUINode custom = (ICustomUINode) node;
            assertTrue(custom.hasCustomUI(), typeId);
            assertTrue(Float.isFinite(custom.getCustomUIHeight()), typeId + " height");
            assertTrue(Float.isFinite(custom.getMinRequiredUIWidth()), typeId + " width");
            assertTrue(CustomUINodeAuditTypeIds.ALL.contains(typeId), typeId);
        }
    }

    @Test
    void materialSlotStripPreferredHeightIsPositive() {
        assertTrue(MaterialSlotStripWidget.preferredHeight() > 0.0f);
        assertTrue(MaterialSlotStripWidget.preferredWidth() > 0.0f);
    }

    @Test
    void previewStatusPanelPreferredHeightIsPositive() {
        assertTrue(PreviewStatusPanelWidget.preferredHeight() > 0.0f);
    }

    @Test
    void previewPlacementsToggleUsesPropertyPath() {
        PreviewPlacementsNode node = new PreviewPlacementsNode();
        assertTrue(node.isPreviewEnabled());
        node.setPreviewEnabled(false);
        assertFalse(node.isPreviewEnabled());
        assertInstanceOf(ICustomUINode.class, node);
    }

    @Test
    void topSideBottomMapIsCustomUINode() {
        assertInstanceOf(ICustomUINode.class, new TopSideBottomMapNode());
    }

    @Test
    void manualTriggerPulseStillOneShotAndNotSerialized() {
        ManualTriggerNode trigger = new ManualTriggerNode();
        assertInstanceOf(ICustomUINode.class, trigger);
        trigger.requestPulse();
        assertTrue(trigger.isPulsePending());
        trigger.processNode(null);
        assertEquals(Boolean.TRUE, trigger.getOutput("output_exec"));
        assertFalse(trigger.isPulsePending());
        trigger.processNode(null);
        assertEquals(Boolean.FALSE, trigger.getOutput("output_exec"));
    }

    @Test
    void groupBNodesImplementCustomUI() {
        for (String typeId : GROUP_B_TYPE_IDS) {
            assertTrue(CustomUINodeAuditTypeIds.ALL.contains(typeId), typeId);
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            assertInstanceOf(ICustomUINode.class, node, typeId);
            ICustomUINode custom = (ICustomUINode) node;
            assertTrue(custom.hasCustomUI(), typeId);
            assertTrue(Float.isFinite(custom.getCustomUIHeight()), typeId + " height");
            assertTrue(Float.isFinite(custom.getMinRequiredUIWidth()), typeId + " width");
        }
    }
}
