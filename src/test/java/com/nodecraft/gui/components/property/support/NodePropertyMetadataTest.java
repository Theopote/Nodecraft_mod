package com.nodecraft.gui.components.property.support;

import com.nodecraft.gui.components.property.core.PropertyConstraints;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyInspector;
import com.nodecraft.nodesystem.core.NodePropertyBindings;
import com.nodecraft.nodesystem.nodes.math.fields.PointAttractorFieldNode;
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import com.nodecraft.nodesystem.preview.TrackedWorldCompatGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodePropertyMetadataTest {

    private final PropertyInspector inspector = new PropertyInspector();

    @AfterEach
    void clearCompatGate() {
        System.clearProperty(TrackedWorldCompatGate.SYSTEM_PROPERTY);
    }

    @Test
    void geometryViewerPerformancePropertiesHavePositiveBounds() {
        PropertyDescriptor maxPreview = requireProperty(GeometryViewerNode.class, "maxPreviewBlocks");
        assertTrue(maxPreview.constraints.hasMin());
        assertTrue(maxPreview.constraints.hasMax());
        assertEquals(1.0d, maxPreview.constraints.min());
        assertEquals(20000.0d, maxPreview.constraints.max());

        PropertyDescriptor draftPreview = requireProperty(GeometryViewerNode.class, "draftPreviewBlocks");
        assertEquals(1.0d, draftPreview.constraints.min());
        assertEquals(20000.0d, draftPreview.constraints.max());
    }

    @Test
    void attractorRadiusAndExponentHaveMinimums() {
        PropertyDescriptor radius = requireProperty(PointAttractorFieldNode.class, "radius");
        PropertyDescriptor exponent = requireProperty(PointAttractorFieldNode.class, "exponent");

        assertEquals(0.001d, radius.constraints.min());
        assertEquals(0.001d, exponent.constraints.min());
        assertFalse(radius.constraints.hasMax());
    }

    @Test
    void geometryViewerGhostOnlyPropertiesHiddenOnTrackedWorldBackend() {
        System.setProperty(TrackedWorldCompatGate.SYSTEM_PROPERTY, "true");

        GeometryViewerNode node = new GeometryViewerNode();
        node.setPreviewBackend(PreviewBackend.TRACKED_WORLD);
        assertEquals(PreviewBackend.TRACKED_WORLD, node.getPreviewBackend());

        PropertyDescriptor previewColor = descriptor("previewColor", String.class);
        PropertyDescriptor transparency = descriptor("transparency", float.class);
        PropertyDescriptor blockType = descriptor("blockType", String.class);

        assertFalse(PropertyVisibilitySupport.shouldDisplayProperty(node, previewColor));
        assertFalse(PropertyVisibilitySupport.shouldDisplayProperty(node, transparency));
        assertTrue(PropertyVisibilitySupport.shouldDisplayProperty(node, blockType));
    }

    @Test
    void bindingsExposeSameConstraintsAsInspector() {
        NodePropertyBindings.NodePropertyBinding binding = NodePropertyBindings.bindingsFor(GeometryViewerNode.class).stream()
                .filter(candidate -> "transparency".equals(candidate.name()))
                .findFirst()
                .orElseThrow();

        PropertyDescriptor descriptor = requireProperty(GeometryViewerNode.class, "transparency");
        assertEquals(binding.constraints(), descriptor.constraints);
    }

    private PropertyDescriptor requireProperty(Class<?> nodeClass, String propertyName) {
        PropertyDescriptor descriptor = inspector.getPropertiesForNode(nodeClass).stream()
                .filter(prop -> propertyName.equals(prop.name))
                .findFirst()
                .orElse(null);
        assertNotNull(descriptor, "Missing property: " + propertyName);
        return descriptor;
    }

    private static PropertyDescriptor descriptor(String name, Class<?> type) {
        return new PropertyDescriptor(name, name, type, null, null, null, "", "General", 0, PropertyConstraints.UNBOUNDED);
    }
}
