package com.nodecraft.gui.components.node;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.api.ResettableNode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.util.Vec3;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResettableNodeContractTest {

    @Test
    void resettableNodesExposeResetWithoutReflection() {
        AtomicBoolean resetCalled = new AtomicBoolean();
        INode node = new ResettableStub(resetCalled);

        assertTrue(node instanceof ResettableNode);
        ((ResettableNode) node).resetProperties();
        assertTrue(resetCalled.get());
    }

    @Test
    void ordinaryNodesAreNotResettable() {
        INode node = new PlainStub();
        assertFalse(node instanceof ResettableNode);
        assertFalse(ResettableNode.supportsReset(node));
    }

    @Test
    void supportsResetMatchesInstanceofCheck() {
        INode resettable = new ResettableStub(new AtomicBoolean());
        assertTrue(ResettableNode.supportsReset(resettable));
    }

    @Test
    void performResetWithHistoryRecordsUndoableNodeState() {
        NodeGraph graph = new NodeGraph("reset-history");
        ResettableWritableNode node = new ResettableWritableNode();
        node.value = 7;
        graph.addNode(node);

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        editor.setCurrentGraph(graph);
        editor.getHistory().clear();

        PropertyPanelComponent panel = new PropertyPanelComponent();
        panel.performResetWithHistory(node);

        assertEquals(0, node.value);
        assertTrue(editor.getHistory().canUndo());
        assertEquals(ImGuiNodeHistory.ActionType.NODE_STATE_CHANGE, editor.getHistory().getUndoTopActionType());
        assertTrue(editor.undo());
        assertEquals(7, node.value);
    }

    @Test
    void performResetWithHistoryRollsBackPartialMutation() {
        FailingResettableNode node = new FailingResettableNode();
        node.value = 4;

        PropertyPanelComponent panel = new PropertyPanelComponent();
        assertThrows(
                NodeActionPanel.ResetRollbackException.class,
                () -> panel.performResetWithHistory(node)
        );
        assertEquals(4, node.value);
    }

    @Test
    void performResetWithHistoryRollsBackInPlaceMutablePropertyMutation() {
        Vec3FailingResettableNode node = new Vec3FailingResettableNode();
        node.center.setX(4.0d);
        node.center.setY(5.0d);
        node.center.setZ(6.0d);

        PropertyPanelComponent panel = new PropertyPanelComponent();
        assertThrows(
                NodeActionPanel.ResetRollbackException.class,
                () -> panel.performResetWithHistory(node)
        );
        assertEquals(4.0d, node.center.getX(), 0.0d);
        assertEquals(5.0d, node.center.getY(), 0.0d);
        assertEquals(6.0d, node.center.getZ(), 0.0d);
    }

    private static final class ResettableStub extends BaseNode implements ResettableNode {
        private final AtomicBoolean resetCalled;

        ResettableStub(AtomicBoolean resetCalled) {
            super(UUID.randomUUID(), "test.stub.resettable");
            this.resetCalled = resetCalled;
        }

        @Override
        public void resetProperties() {
            resetCalled.set(true);
        }

        @Override
        public String getDisplayName() {
            return "ResettableStub";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }

    private static class ResettableWritableNode extends BaseNode implements ResettableNode {
        @NodeProperty(displayName = "Value")
        int value;

        ResettableWritableNode() {
            super(UUID.randomUUID(), "test.stub.resettable_writable");
        }

        @Override
        public void resetProperties() {
            value = 0;
        }

        @Override
        public String getDisplayName() {
            return "ResettableWritable";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }

    private static final class FailingResettableNode extends ResettableWritableNode {
        @Override
        public void resetProperties() {
            value = 1;
            throw new IllegalStateException("boom");
        }
    }

    private static final class Vec3FailingResettableNode extends BaseNode implements ResettableNode {
        @NodeProperty(displayName = "Center")
        Vec3 center = new Vec3(0.0d, 0.0d, 0.0d);

        Vec3FailingResettableNode() {
            super(UUID.randomUUID(), "test.stub.resettable_vec3");
        }

        @Override
        public void resetProperties() {
            center.setX(0.0d);
            throw new IllegalStateException("boom");
        }

        @Override
        public String getDisplayName() {
            return "Vec3FailingResettable";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }

    private static final class PlainStub extends BaseNode {
        PlainStub() {
            super(UUID.randomUUID(), "test.stub.plain");
        }

        @Override
        public String getDisplayName() {
            return "PlainStub";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }
}
