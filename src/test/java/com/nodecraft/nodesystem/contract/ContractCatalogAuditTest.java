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

/**
 * Emits baseline metrics for contract threshold tuning. Always passes; inspect Gradle output.
 */
class ContractCatalogAuditTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void printBaselineMetrics() {
        int total = registry.getNodeCount();
        int unitEligible = 0;
        int minecraftClientOnly = ContractAllowlists.minecraftClientOnlyNodeIds().size();
        List<String> missingNodeInfo = new ArrayList<>();
        List<String> instantiateFailures = new ArrayList<>();
        List<String> serDeFailures = new ArrayList<>();
        int instantiable = 0;

        for (String nodeId : registry.getAllNodeIds()) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null || info.getNodeClass() == null) {
                continue;
            }
            if (!ContractAllowlists.isUnitEligible(info.getNodeClass(), nodeId)) {
                continue;
            }
            unitEligible++;

            com.nodecraft.nodesystem.api.NodeInfo annotation =
                    info.getNodeClass().getAnnotation(com.nodecraft.nodesystem.api.NodeInfo.class);
            if (annotation == null) {
                missingNodeInfo.add(nodeId);
            }

            INode original;
            try {
                original = registry.createNodeInstance(nodeId);
            } catch (Exception | LinkageError e) {
                instantiateFailures.add(nodeId);
                continue;
            }
            instantiable++;

            try {
                Object state = original.getNodeState();
                INode reloaded = registry.createNodeInstance(nodeId);
                reloaded.setNodeState(state);
                Object roundTripped = reloaded.getNodeState();
                if (state instanceof Map<?, ?> originalMap && roundTripped instanceof Map<?, ?> reloadedMap) {
                    for (Map.Entry<?, ?> entry : originalMap.entrySet()) {
                        if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                            continue;
                        }
                        if (!reloadedMap.containsKey(key)
                                || !valuesEquivalent(entry.getValue(), reloadedMap.get(key))) {
                            serDeFailures.add(nodeId);
                            break;
                        }
                    }
                }
            } catch (Throwable e) {
                serDeFailures.add(nodeId);
            }
        }

        System.out.println("=== Contract catalog audit (phase " + ContractThresholds.PHASE + ") ===");
        System.out.println("registry total: " + total);
        System.out.println("UNIT-eligible: " + unitEligible);
        System.out.println("allowlisted MINECRAFT_CLIENT ids: " + minecraftClientOnly);
        System.out.println("missing @NodeInfo: " + missingNodeInfo.size()
                + " (" + pct(missingNodeInfo.size(), unitEligible) + "% of UNIT-eligible, ceiling "
                + pctRatio(ContractThresholds.MAX_MISSING_NODE_INFO_RATIO) + ")");
        System.out.println("instantiate failures: " + instantiateFailures.size()
                + " (" + pct(instantiateFailures.size(), unitEligible) + "% of UNIT-eligible, ceiling "
                + pctRatio(ContractThresholds.MAX_INSTANTIATE_FAILURE_RATIO) + ")");
        System.out.println("instantiable checked: " + instantiable);
        System.out.println("ser/de failures: " + serDeFailures.size()
                + " (" + pct(serDeFailures.size(), instantiable) + "% of checked, ceiling "
                + pctRatio(ContractThresholds.MAX_SERDE_FAILURE_RATIO) + ")");
        if (!missingNodeInfo.isEmpty()) {
            System.out.println("missing @NodeInfo ids: " + missingNodeInfo);
        }
        if (!instantiateFailures.isEmpty()) {
            System.out.println("instantiate failure ids: " + instantiateFailures);
        }
        if (!serDeFailures.isEmpty()) {
            System.out.println("ser/de failure ids: " + serDeFailures);
        }
    }

    private static double pct(int part, int whole) {
        if (whole <= 0) {
            return 0.0;
        }
        return Math.round(part * 10000.0 / whole) / 100.0;
    }

    private static String pctRatio(double ratio) {
        return String.format("%.0f%%", ratio * 100.0);
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
