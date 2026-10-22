package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.nodesystem.util.BlockPaletteData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateBlockPaletteDraftStateTest {

    @Test
    void draftPaletteRoundTripsThroughNodeState() {
        CreateBlockPaletteNode node = new CreateBlockPaletteNode();
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) node.getNodeState();
        state.put("draftBlockIds", List.of("minecraft:stone", "minecraft:dirt"));
        state.put("draftWeights", List.of(1.0d, 2.0d));
        node.setNodeState(state);

        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        BlockPaletteData palette = assertInstanceOf(BlockPaletteData.class, node.getOutput("output_palette"));
        assertEquals(List.of("minecraft:stone", "minecraft:dirt"), palette.blockIds());
        assertEquals(List.of(1.0d, 2.0d), palette.weights());
    }

    @Test
    void portDrivenInputsIgnoreDraft() {
        CreateBlockPaletteNode node = new CreateBlockPaletteNode();
        node.setNodeState(Map.of("draftBlockIds", List.of("minecraft:gravel")));
        node.setInput("input_block_ids", List.of("minecraft:stone"));
        node.processNode(null);

        BlockPaletteData palette = assertInstanceOf(BlockPaletteData.class, node.getOutput("output_palette"));
        assertEquals(List.of("minecraft:stone"), palette.blockIds());
    }

    @Test
    void emptyDraftProducesEmptyValidPalette() {
        CreateBlockPaletteNode node = new CreateBlockPaletteNode();
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        BlockPaletteData palette = assertInstanceOf(BlockPaletteData.class, node.getOutput("output_palette"));
        assertTrue(palette.isEmpty());
    }
}
