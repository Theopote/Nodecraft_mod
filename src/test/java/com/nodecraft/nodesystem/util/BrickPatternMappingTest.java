package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.nodes.material.pattern_mapping.BrickPatternMapNode;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BrickPatternMappingTest {

    @Test
    void resolveAxisPrefersZForNorthSouthWall() {
        BlockPosList wall = new BlockPosList();
        for (int z = 0; z < 12; z++) {
            wall.add(new BlockPos(4, 0, z));
        }

        assertEquals(BrickPatternMapping.Axis.Z, BrickPatternMapping.resolveAxis(wall));
    }

    @Test
    void resolveAxisPrefersXForEastWestWall() {
        BlockPosList wall = new BlockPosList();
        for (int x = 0; x < 12; x++) {
            wall.add(new BlockPos(x, 0, 4));
        }

        assertEquals(BrickPatternMapping.Axis.X, BrickPatternMapping.resolveAxis(wall));
    }

    @Test
    void brickIndexVariesAlongDetectedAxis() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(0, 0, 4);

        int brickA = BrickPatternMapping.brickIndex(a, 2, 1, BrickPatternMapping.Axis.Z);
        int brickB = BrickPatternMapping.brickIndex(b, 2, 1, BrickPatternMapping.Axis.Z);

        assertNotEquals(brickA, brickB);
        assertEquals(
                BrickPatternMapping.brickIndex(a, 2, 1, BrickPatternMapping.Axis.X),
                BrickPatternMapping.brickIndex(b, 2, 1, BrickPatternMapping.Axis.X)
        );
    }

    @Test
    void brickPatternMapNodeAppliesZAxisStaggerOnNorthSouthWall() {
        BlockPosList wall = new BlockPosList();
        for (int z = 0; z < 6; z++) {
            wall.add(new BlockPos(0, 0, z));
        }

        BrickPatternMapNode node = new BrickPatternMapNode();
        node.compute(Map.of(
                "input_coordinates", wall,
                "input_primary", "minecraft:bricks",
                "input_secondary", "minecraft:stone_bricks"
        ));

        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) node.getOutput("output_block_ids");
        assertEquals(6, ids.size());
        assertNotEquals(ids.get(0), ids.get(2));
        assertNotEquals(ids.get(1), ids.get(3));
    }

    @Test
    void staggerOffsetsOddCoursesAlongAxis() {
        BlockPos evenCourse = new BlockPos(0, 0, 0);
        BlockPos oddCourse = new BlockPos(0, 1, 0);

        int evenBrick = BrickPatternMapping.brickIndex(evenCourse, 4, 1, BrickPatternMapping.Axis.X);
        int oddBrick = BrickPatternMapping.brickIndex(oddCourse, 4, 1, BrickPatternMapping.Axis.X);

        assertEquals(0, evenBrick);
        assertEquals(BrickPatternMapping.brickIndex(new BlockPos(2, 0, 0), 4, 1, BrickPatternMapping.Axis.X), oddBrick);
    }
}
