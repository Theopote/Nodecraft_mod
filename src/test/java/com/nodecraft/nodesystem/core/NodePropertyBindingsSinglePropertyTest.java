package com.nodecraft.nodesystem.core;

import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodePropertyBindingsSinglePropertyTest {

    @Test
    void readAndWriteSingleProperty() throws Throwable {
        SampleNode node = new SampleNode();
        assertEquals(1.0d, (double) NodePropertyBindings.readProperty(node, "width"), 0.0d);

        NodePropertyBindings.writeProperty(node, "width", 3.5d);
        assertEquals(3.5d, node.width, 0.0d);
    }

    private static final class SampleNode extends BaseNode {
        @NodeProperty(displayName = "Width")
        private double width = 1.0d;

        SampleNode() {
            super(UUID.randomUUID(), "test.bindings.single");
        }

        @Override
        public String getDisplayName() {
            return "Sample";
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
