package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GridArrayNodeTest {

    @Test
    void hugeGridCountsAreClampedBeforeGeneration() {
        GridArrayNode node = new GridArrayNode();
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 0.5d);

        Map<String, Object> outputs = node.compute(Map.of(
            "input_geometry", sphere,
            "input_x_count", Integer.MAX_VALUE,
            "input_y_count", Integer.MAX_VALUE,
            "input_z_count", Integer.MAX_VALUE
        ));

        @SuppressWarnings("unchecked")
        java.util.List<Object> geometries = (java.util.List<Object>) outputs.get("output_geometries");
        assertTrue(geometries.size() <= GenerationLimits.MAX_GEOMETRY_INSTANCES);
    }
}
