package com.nodecraft.nodesystem.semantic;

/**
 * Physical vs synthetic semantic port keys used in recommendation rules.
 *
 * <p>Example: physical {@code output_face} vs synthetic {@code output_face:horizontal}.
 * Catalog never resolves runtime orientation — callers pass the key they want.</p>
 */
public final class NodeSemanticPortKeys {

    private NodeSemanticPortKeys() {
    }

    /** Strip {@code :variant} suffix; blank/null stays blank/null. */
    public static String physicalBase(String portKey) {
        if (portKey == null || portKey.isBlank()) {
            return portKey;
        }
        int colon = portKey.indexOf(':');
        if (colon <= 0) {
            return portKey;
        }
        return portKey.substring(0, colon);
    }

    /** True when the key includes a {@code :variant} suffix. */
    public static boolean isVariant(String portKey) {
        if (portKey == null || portKey.isBlank()) {
            return false;
        }
        int colon = portKey.indexOf(':');
        return colon > 0 && colon < portKey.length() - 1;
    }

    /**
     * Exact match only — physical {@code output_face} does <strong>not</strong> expand to all
     * {@code output_face:*} variants.
     */
    public static boolean matchesEdgePort(String edgePortKey, String queryKey) {
        if (queryKey == null || queryKey.isBlank()) {
            return true;
        }
        if (edgePortKey == null || edgePortKey.isBlank()) {
            return false;
        }
        return queryKey.equalsIgnoreCase(edgePortKey);
    }
}
