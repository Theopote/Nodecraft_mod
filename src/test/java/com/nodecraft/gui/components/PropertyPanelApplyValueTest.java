package com.nodecraft.gui.components;

import com.nodecraft.gui.components.property.core.MethodAccessor;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyPanelApplyValueTest {

    @Test
    void applyPropertyValueMarksBaseNodeDirty() throws Throwable {
        PropertyPanelComponent panel = new PropertyPanelComponent();
        WritableStubNode node = new WritableStubNode();
        PropertyDescriptor prop = writableDoubleProperty();

        long versionBefore = node.getDirtyVersion();

        panel.applyPropertyValue(node, prop, 12.5d);

        assertEquals(12.5d, node.value, 0.0d);
        assertTrue(node.getDirtyVersion() > versionBefore);
    }

    @Test
    void markNodeDirtyAfterPropertyChangeWorksForResetPath() {
        PropertyPanelComponent panel = new PropertyPanelComponent();
        WritableStubNode node = new WritableStubNode();

        panel.markNodeDirtyAfterPropertyChange(node);

        assertTrue(node.isDirty());
    }

    private static PropertyDescriptor writableDoubleProperty() {
        MethodAccessor getter = new MethodAccessor() {
            @Override
            public Object invoke(Object obj, Object... args) {
                return ((WritableStubNode) obj).value;
            }

            @Override
            public Class<?> getReturnType() {
                return double.class;
            }

            @Override
            public Class<?>[] getParameterTypes() {
                return new Class<?>[0];
            }
        };
        MethodAccessor setter = new MethodAccessor() {
            @Override
            public Object invoke(Object obj, Object... args) {
                ((WritableStubNode) obj).value = (double) args[0];
                return null;
            }

            @Override
            public Class<?> getReturnType() {
                return void.class;
            }

            @Override
            public Class<?>[] getParameterTypes() {
                return new Class<?>[]{double.class};
            }
        };
        return new PropertyDescriptor(
                "value",
                "Value",
                double.class,
                getter,
                setter,
                null,
                "",
                "General",
                0
        );
    }

    private static final class WritableStubNode extends BaseNode {
        private double value;

        WritableStubNode() {
            super(UUID.randomUUID(), "test.stub.property_panel_apply");
        }

        @Override
        public String getDisplayName() {
            return "WritableStub";
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
