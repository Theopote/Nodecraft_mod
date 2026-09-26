package com.nodecraft.nodesystem.api;

import com.nodecraft.nodesystem.core.BasePort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves effective port types for list/tree and scalar passthrough type-variable binding
 * without mutating {@link IPort#getDataType() declared} types.
 * <p>
 * Connection checks are order-independent: a candidate edge that would bind {@code T}
 * is only accepted if every existing connection in that type-variable group remains legal
 * under the resulting binding.
 * <p>
 * {@link NodeDataType#DATA_TREE} ports participate in the same {@code T} group as list
 * ports via {@link IPort#getListTypeVariable()}; their declared type stays {@code DATA_TREE}
 * while kind flows to remapped list/element ports. Multi-input tree nodes (Merge / Entwine)
 * reject conflicting constrained kinds at connect time — they never silently widen to
 * {@link ListElementKind#UNCONSTRAINED}.
 * <p>
 * Scalar passthrough ports ({@link IPort#isPassthroughBinding()}) bind {@code T} to the
 * exact upstream {@link NodeDataType} (e.g. Relay / Fork / Validate Value / Coalesce).
 */
public final class PortTypeResolver {

    private PortTypeResolver() {
    }

    /**
     * Declared type, or remapped from a shared type variable on the same node.
     */
    public static NodeDataType resolveEffectiveType(IPort port) {
        if (port == null) {
            return NodeDataType.ANY;
        }
        if (port.isPassthroughBinding()) {
            NodeDataType bound = resolveBoundPassthroughType(port.getNode(), port.getListTypeVariable());
            if (bound != null && bound != NodeDataType.ANY) {
                return bound;
            }
            return port.getDataType();
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

        if (usesPassthroughVariable(node, variable) || inputPort.isPassthroughBinding()) {
            return isConnectablePassthrough(outputPort, inputPort, node, variable);
        }

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

        ListElementKind candidateKind = kindFromSourcePort(outputPort, new HashSet<>());
        if (!kindsAgree(provisionalKind, candidateKind)) {
            return false;
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
     * against effective types and element kinds implied by {@code boundKind}.
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
                ListElementKind sourceKind = kindFromSourcePort(source, new HashSet<>());
                if (!kindsAgree(boundKind, sourceKind)) {
                    return false;
                }
                NodeDataType sourceEffective = resolveEffectiveType(source);
                if (!NodeDataType.isConnectableTo(sourceEffective, peerEffective)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isConnectablePassthrough(
            IPort outputPort,
            IPort inputPort,
            INode node,
            String variable
    ) {
        NodeDataType provisional = resolveBoundPassthroughTypeProvisional(node, variable, outputPort, inputPort);
        NodeDataType outputEffective = resolveEffectiveType(outputPort);
        NodeDataType inputEffective = remapPassthrough(inputPort, provisional);
        if (!NodeDataType.isConnectableTo(outputEffective, inputEffective)) {
            return false;
        }
        if (variable == null || variable.isBlank() || node == null) {
            return true;
        }
        if (isConcrete(provisional) && isConcrete(outputEffective) && provisional != outputEffective) {
            // Candidate must agree with an already-bound concrete T.
            if (resolveBoundPassthroughType(node, variable) != null
                    && resolveBoundPassthroughType(node, variable) != outputEffective) {
                return false;
            }
        }
        return validatePassthroughGroup(node, variable, provisional);
    }

    private static boolean validatePassthroughGroup(INode node, String variable, NodeDataType boundType) {
        for (IPort peer : allPorts(node)) {
            if (peer == null || !peer.isInput()) {
                continue;
            }
            if (!variable.equals(peer.getListTypeVariable())) {
                continue;
            }
            NodeDataType peerEffective = remapPassthrough(peer, boundType);
            for (IPort source : connectedSources(peer)) {
                NodeDataType sourceEffective = resolveEffectiveType(source);
                if (isConcrete(boundType) && isConcrete(sourceEffective) && boundType != sourceEffective) {
                    return false;
                }
                if (!NodeDataType.isConnectableTo(sourceEffective, peerEffective)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static NodeDataType remapPassthrough(IPort port, NodeDataType boundType) {
        if (port != null && port.isPassthroughBinding() && isConcrete(boundType)) {
            return boundType;
        }
        return port == null ? NodeDataType.ANY : port.getDataType();
    }

    private static NodeDataType resolveBoundPassthroughTypeProvisional(
            INode node,
            String variable,
            IPort candidateOutput,
            IPort candidateInput
    ) {
        NodeDataType existing = resolveBoundPassthroughType(node, variable);
        if (isConcrete(existing)) {
            return existing;
        }
        if (variable != null && !variable.isBlank()
                && candidateInput != null
                && variable.equals(candidateInput.getListTypeVariable())
                && candidateInput.isPassthroughBinding()) {
            NodeDataType fromCandidate = resolveEffectiveType(candidateOutput);
            if (isConcrete(fromCandidate)) {
                return fromCandidate;
            }
        }
        return existing;
    }

    private static NodeDataType resolveBoundPassthroughType(INode node, String variable) {
        return resolveBoundPassthroughType(node, variable, new HashSet<>());
    }

    private static NodeDataType resolveBoundPassthroughType(INode node, String variable, Set<String> visiting) {
        if (node == null || variable == null || variable.isBlank()) {
            return null;
        }
        String visitKey = "pt:" + node.getId() + "#" + variable;
        if (!visiting.add(visitKey)) {
            return null;
        }
        for (IPort peer : allPorts(node)) {
            if (peer == null || !peer.isInput()) {
                continue;
            }
            if (!variable.equals(peer.getListTypeVariable()) || !peer.isPassthroughBinding()) {
                continue;
            }
            for (IPort source : connectedSources(peer)) {
                NodeDataType effective = resolveEffectiveTypeDeep(source, visiting);
                if (isConcrete(effective)) {
                    return effective;
                }
            }
        }
        return null;
    }

    private static NodeDataType resolveEffectiveTypeDeep(IPort port, Set<String> visiting) {
        if (port == null) {
            return NodeDataType.ANY;
        }
        if (port.isPassthroughBinding()) {
            NodeDataType bound = resolveBoundPassthroughType(port.getNode(), port.getListTypeVariable(), visiting);
            if (isConcrete(bound)) {
                return bound;
            }
            return port.getDataType();
        }
        return resolveEffectiveType(port);
    }

    private static boolean usesPassthroughVariable(INode node, String variable) {
        if (node == null || variable == null || variable.isBlank()) {
            return false;
        }
        for (IPort peer : allPorts(node)) {
            if (peer != null && variable.equals(peer.getListTypeVariable()) && peer.isPassthroughBinding()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConcrete(NodeDataType type) {
        return type != null && type != NodeDataType.ANY;
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
        // DATA_TREE (and other non-list carriers) keep declared type; kind is metadata for T.
        return declared;
    }

    /**
     * Bound kind after hypothetically connecting {@code candidateOutput → candidateInput},
     * without mutating the graph.
     */
    private static ListElementKind resolveBoundElementKindProvisional(
            INode node, String variable, IPort candidateOutput, IPort candidateInput) {
        ListElementKind existing = resolveBoundElementKind(node, variable);
        if (isConstrained(existing)) {
            return existing;
        }
        if (variable != null && !variable.isBlank()
                && variable.equals(candidateInput.getListTypeVariable())
                && !candidateInput.isListElementBinding()
                && !candidateInput.isPassthroughBinding()) {
            ListElementKind fromCandidate = kindFromSourcePort(candidateOutput, new HashSet<>());
            if (isConstrained(fromCandidate)) {
                return fromCandidate;
            }
        }
        return existing;
    }

    private static ListElementKind resolveBoundElementKind(INode node, String variable) {
        return resolveBoundElementKind(node, variable, new HashSet<>());
    }

    private static ListElementKind resolveBoundElementKind(INode node, String variable, Set<String> visiting) {
        if (node == null || variable == null || variable.isBlank()) {
            return null;
        }
        String visitKey = node.getId() + "#" + variable;
        if (!visiting.add(visitKey)) {
            return null;
        }
        for (IPort peer : allPorts(node)) {
            if (peer == null || !peer.isInput()) {
                continue;
            }
            if (!variable.equals(peer.getListTypeVariable())
                    || peer.isListElementBinding()
                    || peer.isPassthroughBinding()) {
                continue;
            }
            for (IPort source : connectedSources(peer)) {
                ListElementKind kind = kindFromSourcePort(source, visiting);
                if (isConstrained(kind)) {
                    return kind;
                }
            }
        }
        return null;
    }

    /**
     * Kind carried by a source output: typed list kind, or the producer's shared T when DATA_TREE.
     */
    private static ListElementKind kindFromSourcePort(IPort source, Set<String> visiting) {
        if (source == null) {
            return null;
        }
        NodeDataType declared = source.getDataType();
        if (declared != null && declared.isListType()) {
            ListElementKind declaredKind = declared.getListElementKind();
            if (isConstrained(declaredKind)) {
                return declaredKind;
            }
            String srcVar = source.getListTypeVariable();
            if (srcVar != null && !srcVar.isBlank() && source.getNode() != null) {
                ListElementKind fromProducer = resolveBoundElementKind(source.getNode(), srcVar, visiting);
                if (isConstrained(fromProducer)) {
                    return fromProducer;
                }
            }
        }
        if (declared == NodeDataType.DATA_TREE) {
            String srcVar = source.getListTypeVariable();
            if (srcVar != null && !srcVar.isBlank() && source.getNode() != null) {
                return resolveBoundElementKind(source.getNode(), srcVar, visiting);
            }
        }
        return null;
    }

    private static boolean kindsAgree(ListElementKind boundKind, ListElementKind sourceKind) {
        if (!isConstrained(boundKind) || !isConstrained(sourceKind)) {
            return true;
        }
        return boundKind == sourceKind;
    }

    private static boolean isConstrained(ListElementKind kind) {
        return kind != null
                && kind != ListElementKind.UNCONSTRAINED
                && kind != ListElementKind.NONE;
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
