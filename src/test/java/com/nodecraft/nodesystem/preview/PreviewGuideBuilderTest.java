package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewGuideBuilderTest {

    @Test
    void buildsBoundingBoxLabelsAndPivotFromBlocks() {
        BlockPosList blocks = new BlockPosList()
            .add(new BlockPos(10, 64, 20))
            .add(new BlockPos(12, 68, 23));

        Optional<PreviewGuideBuilder.GuideData> result = PreviewGuideBuilder.fromBlocks(blocks);

        assertTrue(result.isPresent());
        PreviewGuideBuilder.GuideData guide = result.get();
        assertEquals(3, guide.length());
        assertEquals(4, guide.width());
        assertEquals(5, guide.height());
        assertEquals(new Vec3d(11.5d, 66.5d, 22.0d), guide.pivot());
        assertEquals("L3 x W4 x H5", guide.dimensionsLabel().text());
        assertEquals(new Vec3d(0.0d, 1.0d, 0.0d), guide.tangentDirection());
    }

    @Test
    void returnsEmptyForNoBlocks() {
        assertTrue(PreviewGuideBuilder.fromBlocks(new BlockPosList()).isEmpty());
    }
}
