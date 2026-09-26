package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ImageData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.utilities.fileio.ImageSamplerNode;
import com.nodecraft.nodesystem.nodes.utilities.fileio.ImportVoxNode;
import com.nodecraft.nodesystem.nodes.utilities.fileio.ReadImageNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.ImportAccessPolicy;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileIO v1 language fence (Graph V56).
 */
class FileIoLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "utilities.fileio.read_image",
            "utilities.fileio.image_sampler",
            "utilities.fileio.import_vox"
    );

    private static NodeRegistry registry;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @BeforeEach
    void allowImports() {
        ImportAccessPolicy.setCurrent((path, kind) -> true);
    }

    @AfterEach
    void resetPolicy() {
        ImportAccessPolicy.reset();
    }

    @Test
    void currentGraphFormatIsAtLeastV56() {
        assertEquals(56, GraphFormatVersion.V56);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V56);
    }

    @Test
    void exactlyThreeCanonicalFileIoNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("utilities.fileio."))
                .sorted()
                .toList();
        assertEquals(3, ids.size(), "Expected 3 fileio nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
    }

    @Test
    void fileIoNodesHaveUniqueOrderZeroThroughTwo() {
        int[] orders = CANONICAL_IDS.stream()
                .mapToInt(typeId -> {
                    INode created = registry.createNodeInstance(typeId);
                    assertNotNull(created, typeId);
                    NodeInfo info = created.getClass().getAnnotation(NodeInfo.class);
                    assertNotNull(info, typeId);
                    return info.order();
                })
                .sorted()
                .toArray();
        assertEquals(3, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void imageDataCreateValidatesSampleDimsMatchColors() {
        List<ColorData> colors = List.of(
                ColorData.RED, ColorData.GREEN,
                ColorData.BLUE, ColorData.WHITE
        );
        ImageData image = ImageData.create(4, 4, 2, 2, 2, colors);
        assertEquals(4, image.sourceWidth());
        assertEquals(2, image.sampleWidth());
        assertEquals(4, image.colors().size());

        assertThrows(IllegalArgumentException.class,
                () -> ImageData.create(2, 2, 2, 2, 1, List.of(ColorData.RED)));
    }

    @Test
    void readImageDownsampledFeedsSamplerViaImageWire() throws Exception {
        Path file = tempDir.resolve("sample.png");
        writeSolidPng(file, 4, 4, 0xFFFF0000);

        ReadImageNode reader = new ReadImageNode();
        reader.setReadMode(ReadImageNode.ReadMode.DOWNSAMPLED);
        reader.setInput("input_path", file.toString());
        reader.processNode(null);

        assertEquals(Boolean.TRUE, reader.getOutput("output_valid"));
        ImageData image = assertInstanceOf(ImageData.class, reader.getOutput("output_image"));
        assertEquals(image.sampleWidth() * image.sampleHeight(), image.colors().size());

        ImageSamplerNode sampler = new ImageSamplerNode();
        connectInput(sampler, "input_image", NodeDataType.IMAGE);
        sampler.setInput("input_image", image);
        sampler.setInput("input_u", 0.0d);
        sampler.setInput("input_v", 0.0d);
        sampler.processNode(null);

        assertEquals(Boolean.TRUE, sampler.getOutput("output_valid"));
        ColorData color = assertInstanceOf(ColorData.class, sampler.getOutput("output_color"));
        assertEquals(1.0f, color.r(), 0.01f);
    }

    @Test
    void fullModeRejectsOversizedMetadataBeforeDecode() throws Exception {
        Path file = tempDir.resolve("huge_meta.png");
        Files.write(file, createPngWithIhdr(2000, 2000));

        ReadImageNode reader = new ReadImageNode();
        reader.setReadMode(ReadImageNode.ReadMode.FULL);
        reader.setInput("input_path", file.toString());
        reader.processNode(null);

        assertEquals(Boolean.FALSE, reader.getOutput("output_valid"));
        String error = assertInstanceOf(String.class, reader.getOutput("output_error"));
        assertTrue(error.toLowerCase(Locale.ROOT).contains("pixels"), error);
        assertTrue(4_000_000L > GenerationLimits.MAX_IMAGE_PIXELS);
    }

    @Test
    void generationLimitsExposeImageAndVoxBudgets() {
        assertEquals(1_048_576, GenerationLimits.MAX_IMAGE_PIXELS);
        assertEquals(64L * 1024 * 1024, GenerationLimits.MAX_IMAGE_FILE_BYTES);
        assertEquals(64L * 1024 * 1024, GenerationLimits.MAX_VOX_FILE_BYTES);
        assertEquals(262_144, GenerationLimits.MAX_IMPORTED_VOXELS);
    }

    @Test
    void readImageFailsWhenPolicyDeniesPath() throws Exception {
        Path file = tempDir.resolve("denied.png");
        writeSolidPng(file, 2, 2, 0xFF00FF00);
        ImportAccessPolicy.setCurrent((path, kind) -> false);

        ReadImageNode reader = new ReadImageNode();
        reader.setInput("input_path", file.toString());
        reader.processNode(null);

        assertEquals(Boolean.FALSE, reader.getOutput("output_valid"));
        String error = assertInstanceOf(String.class, reader.getOutput("output_error"));
        assertTrue(error.toLowerCase(Locale.ROOT).contains("allowlist"), error);
    }

    @Test
    void readImageFailsOnMissingFile() {
        ReadImageNode reader = new ReadImageNode();
        reader.setInput("input_path", tempDir.resolve("missing.png").toString());
        reader.processNode(null);
        assertEquals(Boolean.FALSE, reader.getOutput("output_valid"));
        String error = assertInstanceOf(String.class, reader.getOutput("output_error"));
        assertTrue(error.toLowerCase(Locale.ROOT).contains("exist"), error);
    }

    @Test
    void imageSamplerIsPureAndHasNoLegacyWidthHeightPorts() {
        ImageSamplerNode sampler = new ImageSamplerNode();
        NodeInfo info = sampler.getClass().getAnnotation(NodeInfo.class);
        assertNotNull(info);
        assertEquals(NodeEffect.PURE, info.effect());

        Set<String> portIds = new java.util.HashSet<>();
        for (IPort port : sampler.getInputPorts()) {
            portIds.add(port.getId().toLowerCase(Locale.ROOT));
        }
        assertFalse(portIds.contains("input_image_width"));
        assertFalse(portIds.contains("input_image_height"));
        assertFalse(portIds.contains("input_width"));
        assertFalse(portIds.contains("input_height"));
        assertFalse(portIds.contains("input_pixel_colors"));
        assertTrue(portIds.contains("input_image"));
    }

    @Test
    void imageSamplerRejectsNanUv() {
        ImageSamplerNode sampler = new ImageSamplerNode();
        List<ColorData> colors = List.of(ColorData.RED, ColorData.GREEN, ColorData.BLUE, ColorData.WHITE);
        ImageData image = ImageData.create(2, 2, 2, 2, 1, colors);
        sampler.setInput("input_image", image);
        sampler.setInput("input_u", Double.NaN);
        sampler.setInput("input_v", 0.5d);
        sampler.processNode(null);
        assertEquals(Boolean.FALSE, sampler.getOutput("output_valid"));
    }

    @Test
    void importVoxHasNoPlacementsOrStonePorts() {
        ImportVoxNode node = new ImportVoxNode();
        Set<String> ids = new java.util.HashSet<>();
        for (IPort port : node.getInputPorts()) {
            ids.add(port.getId().toLowerCase(Locale.ROOT));
        }
        for (IPort port : node.getOutputPorts()) {
            ids.add(port.getId().toLowerCase(Locale.ROOT));
        }
        assertFalse(ids.contains("output_placements"));
        assertFalse(ids.contains("input_block_type"));
        assertFalse(ids.contains("input_max_voxels"));
        assertFalse(ids.contains("input_allow_external_paths"));

        IPort colors = node.getOutputPorts().stream()
                .filter(p -> "output_colors".equals(p.getId()))
                .findFirst()
                .orElseThrow();
        IPort indices = node.getOutputPorts().stream()
                .filter(p -> "output_color_indices".equals(p.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(NodeDataType.COLOR_LIST, colors.getDataType());
        assertEquals(NodeDataType.INTEGER_LIST, indices.getDataType());
    }

    @Test
    void importVoxOriginConnectedNullFailsClosed() throws Exception {
        Path file = tempDir.resolve("origin.vox");
        Files.write(file, createLShapeVox());

        ImportVoxProbe probe = new ImportVoxProbe();
        probe.connectInput("input_origin", NodeDataType.BLOCK_POS);
        probe.setInput("input_path", file.toString());
        probe.setInput("input_origin", null);
        probe.processNode(null);

        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        String error = assertInstanceOf(String.class, probe.getOutput("output_error"));
        assertTrue(error.toLowerCase(Locale.ROOT).contains("origin"), error);
    }

    @Test
    void importVoxLShapeMapsZUpAxes() throws Exception {
        Path file = tempDir.resolve("lshape.vox");
        Files.write(file, createLShapeVox());

        ImportVoxProbe probe = new ImportVoxProbe();
        BlockPos origin = new BlockPos(10, 20, 30);
        probe.connectInput("input_origin", NodeDataType.BLOCK_POS);
        probe.setInput("input_path", file.toString());
        probe.setInput("input_origin", origin);
        probe.processNode(null);

        assertEquals(Boolean.TRUE, probe.getOutput("output_valid"));
        assertEquals(3, probe.getOutput("output_count"));

        BlockPosList blocks = assertInstanceOf(BlockPosList.class, probe.getOutput("output_blocks"));
        // VOX (0,0,0),(2,0,0),(0,0,1) with zUp → MC (ox,oy,oz),(ox+2,oy,oz),(ox,oy+1,oz)
        assertTrue(blocks.contains(new BlockPos(10, 20, 30)));
        assertTrue(blocks.contains(new BlockPos(12, 20, 30)));
        assertTrue(blocks.contains(new BlockPos(10, 21, 30)));

        List<?> colors = assertInstanceOf(List.class, probe.getOutput("output_colors"));
        List<?> indices = assertInstanceOf(List.class, probe.getOutput("output_color_indices"));
        assertEquals(3, colors.size());
        assertEquals(3, indices.size());
    }

    @Test
    void migrateV55ToV56DropsObsoletePortsAndState() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V55;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();

        SavedNode read = savedNode("r1", "utilities.fileio.read_image");
        Map<String, Object> readState = new HashMap<>();
        readState.put("allowExternalPaths", true);
        readState.put("maxPixels", 999);
        readState.put("readMode", "FULL");
        read.state = readState;

        SavedNode sampler = savedNode("s1", "utilities.fileio.image_sampler");
        SavedNode vox = savedNode("v1", "utilities.fileio.import_vox");
        Map<String, Object> voxState = new HashMap<>();
        voxState.put("maxVoxels", 100);
        voxState.put("defaultBlock", "minecraft:stone");
        voxState.put("defaultBlockType", "minecraft:stone");
        voxState.put("zUpToMinecraftY", true);
        vox.state = voxState;

        SavedNode sink = savedNode("n2", "reference.vectors.vector");
        graph.nodes.addAll(List.of(read, sampler, vox, sink));

        graph.connections.add(wire("r1", "output_valid", "n2", "input_x"));
        graph.connections.add(wire("n2", "output_value", "r1", "input_max_pixels"));
        graph.connections.add(wire("n2", "output_value", "r1", "input_allow_external_paths"));
        graph.connections.add(wire("n2", "output_value", "s1", "input_pixel_colors"));
        graph.connections.add(wire("n2", "output_value", "s1", "input_image_width"));
        graph.connections.add(wire("n2", "output_value", "s1", "input_width"));
        graph.connections.add(wire("v1", "output_placements", "n2", "input_y"));
        graph.connections.add(wire("n2", "output_value", "v1", "input_block_type"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        @SuppressWarnings("unchecked")
        Map<String, Object> readMigrated = (Map<String, Object>) nodeOf(migrated, "r1").state;
        assertEquals("FULL", readMigrated.get("readMode"));
        assertFalse(readMigrated.containsKey("allowExternalPaths"));
        assertFalse(readMigrated.containsKey("maxPixels"));

        @SuppressWarnings("unchecked")
        Map<String, Object> voxMigrated = (Map<String, Object>) nodeOf(migrated, "v1").state;
        assertEquals(Boolean.TRUE, voxMigrated.get("zUpToMinecraftY"));
        assertFalse(voxMigrated.containsKey("maxVoxels"));
        assertFalse(voxMigrated.containsKey("defaultBlock"));
        assertFalse(voxMigrated.containsKey("defaultBlockType"));

        assertEquals(1, migrated.connections.size());
        assertEquals("output_valid", migrated.connections.getFirst().sourcePortId);
    }

    private static void writeSolidPng(Path file, int width, int height, int argb) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, argb);
            }
        }
        ImageIO.write(img, "png", file.toFile());
    }

    /**
     * Minimal PNG whose IHDR claims {@code width}×{@code height} (no valid IDAT needed for metadata).
     */
    private static byte[] createPngWithIhdr(int width, int height) {
        byte[] signature = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        byte[] ihdrData = new byte[13];
        ihdrData[0] = (byte) ((width >>> 24) & 0xFF);
        ihdrData[1] = (byte) ((width >>> 16) & 0xFF);
        ihdrData[2] = (byte) ((width >>> 8) & 0xFF);
        ihdrData[3] = (byte) (width & 0xFF);
        ihdrData[4] = (byte) ((height >>> 24) & 0xFF);
        ihdrData[5] = (byte) ((height >>> 16) & 0xFF);
        ihdrData[6] = (byte) ((height >>> 8) & 0xFF);
        ihdrData[7] = (byte) (height & 0xFF);
        ihdrData[8] = 8;  // bit depth
        ihdrData[9] = 6;  // RGBA
        ihdrData[10] = 0;
        ihdrData[11] = 0;
        ihdrData[12] = 0;
        byte[] ihdr = pngChunk("IHDR", ihdrData);
        byte[] iend = pngChunk("IEND", new byte[0]);
        return concat(signature, ihdr, iend);
    }

    private static byte[] pngChunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream(12 + data.length);
        writeIntBe(out, data.length);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeIntBe(out, (int) crc.getValue());
        return out.toByteArray();
    }

    private static void writeIntBe(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static byte[] createLShapeVox() {
        // voxels at (0,0,0), (2,0,0), (0,0,1)
        byte[] size = voxChunk("SIZE", ints(3, 1, 2), new byte[0]);
        byte[] xyzi = voxChunk("XYZI", concat(
                ints(3),
                new byte[]{
                        0, 0, 0, 1,
                        2, 0, 0, 1,
                        0, 0, 1, 2
                }
        ), new byte[0]);
        byte[] rgba = voxChunk("RGBA", voxPalette(), new byte[0]);
        byte[] mainChildren = concat(size, xyzi, rgba);
        return concat("VOX ".getBytes(StandardCharsets.US_ASCII), ints(150),
                voxChunk("MAIN", new byte[0], mainChildren));
    }

    private static byte[] voxPalette() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 4);
        out.writeBytes(new byte[]{(byte) 255, 0, 0, (byte) 255});
        out.writeBytes(new byte[]{0, (byte) 255, 0, (byte) 255});
        for (int i = 2; i < 256; i++) {
            out.writeBytes(new byte[]{0, 0, 0, (byte) 255});
        }
        return out.toByteArray();
    }

    private static byte[] voxChunk(String id, byte[] content, byte[] children) {
        return concat(id.getBytes(StandardCharsets.US_ASCII), ints(content.length), ints(children.length), content, children);
    }

    private static byte[] ints(int... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(values.length * 4);
        for (int value : values) {
            out.write(value & 0xFF);
            out.write((value >>> 8) & 0xFF);
            out.write((value >>> 16) & 0xFF);
            out.write((value >>> 24) & 0xFF);
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String src, String srcPort, String dst, String dstPort) {
        SavedConnection c = new SavedConnection();
        c.sourceNodeId = src;
        c.sourcePortId = srcPort;
        c.targetNodeId = dst;
        c.targetPortId = dstPort;
        return c;
    }

    private static SavedNode nodeOf(SavedGraph graph, String nodeId) {
        return graph.nodes.stream()
                .filter(node -> nodeId.equals(node.nodeId))
                .findFirst()
                .orElseThrow();
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
    }

    private static final class ImportVoxProbe extends ImportVoxNode {
        void connectInput(String portId, NodeDataType outputType) {
            FileIoLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }
}
