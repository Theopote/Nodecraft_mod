package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.datatypes.ColorData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MagicaVoxelVoxReader {

    private static final String MAGIC = "VOX ";
    private static final int MIN_SUPPORTED_VERSION = 150;

    private MagicaVoxelVoxReader() {
    }

    public static VoxModel read(Path path, int maxVoxels) throws IOException {
        byte[] data = Files.readAllBytes(path);
        Parser parser = new Parser(data, Math.max(1, maxVoxels));
        return parser.parse();
    }

    public record VoxModel(int version, int sizeX, int sizeY, int sizeZ, List<Voxel> voxels, List<ColorData> palette) {
        public VoxModel {
            voxels = List.copyOf(voxels);
            palette = List.copyOf(palette);
        }
    }

    public record Voxel(int x, int y, int z, int colorIndex, ColorData color) {
    }

    private record Size(int x, int y, int z) {
    }

    private static final class Parser {
        private final byte[] data;
        private final int maxVoxels;
        private final List<RawVoxel> rawVoxels = new ArrayList<>();
        private ColorData[] palette = createFallbackPalette();
        private int offset;
        private int version;
        private Size currentSize = new Size(0, 0, 0);
        private int maxX;
        private int maxY;
        private int maxZ;

        private Parser(byte[] data, int maxVoxels) {
            this.data = data;
            this.maxVoxels = maxVoxels;
        }

        private VoxModel parse() {
            if (data.length < 8) {
                throw new IllegalArgumentException("VOX file is too small");
            }

            String magic = readId();
            if (!MAGIC.equals(magic)) {
                throw new IllegalArgumentException("Not a MagicaVoxel VOX file");
            }

            version = readIntLE();
            if (version < MIN_SUPPORTED_VERSION) {
                throw new IllegalArgumentException("Unsupported VOX version: " + version);
            }

            parseChunks(data.length);
            List<Voxel> voxels = rawVoxels.stream()
                    .map(voxel -> new Voxel(voxel.x(), voxel.y(), voxel.z(), voxel.colorIndex(), colorFor(voxel.colorIndex())))
                    .toList();
            return new VoxModel(version, Math.max(maxX, currentSize.x()), Math.max(maxY, currentSize.y()),
                    Math.max(maxZ, currentSize.z()), voxels, List.of(palette));
        }

        private void parseChunks(int endOffset) {
            while (offset < endOffset) {
                require(12);
                String chunkId = readId();
                int contentSize = readIntLE();
                int childrenSize = readIntLE();
                if (contentSize < 0 || childrenSize < 0) {
                    throw new IllegalArgumentException("Invalid VOX chunk size for " + chunkId);
                }

                int contentStart = offset;
                int contentEnd = checkedEnd(contentStart, contentSize, chunkId);
                int childrenEnd = checkedEnd(contentEnd, childrenSize, chunkId);

                switch (chunkId) {
                    case "SIZE" -> parseSize(contentEnd);
                    case "XYZI" -> parseVoxels(contentEnd);
                    case "RGBA" -> parsePalette(contentEnd);
                    default -> offset = contentEnd;
                }

                offset = contentEnd;
                if (childrenSize > 0) {
                    parseChunks(childrenEnd);
                }
                offset = childrenEnd;
            }
        }

        private void parseSize(int contentEnd) {
            requireUntil(contentEnd, 12, "SIZE");
            int x = readIntLE();
            int y = readIntLE();
            int z = readIntLE();
            currentSize = new Size(Math.max(0, x), Math.max(0, y), Math.max(0, z));
            maxX = Math.max(maxX, currentSize.x());
            maxY = Math.max(maxY, currentSize.y());
            maxZ = Math.max(maxZ, currentSize.z());
            offset = contentEnd;
        }

        private void parseVoxels(int contentEnd) {
            requireUntil(contentEnd, 4, "XYZI");
            int count = readIntLE();
            if (count < 0) {
                throw new IllegalArgumentException("Invalid VOX voxel count: " + count);
            }
            if ((long) rawVoxels.size() + count > maxVoxels) {
                throw new IllegalArgumentException("VOX voxel count exceeds max voxels " + maxVoxels);
            }
            requireUntil(contentEnd, count * 4, "XYZI");

            for (int i = 0; i < count; i++) {
                int x = readUnsignedByte();
                int y = readUnsignedByte();
                int z = readUnsignedByte();
                int colorIndex = readUnsignedByte();
                maxX = Math.max(maxX, x + 1);
                maxY = Math.max(maxY, y + 1);
                maxZ = Math.max(maxZ, z + 1);
                rawVoxels.add(new RawVoxel(x, y, z, colorIndex));
            }
            offset = contentEnd;
        }

        private void parsePalette(int contentEnd) {
            requireUntil(contentEnd, 256 * 4, "RGBA");
            ColorData[] parsed = new ColorData[256];
            for (int i = 0; i < parsed.length; i++) {
                int r = readUnsignedByte();
                int g = readUnsignedByte();
                int b = readUnsignedByte();
                int a = readUnsignedByte();
                parsed[i] = new ColorData(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
            }
            palette = parsed;
            offset = contentEnd;
        }

        private ColorData colorFor(int colorIndex) {
            int index = Math.max(0, Math.min(palette.length - 1, colorIndex - 1));
            return palette[index];
        }

        private String readId() {
            require(4);
            String id = new String(data, offset, 4, StandardCharsets.US_ASCII);
            offset += 4;
            return id;
        }

        private int readIntLE() {
            require(4);
            int value = (data[offset] & 0xFF)
                    | ((data[offset + 1] & 0xFF) << 8)
                    | ((data[offset + 2] & 0xFF) << 16)
                    | ((data[offset + 3] & 0xFF) << 24);
            offset += 4;
            return value;
        }

        private int readUnsignedByte() {
            require(1);
            return data[offset++] & 0xFF;
        }

        private int checkedEnd(int start, int size, String chunkId) {
            long end = (long) start + size;
            if (end > data.length) {
                throw new IllegalArgumentException("VOX chunk " + chunkId + " exceeds file size");
            }
            return (int) end;
        }

        private void require(int bytes) {
            if (offset + bytes > data.length) {
                throw new IllegalArgumentException("Unexpected end of VOX file");
            }
        }

        private void requireUntil(int endOffset, int bytes, String chunkId) {
            if (offset + bytes > endOffset) {
                throw new IllegalArgumentException("Malformed VOX " + chunkId + " chunk");
            }
        }

        private static ColorData[] createFallbackPalette() {
            ColorData[] colors = new ColorData[256];
            for (int i = 0; i < colors.length; i++) {
                float value = i / 255.0f;
                colors[i] = new ColorData(value, value, value, 1.0f);
            }
            return colors;
        }
    }

    private record RawVoxel(int x, int y, int z, int colorIndex) {
    }
}
