package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportBoundsTest {

    @Test
    void usesLongSizesAndCheckedVolume() {
        ExportBounds bounds = ExportBounds.fromPositions(List.of(
            new BlockPos(0, 0, 0),
            new BlockPos(2, 0, 0)
        ));
        assertEquals(3L, bounds.sizeX());
        assertEquals(1L, bounds.sizeY());
        assertEquals(1L, bounds.sizeZ());
        assertEquals(3L, bounds.checkedVolume());
        assertNull(bounds.validateDense(GenerationLimits.MAX_DENSE_EXPORT_VOLUME, null));
    }

    @Test
    void rejectsDenseVolumeAboveCap() {
        ExportBounds bounds = ExportBounds.fromPositions(List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(100_000, 64, 100_000)
        ));
        String error = bounds.validateDense(GenerationLimits.MAX_DENSE_EXPORT_VOLUME, null);
        assertEquals("volume_exceeds_MAX_DENSE_EXPORT_VOLUME", error);
    }

    @Test
    void rejectsWorldEditAxisAboveShort() {
        ExportBounds bounds = ExportBounds.fromPositions(List.of(
            new BlockPos(0, 0, 0),
            new BlockPos(40_000, 0, 0)
        ));
        String error = bounds.validateDense(
            GenerationLimits.MAX_DENSE_EXPORT_VOLUME,
            GenerationLimits.MAX_WORLD_EDIT_AXIS
        );
        assertEquals("axis_exceeds_MAX_WORLD_EDIT_AXIS", error);
    }

    @Test
    void sparseValidationAcceptsLargeButIntSizedAxes() {
        ExportBounds bounds = ExportBounds.fromPositions(List.of(
            new BlockPos(0, 0, 0),
            new BlockPos(1000, 0, 0)
        ));
        assertNull(bounds.validateSparse());
        assertTrue(bounds.sizeXInt() > 0);
    }
}
