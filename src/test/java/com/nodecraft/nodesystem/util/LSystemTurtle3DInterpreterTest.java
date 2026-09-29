package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PathData;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LSystemTurtle3DInterpreterTest {

    @Test
    void longRotationSequenceKeepsUnitForward() {
        String commands = "+".repeat(10_000) + "F";
        LSystemTurtle3DInterpreter.TurtleResult result = LSystemTurtle3DInterpreter.interpret(
            commands,
            new Vector3d(),
            1.0d,
            25.0d,
            GenerationLimits.MAX_LSYSTEM_COMMAND_LENGTH,
            GenerationLimits.maxLSystemTurtleSegments(),
            GenerationLimits.MAX_LSYSTEM_TURTLE_STACK_DEPTH
        );
        assertTrue(result.valid());
        assertEquals(1, result.segmentCount());
        PathData path = result.paths().getFirst();
        assertNotNull(path.getLine());
        assertEquals(1.0d, path.getLine().getLength(), 1.0e-3d);
    }
}
