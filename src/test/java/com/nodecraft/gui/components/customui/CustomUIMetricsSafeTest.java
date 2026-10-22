package com.nodecraft.gui.components.customui;

import com.nodecraft.gui.editor.impl.ICustomUINode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomUIMetricsSafeTest {

    @BeforeAll
    static void initRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void metricsFallbackWithoutImGuiContext() {
        assertFalse(CustomUIMetricsSafe.isOnRenderThread());
        assertFalse(CustomUIMetricsSafe.hasImGuiContext());
        assertTrue(CustomUIMetricsSafe.frameHeight() > 0.0f);
        assertTrue(CustomUIMetricsSafe.textLineHeight() > 0.0f);
    }

    @Test
    void boundContextOffRenderThreadUsesFallbackWithoutNativeCalls() {
        CustomUIMetricsSafe.bindEditorContext();
        try {
            assertFalse(CustomUIMetricsSafe.isOnRenderThread());
            assertFalse(CustomUIMetricsSafe.hasImGuiContext());
            assertEquals(CustomUIMetricsSafe.FALLBACK_FRAME_HEIGHT, CustomUIMetricsSafe.frameHeight());
            assertEquals(CustomUIMetricsSafe.FALLBACK_TEXT_LINE_HEIGHT, CustomUIMetricsSafe.textLineHeight());
        } finally {
            CustomUIMetricsSafe.unbindEditorContext();
        }
    }

    @Test
    void phase1NodesExposeFiniteHeightWithoutHeadlessFlag() {
        assertFalse(CustomUIMetricsSafe.hasImGuiContext(),
            "Unit tests must not bind native ImGui — metrics use fallbacks");
        System.clearProperty("nodecraft.customui.headlessMetrics");
        String[] phase1TypeIds = {
            "math.scalar_math.graph_mapper",
            "math.data_tree.viewer",
            "material.basic_assignment.create_block_palette",
            "material.basic_assignment.weighted_palette",
            "material.block_state.build_block_state",
            "material.gradient_mapping.gradient_ramp_map"
        };
        for (String typeId : phase1TypeIds) {
            ICustomUINode node = (ICustomUINode) NodeRegistry.getInstance().createNodeInstance(typeId);
            float height = node.getCustomUIHeight();
            float width = node.getMinRequiredUIWidth();
            assertTrue(Float.isFinite(height) && height > 0.0f, typeId + " height");
            assertTrue(Float.isFinite(width) && width > 0.0f, typeId + " width");
        }
    }
}
