package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
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
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null || info.getNodeClass() == null) {
                continue;
            }
            if (!ContractAllowlists.isUnitEligible(info.getNodeClass(), nodeId)) {
                continue;
            }

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
                failures.add(nodeId + " getNodeState: " + ContractTestSupport.rootMessage(e));
                continue;
            }

            INode reloaded;
            try {
                reloaded = registry.createNodeInstance(nodeId);
            } catch (Throwable e) {
                failures.add(nodeId + " recreate: " + ContractTestSupport.rootMessage(e));
                continue;
            }

            try {
                reloaded.setNodeState(state);
            } catch (Throwable e) {
                failures.add(nodeId + " setNodeState: " + ContractTestSupport.rootMessage(e));
                continue;
            }

            Object roundTripped;
            try {
                roundTripped = reloaded.getNodeState();
            } catch (Throwable e) {
                failures.add(nodeId + " get after set: " + ContractTestSupport.rootMessage(e));
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

        assertTrue(checked > 0, "expected to instantiate at least one UNIT-eligible node");
        ContractTestSupport.assertRatioBelowCeiling(
                failures,
                checked,
                ContractThresholds.MAX_SERDE_FAILURE_RATIO,
                "ser/de failures",
                ContractThresholds.PHASE
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
}
