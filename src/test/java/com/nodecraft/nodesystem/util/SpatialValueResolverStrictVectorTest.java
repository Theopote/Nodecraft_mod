package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PointData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpatialValueResolverStrictVectorTest {

    @Test
    void resolveVectorRejectsPointAndBlockPos() {
        assertNull(SpatialValueResolver.resolveVector(new PointData(new Vector3d(1, 2, 3))));
        assertNull(SpatialValueResolver.resolveVector(new BlockPos(1, 2, 3)));
    }

    @Test
    void resolveVectorAcceptsVector3d() {
        Vector3d resolved = SpatialValueResolver.resolveVector(new Vector3d(0, 1, 0));
        assertNotNull(resolved);
        assertEquals(0.0d, resolved.x);
        assertEquals(1.0d, resolved.y);
        assertEquals(0.0d, resolved.z);
    }

    @Test
    void resolvePointStillAcceptsPointAndBlockPos() {
        assertNotNull(SpatialValueResolver.resolvePoint(new PointData(new Vector3d(4, 5, 6))));
        assertNotNull(SpatialValueResolver.resolvePoint(new BlockPos(4, 5, 6)));
    }
}
