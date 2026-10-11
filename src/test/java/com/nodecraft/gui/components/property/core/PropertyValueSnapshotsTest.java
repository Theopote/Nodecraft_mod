package com.nodecraft.gui.components.property.core;

import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.Vec3;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class PropertyValueSnapshotsTest {

    @Test
    void vec3SnapshotIsIsolated() {
        Vec3 original = new Vec3(1.0d, 2.0d, 3.0d);
        Vec3 snapshot = (Vec3) PropertyValueSnapshots.copy(original);

        assertNotSame(original, snapshot);
        original.setX(99.0d);
        assertEquals(1.0d, snapshot.getX(), 0.0d);
    }

    @Test
    void vector3dSnapshotIsIsolated() {
        Vector3d original = new Vector3d(1.0d, 2.0d, 3.0d);
        Vector3d snapshot = (Vector3d) PropertyValueSnapshots.copy(original);

        assertNotSame(original, snapshot);
        original.x = 99.0d;
        assertEquals(1.0d, snapshot.x, 0.0d);
    }

    @Test
    void blockPosSnapshotIsIsolated() {
        BlockPos original = new BlockPos(1, 2, 3);
        BlockPos snapshot = (BlockPos) PropertyValueSnapshots.copy(original);

        assertEquals(original, snapshot);
        assertNotSame(original, snapshot);
    }

    @Test
    void listSnapshotIsIsolated() {
        List<Double> original = new ArrayList<>(List.of(1.0d, 2.0d));
        @SuppressWarnings("unchecked")
        List<Double> snapshot = (List<Double>) PropertyValueSnapshots.copy(original);

        assertNotSame(original, snapshot);
        original.set(0, 99.0d);
        assertEquals(1.0d, snapshot.get(0), 0.0d);
    }

    @Test
    void polylineSnapshotIsIsolated() {
        PolylineData original = new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0)));
        PolylineData snapshot = (PolylineData) PropertyValueSnapshots.copy(original);

        assertNotSame(original, snapshot);
        assertNotSame(original.points().get(0), snapshot.points().get(0));
        original.points().get(0).add(new Vec3d(5, 0, 0));
        assertEquals(new Vec3d(0, 0, 0), snapshot.points().get(0));
    }

    @Test
    void nodeStateMapIsIsolated() {
        Vec3 center = new Vec3(1.0d, 2.0d, 3.0d);
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) PropertyValueSnapshots.copyNodeState(Map.of("center", center));

        Vec3 copiedCenter = (Vec3) snapshot.get("center");
        assertNotSame(center, copiedCenter);
        center.setX(99.0d);
        assertEquals(1.0d, copiedCenter.getX(), 0.0d);
    }

    @Test
    void regionSnapshotIsIsolated() {
        BlockPos corner1 = new BlockPos(0, 0, 0);
        BlockPos corner2 = new BlockPos(2, 2, 2);
        RegionData original = new RegionData(corner1, corner2);
        RegionData snapshot = (RegionData) PropertyValueSnapshots.copy(original);

        assertNotSame(original, snapshot);
        assertEquals(original.corner1(), snapshot.corner1());
    }
}
