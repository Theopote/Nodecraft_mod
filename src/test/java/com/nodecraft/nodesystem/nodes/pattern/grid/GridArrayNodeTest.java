package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridArrayNodeTest {

    @Test
    void hugeGridCountsFailClosedWithoutClamp() {
        GridArrayNode node = new GridArrayNode();
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 0.5d);
        node.setInput("input_geometry", sphere);
        node.setNodeState(Map.of(
            "xCount", Integer.MAX_VALUE,
            "yCount", Integer.MAX_VALUE,
            "zCount", Integer.MAX_VALUE
        ));
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_count"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase().contains("budget")
            || String.valueOf(node.getOutput("output_error")).toLowerCase().contains("overflow")
            || String.valueOf(node.getOutput("output_error")).toLowerCase().contains("count"));
        assertTrue(GenerationLimits.MAX_GEOMETRY_INSTANCES > 0);
    }
}
