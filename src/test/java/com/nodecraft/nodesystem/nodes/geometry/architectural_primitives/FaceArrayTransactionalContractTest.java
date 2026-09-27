package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks transactional face-array build: factory null aborts the whole array (no partial list).
 */
class FaceArrayTransactionalContractTest {

    @Test
    void faceArrayFactoryFailureDoesNotProducePartialOutput() {
        FaceArrayProbe probe = new FaceArrayProbe();
        BoxFaceData face = new BoxFaceData(
            0, "front", List.of(0, 1, 2, 3),
            List.of(
                new Vector3d(0, 0, 0),
                new Vector3d(10, 0, 0),
                new Vector3d(10, 5, 0),
                new Vector3d(0, 5, 0)
            ),
            new Vector3d(5, 2.5, 0),
            new Vector3d(0, 0, 1)
        );
        AbstractFaceArrayNode.FaceArrayLayout layout = probe.resolveFaceArrayLayout(
            face, 3, 2, 1.0d, 1.0d, 0.5d, AbstractFaceArrayNode.VerticalAnchor.BOTTOM);
        assertTrue(layout != null);

        AtomicInteger calls = new AtomicInteger();
        List<GeometryData> partial = probe.build(
            layout,
            placement -> {
                int n = calls.incrementAndGet();
                if (n == 4) {
                    return null;
                }
                return new BoxGeometryData(placement.centerOnFace(), new Vector3d(0.5d, 0.5d, 0.5d));
            }
        );

        assertNull(partial);
        assertEquals(4, calls.get(), "must stop at first null (no silent skip / continue)");
    }

    private static final class FaceArrayProbe extends AbstractFaceArrayNode {
        FaceArrayProbe() {
            super(UUID.randomUUID(), "test.face_array_probe");
        }

        @Override
        public void processNode(ExecutionContext context) {
        }

        @Nullable List<GeometryData> build(
            FaceArrayLayout layout,
            FaceArrayGeometryFactory<GeometryData> factory
        ) {
            return buildFaceArray(layout, factory);
        }
    }
}
