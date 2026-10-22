package com.nodecraft.nodesystem.nodes.input.values;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradientRampNodeStateTest {

    @Test
    void getNodeStateSerializesStops() {
        GradientRampNode node = new GradientRampNode();
        Object state = node.getNodeState();
        assertInstanceOf(Map.class, state);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stops = (List<Map<String, Object>>) ((Map<?, ?>) state).get("stops");
        assertTrue(stops.size() >= 2);
    }

    @Test
    void getNodeStateDoesNotThrowUnderConcurrentStopEdits() throws Exception {
        GradientRampNode node = new GradientRampNode();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 5000; i++) {
                    node.getNodeState();
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }, "gradient-ramp-state-reader");

        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 5000; i++) {
                    Object snapshot = node.getNodeState();
                    node.setNodeState(snapshot);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }, "gradient-ramp-state-writer");

        reader.start();
        writer.start();
        reader.join(30_000);
        writer.join(30_000);

        assertNull(failure.get(), () -> "getNodeState/setNodeState must share stopsLock: " + failure.get());
    }
}
