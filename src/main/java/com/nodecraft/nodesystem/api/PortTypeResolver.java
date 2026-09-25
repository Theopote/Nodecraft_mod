package com.nodecraft.nodesystem.api;

import com.nodecraft.nodesystem.core.BasePort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Resolves effective port types for list type-variable binding without mutating
 * {@link IPort#getDataType() declared} types.
 */
public final class PortTypeResolver {

    private PortTypeResolver() {
    }

    /**
     * Declared type, or list/element type remapped from a shared type variable on the same node.
     */
    public static NodeDataType resolveEffectiveType(IPort port) {
        if (port == null) {
            return NodeDataType.ANY;
        }
        NodeDataType declared = port.getDataType();
        String variable = port.getListTypeVariable();
        if (variable == null || variable.isBlank()) {
            return declared;
        }

        ListElementKind boundKind = resolveBoundElementKind(port.getNode(), variable);
        if (boundKind == null || boundKind == ListElementKind.UNCONSTRAINED || boundKind == ListElementKind.NONE) {
            return declared;
        }

        if (port.isListElementBinding()) {
            return NodeDataType.elementTypeForKind(boundKind);
        }

        if (declared.isListType()) {
            return NodeDataType.forListElementKind(boundKind);
        }
        return declared;
    }

    public static boolean isConnectable(IPort outputPort, IPort inputPort) {
        return NodeDataType.isConnectableTo(resolveEffectiveType(outputPort), resolveEffectiveType(inputPort));
    }

    public static String connectabilityRejectionReason(IPort outputPort, IPort inputPort) {
        return NodeDataType.getConnectabilityRejectionReason(
                resolveEffectiveType(outputPort),
                resolveEffectiveType(inputPort));
    }

    private static ListElementKind resolveBoundElementKind(INode node, String variable) {
        if (node == null || variable == null) {
            return null;
        }
        for (IPort peer : allPorts(node)) {
            if (peer == null || !peer.isInput()) {
                continue;
            }
            if (!variable.equals(peer.getListTypeVariable()) || peer.isListElementBinding()) {
                continue;
            }
            for (IPort source : connectedSources(peer)) {
                NodeDataType sourceType = resolveEffectiveType(source);
                if (sourceType != null && sourceType.isListType()) {
                    ListElementKind kind = sourceType.getListElementKind();
                    if (kind != ListElementKind.UNCONSTRAINED && kind != ListElementKind.NONE) {
                        return kind;
                    }
                }
            }
        }
        return null;
    }

    private static Collection<IPort> connectedSources(IPort inputPort) {
        if (inputPort instanceof BasePort basePort) {
            List<IPort> sources = new ArrayList<>();
            for (IPort connected : basePort.getConnectedPorts()) {
                if (connected != null && !connected.isInput()) {
                    sources.add(connected);
                }
            }
            return sources;
        }
        return List.of();
    }

    private static List<IPort> allPorts(INode node) {
        List<IPort> ports = new ArrayList<>();
        if (node.getInputPorts() != null) {
            ports.addAll(node.getInputPorts());
        }
        if (node.getOutputPorts() != null) {
            ports.addAll(node.getOutputPorts());
        }
        return ports;
    }
}
