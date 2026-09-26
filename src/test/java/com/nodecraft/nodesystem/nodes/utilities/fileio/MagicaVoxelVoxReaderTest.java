package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.ImportAccessPolicy;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicaVoxelVoxReaderTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void allowImports() {
        ImportAccessPolicy.setCurrent((path, kind) -> true);
    }

    @AfterEach
    void resetPolicy() {
        ImportAccessPolicy.reset();
    }

    @Test
    void readsSizeVoxelsAndRgbaPalette() throws Exception {
        Path file = tempDir.resolve("sample.vox");
        Files.write(file, createVoxFile());

        MagicaVoxelVoxReader.VoxModel model = MagicaVoxelVoxReader.read(file, 16);

        assertEquals(150, model.version());
        assertEquals(3, model.sizeX());
        assertEquals(4, model.sizeY());
        assertEquals(5, model.sizeZ());
        assertEquals(2, model.voxels().size());
        assertEquals(1, model.voxels().getFirst().colorIndex());
        assertEquals(1.0f, model.voxels().getFirst().color().r(), 0.0001f);
        assertEquals(0.0f, model.voxels().getFirst().color().g(), 0.0001f);
    }

    @Test
    void rejectsVoxelCountsAboveLimit() throws Exception {
        Path file = tempDir.resolve("too_many.vox");
        Files.write(file, createVoxFile());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MagicaVoxelVoxReader.read(file, 1));

        assertTrue(error.getMessage().contains("exceeds max voxels"));
    }

    @Test
    void importNodeMapsVoxZToMinecraftY() throws Exception {
        Path file = tempDir.resolve("node.vox");
        Files.write(file, createVoxFile());

        ImportVoxNode node = new ImportVoxNode();
        PortStubNode originStub = new PortStubNode(NodeDataType.BLOCK_POS);
        originStub.setOutputValue(new BlockPos(10, 20, 30));
        BasePort originOut = (BasePort) originStub.getOutputPorts().getFirst();
        BasePort originIn = (BasePort) node.getInputPorts().stream()
                .filter(port -> "input_origin".equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(originOut.connectTo(originIn));

        node.setInput("input_path", file.toString());
        node.setInput("input_origin", new BlockPos(10, 20, 30));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(2, node.getOutput("output_count"));

        BlockPosList blocks = assertInstanceOf(BlockPosList.class, node.getOutput("output_blocks"));
        assertTrue(blocks.contains(new BlockPos(10, 20, 30)));
        assertTrue(blocks.contains(new BlockPos(12, 24, 33)));

        List<?> colors = assertInstanceOf(List.class, node.getOutput("output_colors"));
        List<?> indices = assertInstanceOf(List.class, node.getOutput("output_color_indices"));
        assertEquals(2, colors.size());
        assertEquals(2, indices.size());
        assertInstanceOf(ColorData.class, colors.getFirst());
        assertInstanceOf(Integer.class, indices.getFirst());

        assertFalse(node.getOutputPorts().stream().anyMatch(p -> "output_placements".equals(p.getId())));
    }

    private static byte[] createVoxFile() {
        byte[] size = chunk("SIZE", ints(3, 4, 5), new byte[0]);
        byte[] xyzi = chunk("XYZI", concat(
                ints(2),
                new byte[]{
                        0, 0, 0, 1,
                        2, 3, 4, 2
                }
        ), new byte[0]);
        byte[] rgba = chunk("RGBA", palette(), new byte[0]);
        byte[] mainChildren = concat(size, xyzi, rgba);

        return concat("VOX ".getBytes(java.nio.charset.StandardCharsets.US_ASCII), ints(150),
                chunk("MAIN", new byte[0], mainChildren));
    }

    private static byte[] palette() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 4);
        out.writeBytes(new byte[]{(byte) 255, 0, 0, (byte) 255});
        out.writeBytes(new byte[]{0, (byte) 255, 0, (byte) 255});
        for (int i = 2; i < 256; i++) {
            out.writeBytes(new byte[]{0, 0, 0, (byte) 255});
        }
        return out.toByteArray();
    }

    private static byte[] chunk(String id, byte[] content, byte[] children) {
        return concat(id.getBytes(java.nio.charset.StandardCharsets.US_ASCII), ints(content.length), ints(children.length), content, children);
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

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        void setOutputValue(Object value) {
            outputValues.put("output_stub", value);
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }
}
