package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Headless contract only: registry, instantiation, {@code hasCustomUI()}, and safe metrics access.
 *
 * <p><strong>Does not</strong> prove node-specific {@code calculateUIHeight}/{@code calculateMinUIWidth}
 * values. Under {@code -Dnodecraft.customui.headlessMetrics=true}, BaseCustomUINode returns
 * placeholder sizes to avoid native ImGui crashes. Real layout belongs to the in-game layout contract.
 *
 * @see docs/custom-ui-node-audit.md
 */
class CustomUIHeadlessContractTest {

    @BeforeAll
    static void init() {
        System.setProperty("nodecraft.customui.headlessMetrics", "true");
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void everyRegisteredICustomUINodeInstantiatesWithHasCustomUI() {
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

                // Headless placeholders only — not evidence of real in-game layout.
                float height = custom.getCustomUIHeight();
                float width = custom.getMinRequiredUIWidth();
                if (!Float.isFinite(height) || height < 0f) {
                    failures.add(typeId + " headless height=" + height);
                }
                if (!Float.isFinite(width) || width < 0f) {
                    failures.add(typeId + " headless width=" + width);
                }
            } catch (Throwable t) {
                failures.add(typeId + " threw " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }

        assertFalse(audited.isEmpty());
        assertEquals(CustomUINodeAuditTypeIds.ALL, audited,
                "Runtime ICustomUINode set must match audit list");
        if (!failures.isEmpty()) {
            fail("Headless contract failures:\n  " + String.join("\n  ", failures));
        }
    }

    @Test
    void auditInventoryHasExactlyFiftyCustomUINodes() {
        assertEquals(50, CustomUINodeAuditTypeIds.ALL.size());
    }
}
