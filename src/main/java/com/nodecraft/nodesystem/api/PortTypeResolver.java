package com.nodecraft.nodesystem.api;

import com.nodecraft.nodesystem.core.BasePort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Resolves effective port types for list type-variable binding without mutating
 * {@link IPort#getDataType() declared} types.
 * <p>
 * Connection checks are order-independent: a candidate edge that would bind {@code T}
 * is only accepted if every existing connection in that type-variable group remains legal
 * under the resulting binding.
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
        return resolveEffectiveWithKind(port, resolveBoundElementKind(port.getNode(), port.getListTypeVariable()));
    }

    /**
     * Whether {@code outputPort → inputPort} is legal under current bindings and would leave
     * the input node's type-variable group consistent if committed.
     */
    public static boolean isConnectable(IPort outputPort, IPort inputPort) {
        if (outputPort == null || inputPort == null) {
            return false;
        }

        String variable = inputPort.getListTypeVariable();
        INode node = inputPort.getNode();
        ListElementKind provisionalKind = resolveBoundElementKindProvisional(
                node, variable, outputPort, inputPort);

        NodeDataType outputEffective = resolveEffectiveType(outputPort);
        NodeDataType inputEffective = resolveEffectiveWithKind(inputPort, provisionalKind);
        if (!NodeDataType.isConnectableTo(outputEffective, inputEffective)) {
            return false;
        }

        if (variable == null || variable.isBlank() || node == null) {
            return true;
        }

        return validateTypeVariableGroup(node, variable, provisionalKind);
    }

    public static String connectabilityRejectionReason(IPort outputPort, IPort inputPort) {
        if (isConnectable(outputPort, inputPort)) {
            return null;
        }
        return NodeDataType.getConnectabilityRejectionReason(
                resolveEffectiveType(outputPort),
                resolveEffectiveType(inputPort));
    }

    /**
     * Validates every existing inbound connection on ports sharing {@code variable}
     * against effective types implied by {@code boundKind}.
     */
    public static boolean validateTypeVariableGroup(INode node, String variable, ListElementKind boundKind) {
        if (node == null || variable == null || variable.isBlank()) {
            return true;
        }
        for (IPort peer : allPorts(node)) {
            if (peer == null || !peer.isInput()) {
                continue;
            }
            if (!variable.equals(peer.getListTypeVariable())) {
                continue;
            }
            NodeDataType peerEffective = resolveEffectiveWithKind(peer, boundKind);
            for (IPort source : connectedSources(peer)) {
                NodeDataType sourceEffective = resolveEffectiveType(source);
                if (!NodeDataType.isConnectableTo(sourceEffective, peerEffective)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static NodeDataType resolveEffectiveWithKind(IPort port, ListElementKind boundKind) {
        NodeDataType declared = port.getDataType();
        String variable = port.getListTypeVariable();
        if (variable == null || variable.isBlank()
                || boundKind == null
                || boundKind == ListElementKind.UNCONSTRAINED
                || boundKind == ListElementKind.NONE) {
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

    /**
     * Bound kind after hypothetically connecting {@code candidateOutput → candidateInput},
     * without mutating the graph.
     */
    private static ListElementKind resolveBoundElementKindProvisional(
            INode node, String variable, IPort candidateOutput, IPort candidateInput) {
        if (variable != null && !variable.isBlank()
                && variable.equals(candidateInput.getListTypeVariable())
                && !candidateInput.isListElementBinding()) {
            NodeDataType sourceType = resolveEffectiveType(candidateOutput);
            if (sourceType != null && sourceType.isListType()) {
                ListElementKind kind = sourceType.getListElementKind();
                if (kind != ListElementKind.UNCONSTRAINED && kind != ListElementKind.NONE) {
                    return kind;
                }
            }
        }
        return resolveBoundElementKind(node, variable);
    }

    private static ListElementKind resolveBoundElementKind(INode node, String variable) {
        if (node == null || variable == null || variable.isBlank()) {
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
