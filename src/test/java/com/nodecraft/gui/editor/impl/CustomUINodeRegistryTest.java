package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomUINodeRegistryTest {

    private static final Set<String> PRIORITY_A_SAMPLES = Set.of(
            "input.numeric.float_slider",
            "input.values.boolean_toggle",
            "math.list.create_list",
            "input.type_selectors.block_type_selector"
    );

    @BeforeAll
    static void initRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void registryContainsConcreteCustomUINodeClasses() {
        List<String> customNodes = new ArrayList<>();
        for (String typeId : NodeRegistry.getInstance().getAllNodeIds()) {
            NodeInfo info = NodeRegistry.getInstance().getNodeInfo(typeId);
            if (info == null || info.getNodeClass() == null) {
                continue;
            }
            Class<?> nodeClass = info.getNodeClass();
            if (BaseCustomUINode.class.isAssignableFrom(nodeClass)
                    && !Modifier.isAbstract(nodeClass.getModifiers())) {
                customNodes.add(typeId);
            }
        }

        assertFalse(customNodes.isEmpty(), "Expected registered BaseCustomUINode subclasses");
        assertTrue(customNodes.size() >= 40,
                "Expected at least 40 custom UI nodes, found " + customNodes.size());
    }

    @Test
    void priorityCustomUINodesAreRegistered() {
        for (String typeId : PRIORITY_A_SAMPLES) {
            NodeInfo info = NodeRegistry.getInstance().getNodeInfo(typeId);
            assertNotNull(info, "Missing node info for " + typeId);
            assertTrue(BaseCustomUINode.class.isAssignableFrom(info.getNodeClass()),
                    typeId + " should extend BaseCustomUINode");
        }
    }
}
