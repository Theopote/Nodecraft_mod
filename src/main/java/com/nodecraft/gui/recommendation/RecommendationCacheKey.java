package com.nodecraft.gui.recommendation;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.util.OptionalPortDrive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lightweight fingerprint so Suggested Connections Overlay invalidates when the
 * selected node's state, connections, or rule revision changes — not only its UUID.
 */
final class RecommendationCacheKey {

    private RecommendationCacheKey() {
    }

    static String build(INode node, long rulesRevision, String semanticKey) {
        if (node == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append(node.getId()).append('|');
        sb.append(rulesRevision).append('|');
        sb.append(node.getTypeId() != null ? node.getTypeId() : "");
        sb.append("|sem=").append(semanticKey != null ? semanticKey : "");
        sb.append("|in=");
        List<String> connectedInputs = new ArrayList<>();
        List<IPort> inputs = node.getInputPorts();
        if (inputs != null) {
            for (IPort port : inputs) {
                if (port == null || port.getId() == null) {
                    continue;
                }
                if (OptionalPortDrive.isConnected(node, port.getId())) {
                    connectedInputs.add(port.getId());
                }
            }
        }
        Collections.sort(connectedInputs);
        sb.append(String.join(",", connectedInputs));
        sb.append("|st=");
        appendStateFingerprint(sb, node.getNodeState());
        return sb.toString();
    }

    private static void appendStateFingerprint(StringBuilder sb, Object state) {
        if (!(state instanceof Map<?, ?> map) || map.isEmpty()) {
            return;
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                continue;
            }
            Object value = entry.getValue();
            sorted.put(key, value == null ? "" : String.valueOf(value));
        }
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
    }
}
