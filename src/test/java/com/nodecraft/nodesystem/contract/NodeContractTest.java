package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thin Node Contract suite — invariant checks over the full registered catalog.
 * <p>
 * Prefer these shared invariants over per-node unit tests for the 500+ node library.
 */
class NodeContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        assertTrue(registry.getNodeCount() > 0, "registry must contain nodes");
    }

    @Test
    void registeredNodeIdsAreUniqueAndNonBlank() {
        List<String> ids = registry.getAllNodeIds();
        Set<String> seen = new HashSet<>();
        List<String> blanks = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                blanks.add(String.valueOf(id));
                continue;
            }
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }
        assertTrue(blanks.isEmpty(), "blank node ids: " + blanks);
        assertTrue(duplicates.isEmpty(), "duplicate registry ids: " + duplicates);
    }

    @Test
    void annotationIdMatchesRegistryIdAndRuntimeTypeId() {
        List<String> mismatches = new ArrayList<>();
        List<String> missingAnnotation = new ArrayList<>();
        List<String> instantiateFailures = new ArrayList<>();

        for (String nodeId : registry.getAllNodeIds()) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null || info.getNodeClass() == null) {
                mismatches.add(nodeId + " -> missing NodeInfo/class");
                continue;
            }
            if (isNestedOrSynthetic(info.getNodeClass())) {
                continue;
            }

            com.nodecraft.nodesystem.api.NodeInfo annotation =
                    info.getNodeClass().getAnnotation(com.nodecraft.nodesystem.api.NodeInfo.class);
            if (annotation == null) {
                // Convention-registered production nodes are still allowed; track separately later.
                missingAnnotation.add(nodeId + " (" + info.getNodeClass().getName() + ")");
                continue;
            }

            String annotatedId = annotation.id() == null ? "" : annotation.id().trim().toLowerCase(Locale.ROOT);
            if (annotatedId.isEmpty()) {
                mismatches.add(nodeId + " -> empty @NodeInfo.id");
            } else if (!annotatedId.equals(nodeId)) {
                mismatches.add(nodeId + " -> @NodeInfo.id=" + annotatedId);
            }

            if (annotation.category() == null || annotation.category().isBlank()) {
                mismatches.add(nodeId + " -> blank @NodeInfo.category");
            }

            try {
                INode instance = registry.createNodeInstance(nodeId);
                String typeId = instance.getTypeId();
                if (typeId == null || !typeId.equalsIgnoreCase(nodeId)) {
                    mismatches.add(nodeId + " -> runtime typeId=" + typeId);
                }
            } catch (Exception | LinkageError e) {
                // Some nodes may require Minecraft registries at construction time.
                instantiateFailures.add(nodeId + " (" + rootMessage(e) + ")");
            }
        }

        assertTrue(mismatches.isEmpty(),
                "id/typeId/category mismatches (" + mismatches.size() + "): " + preview(mismatches));

        // Prefer @NodeInfo everywhere; allow a small convention-registered residual.
        double missingRatio = missingAnnotation.isEmpty()
                ? 0.0
                : (double) missingAnnotation.size() / (double) registry.getNodeCount();
        assertTrue(
                missingRatio < 0.05,
                "too many nodes missing @NodeInfo (" + missingAnnotation.size() + "/" + registry.getNodeCount()
                        + "): " + preview(missingAnnotation)
        );

        // Instantiation failures are reported but do not fail the suite until env-free ctors are universal.
        // Keep a soft ceiling so sudden mass breakage is still visible.
        double failureRatio = instantiateFailures.isEmpty()
                ? 0.0
                : (double) instantiateFailures.size() / (double) registry.getNodeCount();
        assertTrue(
                failureRatio < 0.25,
                "too many instantiate failures (" + instantiateFailures.size() + "/" + registry.getNodeCount()
                        + "): " + preview(instantiateFailures)
        );
    }

    @Test
    void portIdsAreUniqueWithinEachInstantiableNode() {
        List<String> violations = new ArrayList<>();
        int checked = 0;

        for (String nodeId : registry.getAllNodeIds()) {
            INode instance;
            try {
                instance = registry.createNodeInstance(nodeId);
            } catch (Exception | LinkageError e) {
                continue;
            }
            checked++;
            Set<String> portIds = new HashSet<>();
            List<String> localDupes = new ArrayList<>();
            for (IPort port : concatPorts(instance)) {
                if (port == null || port.getId() == null || port.getId().isBlank()) {
                    localDupes.add("<blank>");
                    continue;
                }
                if (!portIds.add(port.getId())) {
                    localDupes.add(port.getId());
                }
            }
            if (!localDupes.isEmpty()) {
                violations.add(nodeId + " -> " + localDupes);
            }
        }

        assertTrue(checked > 0, "expected to instantiate at least one node for port checks");
        assertTrue(violations.isEmpty(),
                "duplicate/blank port ids (" + violations.size() + "): " + preview(violations));
    }

    @Test
    void annotationIdsAreUniqueAcrossRegisteredClasses() {
        Map<String, String> annotationToClass = new HashMap<>();
        List<String> duplicates = new ArrayList<>();

        for (String nodeId : registry.getAllNodeIds()) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null || info.getNodeClass() == null) {
                continue;
            }
            com.nodecraft.nodesystem.api.NodeInfo annotation =
                    info.getNodeClass().getAnnotation(com.nodecraft.nodesystem.api.NodeInfo.class);
            if (annotation == null || annotation.id() == null || annotation.id().isBlank()) {
                continue;
            }
            String annotatedId = annotation.id().trim().toLowerCase(Locale.ROOT);
            String previous = annotationToClass.put(annotatedId, info.getNodeClass().getName());
            if (previous != null && !previous.equals(info.getNodeClass().getName())) {
                duplicates.add(annotatedId + " -> " + previous + " vs " + info.getNodeClass().getName());
            }
        }

        assertTrue(duplicates.isEmpty(), "duplicate @NodeInfo.id across classes: " + duplicates);
    }

    @Test
    void registryCategoriesAreNonBlankForAllNodes() {
        List<String> blanks = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null || info.getCategoryId() == null || info.getCategoryId().isBlank()) {
                blanks.add(nodeId);
            }
        }
        assertFalse(registry.getAllNodeIds().isEmpty());
        assertTrue(blanks.isEmpty(), "nodes with blank category: " + blanks);
    }

    private static List<IPort> concatPorts(INode node) {
        List<IPort> ports = new ArrayList<>();
        if (node.getInputPorts() != null) {
            ports.addAll(node.getInputPorts());
        }
        if (node.getOutputPorts() != null) {
            ports.addAll(node.getOutputPorts());
        }
        return ports;
    }

    private static boolean isNestedOrSynthetic(Class<?> type) {
        return type.isAnonymousClass()
                || type.isLocalClass()
                || type.getEnclosingClass() != null
                || type.getName().contains("$");
    }

    private static String preview(List<String> items) {
        int limit = Math.min(12, items.size());
        String body = String.join("; ", items.subList(0, limit));
        if (items.size() > limit) {
            body += "; ... +" + (items.size() - limit) + " more";
        }
        return body;
    }

    private static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return cursor.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
