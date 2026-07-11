package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.datatypes.GridScalarFieldData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainErosionFieldTest {

    @Test
    void chainedThermalErosionOutputsMaterializedGrids() {
        ScalarFieldData seed = point -> 0.42d;
        ThermalErosionStepNode step = new ThermalErosionStepNode();
        ScalarFieldData current = seed;

        for (int i = 0; i < 20; i++) {
            step.compute(Map.of("input_height_field", current));
            Object output = step.getOutput("output_height_field");
            assertInstanceOf(GridScalarFieldData.class, output);
            current = (ScalarFieldData) output;
        }

        long start = System.nanoTime();
        Vector3d samplePoint = new Vector3d(0.0d, 64.0d, 0.0d);
        for (int i = 0; i < 2_000; i++) {
            current.sampleScalar(samplePoint);
        }
        long elapsedNanos = System.nanoTime() - start;
        assertTrue(elapsedNanos < 50_000_000L, "Sampling materialized field should stay fast, took " + elapsedNanos + "ns");
    }

    @Test
    void hydraulicErosionOutputsMaterializedGrids() {
        ScalarFieldData height = point -> 0.35d;
        ScalarFieldData accumulation = point -> 0.5d;

        HydraulicErosionStepNode step = new HydraulicErosionStepNode();
        step.compute(Map.of(
            "input_height_field", height,
            "input_accumulation_field", accumulation
        ));

        assertInstanceOf(GridScalarFieldData.class, step.getOutput("output_eroded_field"));
        assertInstanceOf(GridScalarFieldData.class, step.getOutput("output_sediment_field"));
        assertInstanceOf(GridScalarFieldData.class, step.getOutput("output_delta_field"));
    }
}
