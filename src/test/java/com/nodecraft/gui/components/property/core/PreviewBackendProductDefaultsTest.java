package com.nodecraft.gui.components.property.core;

import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewBackendProductDefaultsTest {

    @Test
    void geometryViewerDefaultsToGhostInAdvancedCategory() throws Exception {
        GeometryViewerNode node = new GeometryViewerNode();
        assertEquals(PreviewBackend.GHOST, node.getPreviewBackend());

        Field field = GeometryViewerNode.class.getDeclaredField("previewBackend");
        NodeProperty annotation = field.getAnnotation(NodeProperty.class);
        assertEquals("Advanced", annotation.category());
        assertTrue(annotation.description().toLowerCase().contains("compat"));
    }

    @Test
    void advancedSectionsSortLastAndCollapseByDefault() {
        MethodAccessor noop = new MethodAccessor() {
            @Override
            public Object invoke(Object obj, Object... args) {
                return null;
            }

            @Override
            public Class<?> getReturnType() {
                return String.class;
            }

            @Override
            public Class<?>[] getParameterTypes() {
                return new Class<?>[0];
            }
        };

        List<PropertyDescriptor> props = List.of(
                new PropertyDescriptor("backend", "Preview Backend", String.class, noop, null, null, "", "Advanced", 1),
                new PropertyDescriptor("color", "Color", String.class, noop, null, null, "", "Display", 1),
                new PropertyDescriptor("mode", "Mode", String.class, noop, null, null, "", "Compatibility", 1)
        );

        PropertySectionOrganizer.OrganizedProperties organized = PropertySectionOrganizer.organize(props);
        assertEquals(3, organized.sections().size());
        assertEquals("Display", organized.sections().get(0).displayName());
        assertEquals("Advanced", organized.sections().get(1).displayName());
        assertEquals("Compatibility", organized.sections().get(2).displayName());

        assertFalse(organized.sections().get(0).collapsedByDefault());
        assertTrue(organized.sections().get(1).collapsedByDefault());
        assertTrue(organized.sections().get(2).collapsedByDefault());
    }
}
