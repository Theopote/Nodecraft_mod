package com.nodecraft.gui.components.property.support;

import com.nodecraft.gui.components.property.core.MethodAccessor;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertySemanticHintsTest {

    @Test
    void detectsTransparencySliderMode() {
        GeometryViewerNode node = new GeometryViewerNode();
        PropertyDescriptor prop = descriptor("transparency", "Transparency", float.class);
        assertEquals(PropertySemanticHints.ScalarMode.PERCENT,
                PropertySemanticHints.scalarMode(node, prop, float.class));
    }

    @Test
    void detectsAngleModeFromPropertyName() {
        StubNode node = new StubNode();
        PropertyDescriptor prop = descriptor("rotationY", "Rotation Y", double.class);
        assertEquals(PropertySemanticHints.ScalarMode.ANGLE,
                PropertySemanticHints.scalarMode(node, prop, double.class));
    }

    @Test
    void detectsIntegerCountMode() {
        StubNode node = new StubNode();
        PropertyDescriptor prop = descriptor("segmentCount", "Segment Count", int.class);
        assertEquals(PropertySemanticHints.ScalarMode.INTEGER_COUNT,
                PropertySemanticHints.scalarMode(node, prop, int.class));
    }

    @Test
    void segmentedEnumControlEnabledForSmallSets() {
        assertTrue(PropertySemanticHints.useSegmentedEnumControl(3));
        assertFalse(PropertySemanticHints.useSegmentedEnumControl(5));
    }

    private static PropertyDescriptor descriptor(String name, String displayName, Class<?> type) {
        MethodAccessor getter = new MethodAccessor() {
            @Override
            public Object invoke(Object obj, Object... args) {
                return null;
            }

            @Override
            public Class<?> getReturnType() {
                return type;
            }

            @Override
            public Class<?>[] getParameterTypes() {
                return new Class<?>[0];
            }
        };
        return new PropertyDescriptor(name, displayName, type, getter, getter, null, "", "General", 0);
    }

    private static final class StubNode extends BaseNode {
        StubNode() {
            super(UUID.randomUUID(), "test.semantic_hints");
        }

        @Override
        public String getDisplayName() {
            return "Stub";
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
