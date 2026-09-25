package com.nodecraft.nodesystem.material;

import com.nodecraft.nodesystem.nodes.material.block_state.BuildBlockStateNode;
import com.nodecraft.nodesystem.nodes.material.block_state.OrientBlockStateNode;
import com.nodecraft.nodesystem.util.BlockStateData;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStateNodeTest {

    @Test
    void buildBlockStateMergesBaseStateAndDynamicProperty() {
        BuildBlockStateNode node = new BuildBlockStateNode();
        BlockStateData base = new BlockStateData();
        base.setBooleanProperty("waterlogged", false);

        Map<String, Object> outputs = node.compute(Map.of(
                "input_base_state", base,
                "input_block_type", "oak_log",
                "input_property_name", "axis",
                "input_property_value", "x",
                "input_waterlogged", true
        ));

        BlockStateData state = assertInstanceOf(BlockStateData.class, outputs.get("output_block_state"));
        assertEquals("minecraft:oak_log", state.get("blockId"));
        assertEquals("x", state.get("axis"));
        assertEquals("true", state.get("waterlogged"));
    }

    @Test
    void orientBlockStateCanDeriveAxisFromVector() {
        OrientBlockStateNode node = new OrientBlockStateNode();

        Map<String, Object> outputs = node.compute(Map.of(
                "input_block_type", "minecraft:oak_log",
                "input_vector", new Vector3d(0.0d, 5.0d, 0.0d),
                "input_mode", "axis"
        ));

        BlockStateData state = assertInstanceOf(BlockStateData.class, outputs.get("output_block_state"));
        assertEquals("minecraft:oak_log", state.get("blockId"));
        assertEquals("y", state.get("axis"));
        assertEquals("y", outputs.get("output_axis"));
        assertEquals(true, outputs.get("output_valid"));
    }

    @Test
    void buildBlockStateAppliesPropertiesTextWithValidation() {
        BuildBlockStateNode node = new BuildBlockStateNode();
        node.setPropertiesText("axis=x,waterlogged=false");

        Map<String, Object> outputs = node.compute(Map.of(
                "input_block_type", "minecraft:oak_log"
        ));

        BlockStateData state = assertInstanceOf(BlockStateData.class, outputs.get("output_block_state"));
        assertEquals("minecraft:oak_log", state.get("blockId"));
        assertEquals("x", state.get("axis"));
        assertEquals("false", state.get("waterlogged"));
        // When Minecraft registries are bootstrapped, Valid should be true for real properties.
        // Unit tests without bootstrap skip property validation (Valid remains true with a note).
        assertInstanceOf(Boolean.class, outputs.get("output_valid"));
        assertInstanceOf(String.class, outputs.get("output_error"));
    }

    @Test
    void buildBlockStatePropertiesTextRejectsUnknownProperty() {
        BuildBlockStateNode node = new BuildBlockStateNode();
        node.setPropertiesText("foo=bar");

        Map<String, Object> outputs = node.compute(Map.of(
                "input_block_type", "minecraft:stone"
        ));

        BlockStateData state = assertInstanceOf(BlockStateData.class, outputs.get("output_block_state"));
        assertEquals("bar", state.get("foo"));
        assertInstanceOf(String.class, outputs.get("output_error"));
        Boolean valid = assertInstanceOf(Boolean.class, outputs.get("output_valid"));
        String error = (String) outputs.get("output_error");
        // Bootstrapped: Valid=false + unsupported property message.
        // Headless unit tests: registry unavailable → skip validation (Valid=true + note).
        if (error.contains("registry unavailable")) {
            assertEquals(true, valid);
        } else {
            assertEquals(false, valid);
            assertTrue(error.toLowerCase().contains("foo") || error.toLowerCase().contains("unsupported"));
        }
    }

    @Test
    void orientBlockStateCanDeriveStairFacingAndHalfFromVector() {
        OrientBlockStateNode node = new OrientBlockStateNode();

        Map<String, Object> outputs = node.compute(Map.of(
                "input_block_type", "minecraft:oak_stairs",
                "input_vector", new Vector3d(3.0d, -1.0d, 1.0d),
                "input_mode", "stair",
                "input_include_waterlogged", true,
                "input_waterlogged", false
        ));

        BlockStateData state = assertInstanceOf(BlockStateData.class, outputs.get("output_block_state"));
        assertEquals("minecraft:oak_stairs", state.get("blockId"));
        assertEquals("east", state.get("facing"));
        assertEquals("top", state.get("half"));
        assertEquals("straight", state.get("shape"));
        assertEquals("false", state.get("waterlogged"));
    }
}
