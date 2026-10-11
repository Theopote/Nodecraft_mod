package com.nodecraft.gui.components.property.core;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexPropertyHistoryTest {

    @Test
    void planePropertyUndoRedoRestoresFullPlane() throws Throwable {
        NodeGraph graph = new NodeGraph("plane-history");
        PlaneNode node = new PlaneNode();
        PlaneData before = PlaneData.canonical(new Vector3d(0, 10, 0), new Vector3d(0, 1, 0));
        PlaneData after = PlaneData.canonical(new Vector3d(5, 0, 0), new Vector3d(1, 0, 0));
        assertNotNull(before);
        assertNotNull(after);
        node.plane = before;
        graph.addNode(node);

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        editor.setCurrentGraph(graph);
        editor.getHistory().clear();

        PropertyInspector inspector = new PropertyInspector();
        PropertyDescriptor prop = inspector.getPropertiesForNode(PlaneNode.class).stream()
                .filter(candidate -> "plane".equals(candidate.name))
                .findFirst()
                .orElseThrow();

        PropertyPanelComponent panel = new PropertyPanelComponent();
        panel.applyPropertyValue(node, prop, after);

        assertEquals(after.getNormal().x, node.plane.getNormal().x, 1.0e-6);
        assertTrue(editor.getHistory().canUndo());
        assertEquals(ImGuiNodeHistory.ActionType.PROPERTY_CHANGE, editor.getHistory().getUndoTopActionType());
        assertTrue(editor.undo());
        assertEquals(before.getNormal().y, node.plane.getNormal().y, 1.0e-6);
        assertEquals(PlaneEquationEdit.displayOffset(before), PlaneEquationEdit.displayOffset(node.plane), 1.0e-4);
        assertTrue(editor.redo());
        assertEquals(after.getNormal().x, node.plane.getNormal().x, 1.0e-6);
    }

    @Test
    void colorPropertyUndoRedoRestoresRgba() throws Throwable {
        NodeGraph graph = new NodeGraph("color-history");
        ColorNode node = new ColorNode();
        node.tint = new ColorData(1f, 0f, 0f, 1f);
        graph.addNode(node);

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        editor.setCurrentGraph(graph);
        editor.getHistory().clear();

        PropertyInspector inspector = new PropertyInspector();
        PropertyDescriptor prop = inspector.getPropertiesForNode(ColorNode.class).stream()
                .filter(candidate -> "tint".equals(candidate.name))
                .findFirst()
                .orElseThrow();

        PropertyPanelComponent panel = new PropertyPanelComponent();
        panel.applyPropertyValue(node, prop, new ColorData(0.2f, 0.4f, 0.6f, 0.8f));

        assertEquals(0.2f, node.tint.r(), 1.0e-5f);
        assertTrue(editor.undo());
        assertEquals(1f, node.tint.r(), 1.0e-5f);
        assertTrue(editor.redo());
        assertEquals(0.6f, node.tint.b(), 1.0e-5f);
    }

    @Test
    void unisolatablePropertyWriteIsRefusedWithoutMutation() throws Throwable {
        NodeGraph graph = new NodeGraph("unisolatable-history");
        UnisolatableNode node = new UnisolatableNode();
        Object original = node.payload;
        Object next = unisolatableValue();
        graph.addNode(node);

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        editor.setCurrentGraph(graph);
        editor.getHistory().clear();

        PropertyInspector inspector = new PropertyInspector();
        PropertyDescriptor prop = inspector.getPropertiesForNode(UnisolatableNode.class).stream()
                .filter(candidate -> "payload".equals(candidate.name))
                .findFirst()
                .orElseThrow();

        PropertyPanelComponent panel = new PropertyPanelComponent();
        assertThrows(
                PropertyApplyException.class,
                () -> panel.applyPropertyValue(node, prop, next)
        );
        assertSame(original, node.payload);
        assertFalse(editor.getHistory().canUndo());
    }

    private static Object unisolatableValue() {
        return new Object() {
            @SuppressWarnings("unused")
            final byte[] payload = new byte[]{1, 2, 3};
        };
    }

    private static final class PlaneNode extends BaseNode {
        @NodeProperty(displayName = "Plane")
        PlaneData plane = PlaneData.XY_PLANE;

        PlaneNode() {
            super(UUID.randomUUID(), "test.stub.plane_prop");
        }

        @Override
        public String getDisplayName() {
            return "PlaneNode";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }

    private static final class ColorNode extends BaseNode {
        @NodeProperty(displayName = "Tint")
        ColorData tint = ColorData.WHITE;

        ColorNode() {
            super(UUID.randomUUID(), "test.stub.color_prop");
        }

        @Override
        public String getDisplayName() {
            return "ColorNode";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }

    private static final class UnisolatableNode extends BaseNode {
        @NodeProperty(displayName = "Payload")
        Object payload = unisolatableValue();

        UnisolatableNode() {
            super(UUID.randomUUID(), "test.stub.unisolatable_prop");
        }

        @Override
        public String getDisplayName() {
            return "UnisolatableNode";
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
