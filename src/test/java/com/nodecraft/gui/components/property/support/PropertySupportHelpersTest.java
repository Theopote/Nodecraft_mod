package com.nodecraft.gui.components.property.support;

import com.nodecraft.gui.components.property.core.MethodAccessor;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertySupportHelpersTest {

    @Test
    void colorPickerTriggersOnHexOrColorNamedBlank() {
        PropertyDescriptor colorProp = descriptor("previewColor", "Preview Color", String.class);
        PropertyDescriptor otherProp = descriptor("tag", "Tag", String.class);

        assertTrue(StringColorPropertyEditor.shouldUseColorPicker(colorProp, "#AABBCC"));
        assertTrue(StringColorPropertyEditor.shouldUseColorPicker(colorProp, ""));
        assertFalse(StringColorPropertyEditor.shouldUseColorPicker(otherProp, "not-a-color"));
        assertTrue(StringColorPropertyEditor.isHexColorString("#112233"));
        assertFalse(StringColorPropertyEditor.isHexColorString("112233"));
        assertEquals("#AABBCC", StringColorPropertyEditor.normalizeHexColor("#AABBCC"));
        assertEquals("#000000", StringColorPropertyEditor.normalizeHexColor("nope"));
        assertEquals("#FF8000", StringColorPropertyEditor.toHexColor(new float[]{1f, 0.5f, 0f}));
    }

    @Test
    void geometryViewerHidesGhostOnlyPropsOnNonGhostBackend() {
        GeometryViewerNode node = new GeometryViewerNode();
        node.setPreviewBackend(PreviewBackend.TRACKED_WORLD);

        assertFalse(GeometryViewerPropertySupport.shouldDisplayProperty(
                node, descriptor("previewColor", "Preview Color", String.class)));
        assertFalse(GeometryViewerPropertySupport.shouldDisplayProperty(
                node, descriptor("transparency", "Transparency", float.class)));
        assertTrue(GeometryViewerPropertySupport.shouldDisplayProperty(
                node, descriptor("blockType", "Block Type", String.class)));
    }

    @Test
    void enumLabelsHumanizeAndOverrideGhostModes() {
        assertEquals("Solid Color", EnumPropertyLabels.humanizeEnumName("SOLID_COLOR"));

        GeometryViewerNode node = new GeometryViewerNode();
        PropertyDescriptor prop = descriptor(
                "ghostRenderMode",
                "Ghost Render Mode",
                GeometryViewerNode.GhostRenderMode.class);
        String[] names = EnumPropertyLabels.buildDisplayNames(
                node, prop, GeometryViewerNode.GhostRenderMode.values());

        assertEquals("Block Color", names[GeometryViewerNode.GhostRenderMode.BLOCK_COLOR.ordinal()]);
        assertEquals("Solid Color", names[GeometryViewerNode.GhostRenderMode.SOLID_COLOR.ordinal()]);
        assertEquals("Wireframe", names[GeometryViewerNode.GhostRenderMode.WIREFRAME.ordinal()]);
    }

    private static PropertyDescriptor descriptor(String name, String displayName, Class<?> type) {
        MethodAccessor noop = new MethodAccessor() {
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
        return new PropertyDescriptor(name, displayName, type, noop, null, null, "", "General", 0);
    }
}
