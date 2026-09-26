package com.nodecraft.gui.components.node;

import com.nodecraft.gui.components.node.actions.ApplyChangesActionProvider;
import com.nodecraft.gui.components.node.actions.CoalesceActionProvider;
import com.nodecraft.gui.components.node.actions.SignalForkActionProvider;
import com.nodecraft.gui.components.node.actions.TagRelayActionProvider;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.CoalesceNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.RelayNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalForkNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeActionProviderRegistryTest {

    @Test
    void registryOwnsAssistAndActionProviders() {
        assertEquals(3, NodeActionProviderRegistry.assistProviders().size());
        assertTrue(NodeActionProviderRegistry.assistProviders().contains(SignalForkActionProvider.INSTANCE));
        assertTrue(NodeActionProviderRegistry.assistProviders().contains(CoalesceActionProvider.INSTANCE));
        assertTrue(NodeActionProviderRegistry.assistProviders().contains(TagRelayActionProvider.INSTANCE));

        assertEquals(1, NodeActionProviderRegistry.actionProviders().size());
        assertTrue(NodeActionProviderRegistry.actionProviders().contains(ApplyChangesActionProvider.INSTANCE));
    }

    @Test
    void providersDeclineNodesTheyDoNotOwn() {
        SignalForkNode fork = new SignalForkNode();
        CoalesceNode coalesce = new CoalesceNode();
        RelayNode relay = new RelayNode();

        assertFalse(SignalForkActionProvider.INSTANCE.render(coalesce, () -> null));
        assertFalse(CoalesceActionProvider.INSTANCE.render(fork, () -> null));
        assertFalse(TagRelayActionProvider.INSTANCE.render(fork, () -> null));
        assertFalse(ApplyChangesActionProvider.INSTANCE.render(relay, () -> null));
    }

    @Test
    void removeConnectionsForPortDropsMatchingWires() {
        NodeGraph graph = new NodeGraph("action-support");
        SignalForkNode fork = new SignalForkNode();
        RelayNode relay = new RelayNode();
        graph.addNode(fork);
        graph.addNode(relay);

        assertTrue(graph.connect(fork.getId(), "output_a", relay.getId(), "input_signal"));
        assertEquals(1, graph.getConnections().size());

        NodeActionGraphSupport.removeConnectionsForPort(() -> graph, fork.getId(), "output_a", false);

        assertTrue(graph.getConnections().isEmpty());
    }
}
