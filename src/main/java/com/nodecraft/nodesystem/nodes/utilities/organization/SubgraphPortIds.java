package com.nodecraft.nodesystem.nodes.utilities.organization;

import java.util.regex.Pattern;

/**
 * Shared dynamic port naming for {@link SubgraphNode} and editor subgraph wiring.
 */
public final class SubgraphPortIds {

    private static final Pattern NON_ALNUM_UNDERSCORE = Pattern.compile("[^a-zA-Z0-9_]");

    public static final String DYNAMIC_INPUT_PREFIX = "dynamic_input_key_";
    public static final String DYNAMIC_OUTPUT_PREFIX = "dynamic_output_key_";

    private SubgraphPortIds() {
    }

    public static String dynamicInputPortId(String key) {
        return DYNAMIC_INPUT_PREFIX + keyToken(key);
    }

    public static String dynamicOutputPortId(String key) {
        return DYNAMIC_OUTPUT_PREFIX + keyToken(key);
    }

    public static String keyFromInputPortId(String portId) {
        if (portId == null || !portId.startsWith(DYNAMIC_INPUT_PREFIX)) {
            return null;
        }
        return portId.substring(DYNAMIC_INPUT_PREFIX.length());
    }

    public static String keyFromOutputPortId(String portId) {
        if (portId == null || !portId.startsWith(DYNAMIC_OUTPUT_PREFIX)) {
            return null;
        }
        return portId.substring(DYNAMIC_OUTPUT_PREFIX.length());
    }

    public static String keyToken(String key) {
        if (key == null || key.isBlank()) {
            return "empty";
        }
        String normalized = NON_ALNUM_UNDERSCORE.matcher(key.trim()).replaceAll("_");
        return normalized.isEmpty() ? "empty" : normalized;
    }
}
