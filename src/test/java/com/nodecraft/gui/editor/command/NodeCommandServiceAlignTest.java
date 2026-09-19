package com.nodecraft.gui.editor.command;

import com.nodecraft.gui.editor.impl.NodePosition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCommandServiceAlignTest {

    @Test
    void alignLeftMovesAllToMinimumX() {
        List<NodePosition> positions = new ArrayList<>();
        positions.add(pos(10, 0));
        positions.add(pos(40, 0));
        positions.add(pos(25, 0));

        assertTrue(NodeCommandService.alignLeft(positions));
        assertEquals(10f, positions.get(0).x);
        assertEquals(10f, positions.get(1).x);
        assertEquals(10f, positions.get(2).x);
        assertFalse(NodeCommandService.alignLeft(positions));
    }

    @Test
    void alignCenterSharesMidpoint() {
        List<NodePosition> positions = new ArrayList<>();
        NodePosition left = pos(0, 0);
        left.width = 100;
        NodePosition right = pos(200, 0);
        right.width = 100;
        positions.add(left);
        positions.add(right);

        assertTrue(NodeCommandService.alignCenter(positions));
        assertEquals(150f, left.x + left.width / 2.0f, 1.0e-4f);
        assertEquals(150f, right.x + right.width / 2.0f, 1.0e-4f);
    }

    @Test
    void distributeHorizontalNeedsThreeNodes() {
        List<NodePosition> two = new ArrayList<>();
        two.add(pos(0, 0));
        two.add(pos(100, 0));
        assertFalse(NodeCommandService.distributeHorizontal(two));

        List<NodePosition> three = new ArrayList<>();
        NodePosition a = pos(0, 0);
        a.width = 10;
        NodePosition b = pos(10, 0);
        b.width = 10;
        NodePosition c = pos(100, 0);
        c.width = 10;
        three.add(a);
        three.add(b);
        three.add(c);
        assertTrue(NodeCommandService.distributeHorizontal(three));
        assertEquals(55f, b.x + b.width / 2.0f, 1.0e-3f);
    }

    private static NodePosition pos(float x, float y) {
        return new NodePosition(x, y);
    }
}
