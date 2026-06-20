package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicaVoxelVoxReaderTest {

    @TempDir
    Path tempDir;

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
        Map<String, Object> outputs = node.compute(Map.of(
                "input_path", file.toString(),
                "input_allow_external_paths", true,
                "input_block_type", "minecraft:gold_block",
                "input_origin", new BlockPos(10, 20, 30)
        ));

        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(2, outputs.get("output_count"));

        BlockPosList blocks = assertInstanceOf(BlockPosList.class, outputs.get("output_blocks"));
        assertTrue(blocks.contains(new BlockPos(10, 20, 30)));
        assertTrue(blocks.contains(new BlockPos(12, 24, 33)));

        List<?> placements = assertInstanceOf(List.class, outputs.get("output_placements"));
        assertFalse(placements.isEmpty());
        BlockPlacementData placement = assertInstanceOf(BlockPlacementData.class, placements.getFirst());
        assertEquals("minecraft:gold_block", placement.blockId());
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
}
