package com.nodecraft.nodesystem.nodes.flow.loop;

import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhileLoopNodeTest {

    @Test
    void maxIterationsPropertyIsCappedByGenerationLimits() {
        WhileLoopNode node = new WhileLoopNode();
        node.setNodeState(Map.of("maxIterations", Integer.MAX_VALUE));

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) node.getNodeState();

        assertEquals(GenerationLimits.MAX_LOOP_ITERATIONS, state.get("maxIterations"));
    }
}
