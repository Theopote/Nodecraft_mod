package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewBlocksNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputPreviewLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void generationLimitsExposePreviewCaps() {
        assertEquals(20_000, GenerationLimits.MAX_PREVIEW_BLOCKS);
        assertEquals(GenerationLimits.MAX_PREVIEW_BLOCKS, GenerationLimits.MAX_PREVIEW_POINTS);
        assertEquals(GenerationLimits.MAX_PREVIEW_BLOCKS, GenerationLimits.MAX_PREVIEW_CURVE_POINTS);
        assertEquals(4_096, GenerationLimits.MAX_PREVIEW_LABELS);
    }

    @Test
    void previewBlocksHasNoCoordinatesListPort() {
        INode node = registry.createNodeInstance("output.preview.preview_blocks");
        assertNotNull(node);
        assertNull(findPort(node, "input_coords"));
        assertEquals(NodeDataType.BLOCK_LIST, findPort(node, "input_blocks").getDataType());
        assertEquals(NodeDataType.BLOCK_PLACEMENT_LIST, findPort(node, "input_block_placements").getDataType());
        assertEquals(NodeDataType.DATA_TREE, findPort(node, "input_block_placements_tree").getDataType());
        assertNotNull(findPort(node, "output_source_count"));
        assertNotNull(findPort(node, "output_preview_count"));
        assertNotNull(findPort(node, "output_truncated"));
        assertNotNull(findPort(node, "output_error"));
        NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
        assertEquals(NodeEffect.PREVIEW_WRITE, info.effect());
    }

    @Test
    void previewBlocksTruncatesAtMaxPreviewBlocks() {
        PreviewBlocksNode node = new PreviewBlocksNode();
        List<BlockPlacementData> placements = new ArrayList<>(GenerationLimits.MAX_PREVIEW_BLOCKS + 50);
        for (int i = 0; i < GenerationLimits.MAX_PREVIEW_BLOCKS + 50; i++) {
            placements.add(new BlockPlacementData(new BlockPos(i, 64, 0), "minecraft:stone"));
        }
        node.setInput("input_block_placements", placements);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_truncated"));
        assertEquals(GenerationLimits.MAX_PREVIEW_BLOCKS + 50, node.getOutput("output_source_count"));
        assertEquals(GenerationLimits.MAX_PREVIEW_BLOCKS, node.getOutput("output_preview_count"));
        assertEquals(GenerationLimits.MAX_PREVIEW_BLOCKS, node.getOutput("output_block_count"));
    }

    @Test
    void previewBlocksPublishesOutputsOnEveryProcessNode() {
        PreviewBlocksNode node = new PreviewBlocksNode();
        node.setInput("input_block_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 64, 0), "minecraft:stone")));
        node.processNode(null);
        assertEquals(1, node.getOutput("output_preview_count"));

        List<BlockPlacementData> larger = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            larger.add(new BlockPlacementData(new BlockPos(i, 64, 0), "minecraft:stone"));
        }
        node.setInput("input_block_placements", larger);
        node.processNode(null);
        assertEquals(5, node.getOutput("output_preview_count"));
        assertEquals(5, node.getOutput("output_source_count"));
        assertEquals(Boolean.FALSE, node.getOutput("output_truncated"));
    }

    @Test
    void previewBlocksSourceHasNoNodeThrottleReturn() throws Exception {
        String src = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/output/preview/PreviewBlocksNode.java"));
        assertFalse(src.contains("MIN_EXECUTION_INTERVAL_MS"));
        assertFalse(src.contains("EMPTY_INPUT_HOLD_MS"));
        assertFalse(src.contains("input_coords"));
        assertTrue(src.contains("MAX_PREVIEW_BLOCKS"));
        assertTrue(src.toLowerCase(Locale.ROOT).contains("truncated"));
    }

    @Test
    void previewFamilyNodesDoNotEarlyReturnOnThrottle() throws Exception {
        List<String> files = List.of(
            "PreviewBlocksNode.java",
            "PreviewGeometryNode.java",
            "PreviewPointsNode.java",
            "PreviewVectorsNode.java",
            "PreviewPathsNode.java",
            "PreviewRegionsNode.java",
            "PreviewPlaneNode.java",
            "PreviewFrameNode.java",
            "PreviewLabelsNode.java",
            "PreviewPolygonProfilesNode.java",
            "PreviewSurfaceStripNode.java"
        );
        for (String file : files) {
            String src = Files.readString(Path.of(
                "src/main/java/com/nodecraft/nodesystem/nodes/output/preview/" + file));
            assertFalse(src.contains("MIN_EXECUTION_INTERVAL_MS"), file + " still has node throttle");
        }
    }

    private static IPort findPort(INode node, String portId) {
        for (IPort port : node.getInputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }
}
