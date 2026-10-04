package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.output.export.ExportDataNode;
import com.nodecraft.nodesystem.nodes.output.export.ExportLitematicNode;
import com.nodecraft.nodesystem.nodes.output.export.ExportSchematicNode;
import com.nodecraft.nodesystem.nodes.output.export.ExportWorldEditNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.ExportDataEncoder;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputExportLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
        "output.export.export_schematic",
        "output.export.export_litematic",
        "output.export.export_worldedit",
        "output.export.export_data"
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void generationLimitsExposeExportCaps() {
        assertEquals(GenerationLimits.MAX_BLOCK_PLACEMENTS, GenerationLimits.MAX_EXPORT_PLACEMENTS);
        assertEquals(GenerationLimits.MAX_WORLD_WRITE_BLOCKS, GenerationLimits.MAX_DENSE_EXPORT_VOLUME);
        assertEquals(32_767, GenerationLimits.MAX_WORLD_EDIT_AXIS);
        assertEquals(65_536, GenerationLimits.MAX_EXPORT_ROWS);
        assertEquals(8, GenerationLimits.MAX_EXPORT_DEPTH);
        assertEquals(65_536, GenerationLimits.MAX_EXPORT_TEXT_CHARS);
    }

    @Test
    void exactlyFourOutputExportNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("output.export."))
            .sorted()
            .toList();
        assertEquals(CANONICAL_IDS, Set.copyOf(ids), ids.toString());
    }

    @Test
    void allExportNodesAreFileIoWithExecTrigger() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            assertNotNull(node, id);
            NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
            assertEquals(NodeEffect.FILE_IO, info.effect(), id);
            assertEquals(NodeEffect.FILE_IO, NodeEffectResolver.resolve(node.getClass(), id), id);
            assertEquals(NodeDataType.EXEC, findPort(node, "input_trigger").getDataType(), id);
        }
    }

    @Test
    void idleExecLeavesEmptyError() {
        ExportSchematicNode schematic = new ExportSchematicNode();
        schematic.processNode(null);
        assertEquals(Boolean.FALSE, schematic.getOutput("output_success"));
        assertEquals("", schematic.getOutput("output_error"));

        ExportDataNode data = new ExportDataNode();
        data.processNode(null);
        assertEquals(Boolean.FALSE, data.getOutput("output_success"));
        assertEquals("", data.getOutput("output_error"));
    }

    @Test
    void litematicFailsClosedOnHugeVolume() {
        ExportLitematicNode node = new ExportLitematicNode();
        node.setInput("input_trigger", Boolean.TRUE);
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 64, 0), "minecraft:stone"),
            new BlockPlacementData(new BlockPos(100_000, 64, 100_000), "minecraft:stone")
        ));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_success"));
        assertEquals("volume_exceeds_MAX_DENSE_EXPORT_VOLUME", node.getOutput("output_error"));
    }

    @Test
    void worldEditFailsClosedOnAxisOverflow() {
        ExportWorldEditNode node = new ExportWorldEditNode();
        node.setInput("input_trigger", Boolean.TRUE);
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone"),
            new BlockPlacementData(new BlockPos(40_000, 0, 0), "minecraft:stone")
        ));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_success"));
        assertEquals("axis_exceeds_MAX_WORLD_EDIT_AXIS", node.getOutput("output_error"));
    }

    @Test
    void schematicRejectsMalformedPlacementList() {
        ExportSchematicNode node = new ExportSchematicNode();
        node.setInput("input_trigger", Boolean.TRUE);
        List<Object> mixed = new ArrayList<>();
        mixed.add(new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone"));
        mixed.add("bad");
        node.setInput("input_placements", mixed);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_success"));
        assertFalse(((String) node.getOutput("output_error")).isBlank());
    }

    @Test
    void exportDataCycleFailsClosed() {
        List<Object> cyclic = new ArrayList<>();
        cyclic.add(cyclic);
        ExportDataEncoder.Result encoded = ExportDataEncoder.encodeJson(cyclic, false);
        assertFalse(encoded.valid());
        assertTrue(encoded.error().contains("cyclic"));

        ExportDataNode node = new ExportDataNode();
        node.setInput("input_trigger", Boolean.TRUE);
        node.setInput("input_format", "json");
        node.setInput("input_data", cyclic);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_success"));
        assertTrue(((String) node.getOutput("output_error")).contains("cyclic"));
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
