package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalog-wide getNodeState / setNodeState roundtrip fence.
 */
class NodeStateSerDeContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        assertTrue(registry.getNodeCount() > 0);
    }

    @Test
    void defaultStateRoundTripsForInstantiableNodes() {
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (String nodeId : registry.getAllNodeIds()) {
            INode original;
            try {
                original = registry.createNodeInstance(nodeId);
            } catch (Exception | LinkageError e) {
                continue;
            }
            checked++;

            Object state;
            try {
                state = original.getNodeState();
            } catch (Throwable e) {
                failures.add(nodeId + " getNodeState: " + rootMessage(e));
                continue;
            }

            INode reloaded;
            try {
                reloaded = registry.createNodeInstance(nodeId);
            } catch (Throwable e) {
                failures.add(nodeId + " recreate: " + rootMessage(e));
                continue;
            }

            try {
                reloaded.setNodeState(state);
            } catch (Throwable e) {
                failures.add(nodeId + " setNodeState: " + rootMessage(e));
                continue;
            }

            Object roundTripped;
            try {
                roundTripped = reloaded.getNodeState();
            } catch (Throwable e) {
                failures.add(nodeId + " get after set: " + rootMessage(e));
                continue;
            }

            if (state instanceof Map<?, ?> originalMap && roundTripped instanceof Map<?, ?> reloadedMap) {
                for (Map.Entry<?, ?> entry : originalMap.entrySet()) {
                    if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                        continue;
                    }
                    if (!reloadedMap.containsKey(key)) {
                        failures.add(nodeId + " missing key after roundtrip: " + key);
                        break;
                    }
                    if (!valuesEquivalent(entry.getValue(), reloadedMap.get(key))) {
                        failures.add(nodeId + " value mismatch for key=" + key
                                + " before=" + entry.getValue() + " after=" + reloadedMap.get(key));
                        break;
                    }
                }
            }
        }

        assertTrue(checked > 0, "expected to instantiate at least one node");
        double failureRatio = failures.isEmpty() ? 0.0 : (double) failures.size() / (double) registry.getNodeCount();
        assertTrue(
                failureRatio < 0.25,
                "too many ser/de failures (" + failures.size() + "/" + registry.getNodeCount()
                        + " checked=" + checked + "): " + preview(failures)
        );
    }

    private static boolean valuesEquivalent(Object left, Object right) {
        if (Objects.equals(left, right)) {
            return true;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
        }
        if (left instanceof Enum<?> leftEnum && right instanceof CharSequence sequence) {
            return leftEnum.name().equals(sequence.toString());
        }
        if (right instanceof Enum<?> rightEnum && left instanceof CharSequence sequence) {
            return rightEnum.name().equals(sequence.toString());
        }
        return false;
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
