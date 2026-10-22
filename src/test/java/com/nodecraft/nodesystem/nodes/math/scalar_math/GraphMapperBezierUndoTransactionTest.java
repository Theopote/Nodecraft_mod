package com.nodecraft.nodesystem.nodes.math.scalar_math;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GraphMapperBezierUndoTransactionTest {

    @Test
    void bezierDragRestoresBothCoordinatesFromSingleSnapshot() throws Exception {
        GraphMapperNode node = new GraphMapperNode();
        node.setNodeState(Map.of(
            "bezierX1", 0.25d,
            "bezierY1", 0.25d,
            "bezierX2", 0.75d,
            "bezierY2", 0.75d));

        Object originalState = node.getNodeState();
        Object dragBefore = node.getNodeState();

        setField(node, "bezierDragBeforeState", dragBefore);
        node.setBezierX1(0.1d);
        node.setBezierY1(0.9d);

        assertNotEquals(originalState, node.getNodeState());

        node.setNodeState(originalState);
        assertEquals(0.25d, node.getBezierX1(), 1.0e-9);
        assertEquals(0.25d, node.getBezierY1(), 1.0e-9);
        assertEquals(0.75d, node.getBezierX2(), 1.0e-9);
        assertEquals(0.75d, node.getBezierY2(), 1.0e-9);

        invokeDragEnded(node);
        assertNull(getField(node, "bezierDragBeforeState"));
    }

    private static void invokeDragEnded(GraphMapperNode node) throws Exception {
        Method callbacks = GraphMapperNode.class.getDeclaredMethod("bezierDragCallbacks");
        callbacks.setAccessible(true);
        Object callback = callbacks.invoke(node);
        Method ended = callback.getClass().getDeclaredMethod("onBezierDragEnded",
            com.nodecraft.gui.components.customui.CurvePlotWidget.BezierHandle.class);
        ended.invoke(callback, com.nodecraft.gui.components.customui.CurvePlotWidget.BezierHandle.P1);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
