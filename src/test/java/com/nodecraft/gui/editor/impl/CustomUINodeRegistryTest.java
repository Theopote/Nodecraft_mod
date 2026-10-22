package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomUINodeRegistryTest {

    @BeforeAll
    static void initRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void auditListsExactlyFiftySixRegisteredCustomUINodes() {
        List<String> missing = new ArrayList<>();
        List<String> notCustomUi = new ArrayList<>();

        for (String typeId : CustomUINodeAuditTypeIds.ALL) {
            NodeInfo info = NodeRegistry.getInstance().getNodeInfo(typeId);
            if (info == null) {
                missing.add(typeId);
                continue;
            }
            if (!BaseCustomUINode.class.isAssignableFrom(info.getNodeClass())) {
                notCustomUi.add(typeId);
            }
        }

        assertTrue(missing.isEmpty(), "Missing audit type IDs: " + missing);
        assertTrue(notCustomUi.isEmpty(), "Audit type IDs not extending BaseCustomUINode: " + notCustomUi);
        assertEquals(56, CustomUINodeAuditTypeIds.ALL.size());
    }

    @Test
    void registryCustomUINodeCountMatchesAudit() {
        Set<String> registeredCustom = new HashSet<>();
        for (String typeId : NodeRegistry.getInstance().getAllNodeIds()) {
            NodeInfo info = NodeRegistry.getInstance().getNodeInfo(typeId);
            if (info == null || info.getNodeClass() == null) {
                continue;
            }
            Class<?> nodeClass = info.getNodeClass();
            if (BaseCustomUINode.class.isAssignableFrom(nodeClass)
                    && !Modifier.isAbstract(nodeClass.getModifiers())) {
                registeredCustom.add(typeId);
            }
        }

        assertEquals(CustomUINodeAuditTypeIds.ALL, registeredCustom,
                "Registry custom UI set must match audit list exactly");
    }
}
