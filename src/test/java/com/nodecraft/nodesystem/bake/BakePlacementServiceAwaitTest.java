package com.nodecraft.nodesystem.bake;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BakePlacementServiceAwaitTest {

    @Test
    void awaitTaskTerminalStateReturnsNullStateForUnknownTask() {
        BakePlacementService.AwaitResult result = BakePlacementService.getInstance()
                .awaitTaskTerminalState(UUID.randomUUID(), System.currentTimeMillis() + 10L);
        assertNull(result.state());
        assertFalse(result.completed());
    }

    @Test
    void awaitTaskCompletionDelegatesToTerminalState() {
        UUID unknown = UUID.randomUUID();
        BakePlacementService service = BakePlacementService.getInstance();
        assertFalse(service.awaitTaskCompletion(unknown, System.currentTimeMillis() + 10L));
        assertFalse(service.awaitTaskTerminalState(unknown, System.currentTimeMillis() + 10L).completed());
    }
}
