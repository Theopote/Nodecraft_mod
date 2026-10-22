package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runtime registry audit for every concrete {@link ICustomUINode} (not inheritance-only).
 * Metrics that touch ImGui are exercised under {@code -Dnodecraft.customui.headlessMetrics=true}.
 */
class CustomUIRuntimeMetricsAuditTest {

    @BeforeAll
    static void init() {
        System.setProperty("nodecraft.customui.headlessMetrics", "true");
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void everyRegisteredCustomUINodeHasFiniteNonNegativeMetrics() {
        Set<String> audited = new TreeSet<>();
        List<String> failures = new ArrayList<>();

        for (String typeId : NodeRegistry.getInstance().getAllNodeIds()) {
            NodeInfo info = NodeRegistry.getInstance().getNodeInfo(typeId);
            if (info == null || info.getNodeClass() == null) {
                continue;
            }
            Class<?> nodeClass = info.getNodeClass();
            if (Modifier.isAbstract(nodeClass.getModifiers())) {
                continue;
            }
            if (!ICustomUINode.class.isAssignableFrom(nodeClass)) {
                continue;
            }

            audited.add(typeId);
            try {
                INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
                assertInstanceOf(ICustomUINode.class, node, typeId);
                ICustomUINode custom = (ICustomUINode) node;

                assertTrue(custom.hasCustomUI(), typeId + " hasCustomUI() must be true");

                float height = custom.getCustomUIHeight();
                float width = custom.getMinRequiredUIWidth();

                if (!Float.isFinite(height) || height < 0f) {
                    failures.add(typeId + " height=" + height);
                }
                if (!Float.isFinite(width) || width < 0f) {
                    failures.add(typeId + " width=" + width);
                }
            } catch (Throwable t) {
                failures.add(typeId + " threw " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }

        assertFalse(audited.isEmpty(), "Expected registered ICustomUINode implementations");
        assertEquals(CustomUINodeAuditTypeIds.ALL, audited,
                "Runtime ICustomUINode set must match audit list");
        if (!failures.isEmpty()) {
            fail("Invalid custom UI metrics:\n  " + String.join("\n  ", failures));
        }
    }

    @Test
    void auditListContainsNoIntentionallyInactiveCustomUI() {
        // Phase 2 policy: every audited node is Restore; none are Panel-Only / inactive.
        assertEquals(44, CustomUINodeAuditTypeIds.ALL.size());
        for (String typeId : CustomUINodeAuditTypeIds.ALL) {
            assertFalse(typeId.toLowerCase(Locale.ROOT).contains("panel_only"),
                    "Unexpected panel-only marker in audit id: " + typeId);
        }
    }
}
