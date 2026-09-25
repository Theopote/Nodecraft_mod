package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.nodes.material.pattern_mapping.BrickPatternMapNode;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        int brickA = BrickPatternMapping.brickIndex(0, 0, 0, 2, 1, BrickPatternMapping.Axis.Z);
        int brickB = BrickPatternMapping.brickIndex(0, 0, 4, 2, 1, BrickPatternMapping.Axis.Z);

        assertNotEquals(brickA, brickB);
        assertEquals(
                BrickPatternMapping.brickIndex(0, 0, 0, 2, 1, BrickPatternMapping.Axis.X),
                BrickPatternMapping.brickIndex(0, 0, 4, 2, 1, BrickPatternMapping.Axis.X)
        );
    }

    @Test
    void brickIndexRejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> BrickPatternMapping.brickIndex(0, 0, 0, 0, 1, BrickPatternMapping.Axis.X));
        assertThrows(IllegalArgumentException.class,
                () -> BrickPatternMapping.brickIndex(0, 0, 0, 2, 0, BrickPatternMapping.Axis.X));
    }

    @Test
    void brickPatternMapNodeAppliesZAxisStaggerOnNorthSouthWall() {
        List<BlockPlacementData> wall = new java.util.ArrayList<>();
        for (int z = 0; z < 6; z++) {
            wall.add(new BlockPlacementData(new BlockPos(0, 0, z), "minecraft:oak_planks", null));
        }

        BrickPatternMapNode node = new BrickPatternMapNode();
        node.setInput("input_placements", wall);
        node.setInput("input_primary", "minecraft:bricks");
        node.setInput("input_secondary", "minecraft:stone_bricks");
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = (List<BlockPlacementData>) node.getOutput("output_placements");
        assertEquals(6, out.size());
        assertNotEquals(out.get(0).blockId(), out.get(2).blockId());
        assertNotEquals(out.get(1).blockId(), out.get(3).blockId());
    }

    @Test
    void staggerOffsetsOddCoursesAlongAxis() {
        int evenBrick = BrickPatternMapping.brickIndex(0, 0, 0, 4, 1, BrickPatternMapping.Axis.X);
        int oddBrick = BrickPatternMapping.brickIndex(0, 1, 0, 4, 1, BrickPatternMapping.Axis.X);

        assertEquals(0, evenBrick);
        assertEquals(BrickPatternMapping.brickIndex(2, 0, 0, 4, 1, BrickPatternMapping.Axis.X), oddBrick);
    }
}
