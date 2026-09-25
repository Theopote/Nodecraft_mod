package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Freeze fence for Batch 8 Material / Block Assignment contracts.
 */
class MaterialFamilyContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void materialNodesExposeCanonicalPlacementPorts() {
        assertPortType("material.basic_assignment.assign_block_type", "input_placements", true, NodeDataType.BLOCK_PLACEMENT_LIST);
        assertPortType("material.basic_assignment.assign_block_type", "output_placements", false, NodeDataType.BLOCK_PLACEMENT_LIST);
        assertPortType("material.directional_mapping.slope_map", "input_placements", true, NodeDataType.BLOCK_PLACEMENT_LIST);
        assertPortType("material.directional_mapping.top_side_bottom_map", "input_placements", true, NodeDataType.BLOCK_PLACEMENT_LIST);
        assertPortType("material.gradient_mapping.height_gradient_map", "input_placements", true, NodeDataType.BLOCK_PLACEMENT_LIST);
        assertPortType("output.preview.preview_blocks", "input_block_placements", true, NodeDataType.BLOCK_PLACEMENT_LIST);
        assertPortType("output.execute.apply_changes", "input_block_placements", true, NodeDataType.BLOCK_PLACEMENT_LIST);
    }

    @Test
    void materialRemapPreservesStateData() {
        BaseNode height = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("material.gradient_mapping.height_gradient_map"));

        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "east");
        state.setProperty("half", "top");
        BlockPlacementData incoming = new BlockPlacementData(
            new BlockPos(1, 10, 2),
            "minecraft:oak_stairs",
            state
        );

        height.setInput("input_placements", List.of(incoming));
        height.setInput("input_bottom", "minecraft:stone_bricks");
        height.setInput("input_middle", "minecraft:stone_bricks");
        height.setInput("input_top", "minecraft:stone_bricks");
        height.setInput("input_peak", "minecraft:stone_bricks");
        height.processNode(null);

        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, height.getOutput("output_placements"));
        assertEquals(1, out.size());
        BlockPlacementData remapped = out.getFirst();
        assertEquals("minecraft:stone_bricks", remapped.blockId());
        assertNotNull(remapped.stateData());
        assertEquals("east", remapped.stateData().get("facing"));
        assertEquals("top", remapped.stateData().get("half"));
    }

    @Test
    void previewBlockCarriesStateDataFromPlacement() {
        BaseNode preview = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("output.preview.preview_blocks"));

        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "north");
        BlockPlacementData placement = new BlockPlacementData(
            new BlockPos(3, 4, 5),
            "minecraft:oak_stairs",
            state
        );

        // Exercise toPreviewBlock path via process without world write by checking remapped payload construction
        // through Height Gradient → PreviewBlocks signature: construct PreviewBlock directly as protocol contract.
        PreviewBlock block = new PreviewBlock(3, 4, 5, placement.blockId(), placement.stateData());
        assertEquals("minecraft:oak_stairs", block.blockId());
        assertNotNull(block.stateData());
        assertEquals("north", block.stateData().get("facing"));

        // Ensure Preview Blocks node accepts placements port
        assertPortType("output.preview.preview_blocks", "input_block_placements", true, NodeDataType.BLOCK_PLACEMENT_LIST);
        preview.setInput("input_block_placements", List.of(placement));
    }

    @Test
    void slopeMapUsesFourNeighborGradeWithoutDirectionalBias() {
        BaseNode slope = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("material.directional_mapping.slope_map"));

        BlockPosList positions = new BlockPosList();
        // Flat column at (0,0) height 5, neighbor only at -X with height 7 → grade 2 → steep
        // Interior voxel at Y=4 must preserve source blockId (surface-only remap).
        positions.add(new BlockPos(0, 5, 0));
        positions.add(new BlockPos(0, 4, 0));
        positions.add(new BlockPos(-1, 7, 0));

        slope.setInput("input_coordinates", positions);
        slope.setInput("input_flat", "minecraft:grass_block");
        slope.setInput("input_slope", "minecraft:dirt");
        slope.setInput("input_steep", "minecraft:stone");
        slope.processNode(null);

        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, slope.getOutput("output_placements"));
        assertEquals(3, out.size());
        BlockPlacementData atOriginTop = out.stream()
            .filter(p -> p.pos().getX() == 0 && p.pos().getZ() == 0 && p.pos().getY() == 5)
            .findFirst()
            .orElseThrow();
        assertEquals("minecraft:stone", atOriginTop.blockId());

        BlockPlacementData atOriginInterior = out.stream()
            .filter(p -> p.pos().getX() == 0 && p.pos().getZ() == 0 && p.pos().getY() == 4)
            .findFirst()
            .orElseThrow();
        // Geometry path uses flat as fallback blockId for all generated placements; interior
        // voxels preserve that source id instead of remapping by slope grade.
        assertEquals("minecraft:grass_block", atOriginInterior.blockId());
    }

    @Test
    void withBlockIdPreservesStateWithoutTouchingMinecraftRegistry() {
        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "east");
        state.setProperty("half", "top");
        BlockPlacementData stairs = new BlockPlacementData(
            new BlockPos(0, 0, 0),
            "minecraft:oak_stairs",
            state
        );
        BlockPlacementData remapped = stairs.withBlockId("minecraft:stone");
        assertEquals("minecraft:stone", remapped.blockId());
        assertEquals("east", remapped.stateData().get("facing"));
        assertEquals("top", remapped.stateData().get("half"));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        assertInstanceOf(INode.class, node);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
            .filter(candidate -> candidate.getId().equals(portId))
            .findFirst()
            .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }
}
