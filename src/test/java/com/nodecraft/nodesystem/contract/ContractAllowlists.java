package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.ContractEnvironment;
import com.nodecraft.nodesystem.api.ContractTestEnvironment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Explicit allowlist for nodes that cannot be fully exercised in headless UNIT tests.
 */
final class ContractAllowlists {

    private static final String MINECRAFT_CLIENT_RESOURCE =
            "nodecraft/contracts/minecraft-client-only-nodes.txt";

    private static final Set<String> MINECRAFT_CLIENT_NODE_IDS = loadMinecraftClientOnlyNodes();

    private ContractAllowlists() {
    }

    static ContractTestEnvironment requiredEnvironment(Class<?> nodeClass, String nodeId) {
        ContractEnvironment annotation = nodeClass.getAnnotation(ContractEnvironment.class);
        if (annotation != null) {
            return annotation.value();
        }
        if (MINECRAFT_CLIENT_NODE_IDS.contains(normalize(nodeId))) {
            return ContractTestEnvironment.MINECRAFT_CLIENT;
        }
        return ContractTestEnvironment.UNIT;
    }

    static boolean isUnitEligible(Class<?> nodeClass, String nodeId) {
        return requiredEnvironment(nodeClass, nodeId) == ContractTestEnvironment.UNIT;
    }

    static Set<String> minecraftClientOnlyNodeIds() {
        return MINECRAFT_CLIENT_NODE_IDS;
    }

    private static Set<String> loadMinecraftClientOnlyNodes() {
        InputStream stream = ContractAllowlists.class.getClassLoader().getResourceAsStream(MINECRAFT_CLIENT_RESOURCE);
        if (stream == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                ids.add(normalize(trimmed));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + MINECRAFT_CLIENT_RESOURCE, e);
        }
        return Collections.unmodifiableSet(ids);
    }

    private static String normalize(String nodeId) {
        return nodeId.trim().toLowerCase(Locale.ROOT);
    }
}
