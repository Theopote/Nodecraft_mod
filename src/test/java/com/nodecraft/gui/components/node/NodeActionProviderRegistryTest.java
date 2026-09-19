package com.nodecraft.gui.components.node;

import com.nodecraft.gui.components.node.actions.ApplyChangesActionProvider;
import com.nodecraft.gui.components.node.actions.SignalForkActionProvider;
import com.nodecraft.gui.components.node.actions.SignalMergeActionProvider;
import com.nodecraft.gui.components.node.actions.TagRelayActionProvider;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalForkNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalMergeNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.TagRelayNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeActionProviderRegistryTest {

    @Test
    void registryOwnsAssistAndActionProviders() {
        assertEquals(3, NodeActionProviderRegistry.assistProviders().size());
        assertTrue(NodeActionProviderRegistry.assistProviders().contains(SignalForkActionProvider.INSTANCE));
        assertTrue(NodeActionProviderRegistry.assistProviders().contains(SignalMergeActionProvider.INSTANCE));
        assertTrue(NodeActionProviderRegistry.assistProviders().contains(TagRelayActionProvider.INSTANCE));

        assertEquals(1, NodeActionProviderRegistry.actionProviders().size());
        assertTrue(NodeActionProviderRegistry.actionProviders().contains(ApplyChangesActionProvider.INSTANCE));
    }

    @Test
    void providersDeclineNodesTheyDoNotOwn() {
        SignalForkNode fork = new SignalForkNode();
        SignalMergeNode merge = new SignalMergeNode();
        TagRelayNode relay = new TagRelayNode();

        assertFalse(SignalForkActionProvider.INSTANCE.render(merge, () -> null));
        assertFalse(SignalMergeActionProvider.INSTANCE.render(fork, () -> null));
        assertFalse(TagRelayActionProvider.INSTANCE.render(fork, () -> null));
        assertFalse(ApplyChangesActionProvider.INSTANCE.render(relay, () -> null));
    }

    @Test
    void removeConnectionsForPortDropsMatchingWires() {
        NodeGraph graph = new NodeGraph("action-support");
        SignalForkNode fork = new SignalForkNode();
        TagRelayNode relay = new TagRelayNode();
        graph.addNode(fork);
        graph.addNode(relay);

        assertTrue(graph.connect(fork.getId(), "output_a", relay.getId(), "input_signal"));
        assertEquals(1, graph.getConnections().size());

        NodeActionGraphSupport.removeConnectionsForPort(() -> graph, fork.getId(), "output_a", false);

        assertTrue(graph.getConnections().isEmpty());
    }
}
