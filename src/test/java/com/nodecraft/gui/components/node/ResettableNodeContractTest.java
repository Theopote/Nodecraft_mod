package com.nodecraft.gui.components.node;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.ResettableNode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
