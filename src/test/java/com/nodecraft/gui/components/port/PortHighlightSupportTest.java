package com.nodecraft.gui.components.port;

import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.Vec3;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortHighlightSupportTest {

    @Test
    void previewOwnerIdIsStable() {
        UUID nodeId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals(nodeId + ":property-panel-preview", PortHighlightSupport.previewOwnerId(nodeId));
    }

    @Test
    void vec3FloorsToBlockCoordinate() {
        Coordinate coordinate = PortHighlightSupport.vec3ToCoordinate(new Vec3(10.9d, -1.2d, 3.0d));
        assertEquals(10, coordinate.x());
        assertEquals(-2, coordinate.y());
        assertEquals(3, coordinate.z());
    }

    @Test
    void vec3ListToCoordinatesFiltersNonVec3Entries() {
        List<Coordinate> coordinates = PortHighlightSupport.vec3ListToCoordinates(
                List.of(new Vec3(1.0d, 2.0d, 3.0d), "not-a-point", new Vec3(4.5d, 5.5d, 6.5d))
        );
        assertEquals(2, coordinates.size());
        assertEquals(new Coordinate(1, 2, 3), coordinates.get(0));
        assertEquals(new Coordinate(4, 5, 6), coordinates.get(1));
    }

    @Test
    void extractRegionBoundsUsesMaxExclusiveCorners() {
        TestRegion region = new TestRegion(new Vec3(1.0d, 2.0d, 3.0d), new Vec3(4.0d, 5.0d, 6.0d));
        PortHighlightSupport.RegionBounds bounds = PortHighlightSupport.extractRegionBounds(region).orElseThrow();

        assertEquals(new Vec3d(1.0d, 2.0d, 3.0d), bounds.min());
        assertEquals(new Vec3d(5.0d, 6.0d, 7.0d), bounds.maxExclusive());
    }

    @Test
    void extractRegionBoundsReturnsEmptyForUnsupportedType() {
        assertTrue(PortHighlightSupport.extractRegionBounds(new Object()).isEmpty());
    }

    private static final class TestRegion {
        private final Vec3 min;
        private final Vec3 max;

        private TestRegion(Vec3 min, Vec3 max) {
            this.min = min;
            this.max = max;
        }

        public Vec3 getMin() {
            return min;
        }

        public Vec3 getMax() {
            return max;
        }

        public int getBlockCount() {
            return 1;
        }
    }
}
