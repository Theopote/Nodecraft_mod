package com.nodecraft.gui.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SvgIconRasterizerTest {

    private static final Path ICON_ROOT = Path.of("src/main/resources/assets/nodecraft/textures/icons/nodes");

    @TempDir
    Path tempDir;

    @Test
    void rasterizeSimpleSvgProducesArgbWithPixels() throws Exception {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32">
                  <rect width="32" height="32" fill="#EF4444"/>
                </svg>
                """;
        BufferedImage image = SvgIconRasterizer.rasterize(
                new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)),
                64,
                64
        );
        assertEquals(BufferedImage.TYPE_INT_ARGB, image.getType());
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        int center = image.getRGB(32, 32);
        int alpha = (center >> 24) & 0xFF;
        assertTrue(alpha > 0, "expected opaque center pixel");
    }

    @Test
    void writePngRoundTrip(@TempDir Path dir) throws Exception {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16">
                  <circle cx="8" cy="8" r="6" fill="#10B981"/>
                </svg>
                """;
        BufferedImage image = SvgIconRasterizer.rasterize(
                new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)),
                32,
                32
        );
        Path out = dir.resolve("icon.png");
        SvgIconRasterizer.writePng(image, out);
        assertTrue(Files.size(out) > 0);
    }

    @Test
    void allPackagedSvgsRasterizeOrReportFailures() throws Exception {
        if (!Files.isDirectory(ICON_ROOT)) {
            fail("Missing icon root: " + ICON_ROOT.toAbsolutePath());
        }

        List<String> failures = new ArrayList<>();
        int count = 0;
        try (Stream<Path> walk = Files.walk(ICON_ROOT)) {
            List<Path> svgs = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".svg"))
                    .toList();
            assertFalse(svgs.isEmpty(), "expected packaged SVG icons");

            for (Path svg : svgs) {
                count++;
                try {
                    BufferedImage image = SvgIconRasterizer.rasterizeFile(svg, 32, 32);
                    assertEquals(32, image.getWidth());
                    Path out = tempDir.resolve(ICON_ROOT.relativize(svg).toString().replace(".svg", ".png"));
                    SvgIconRasterizer.writePng(image, out);
                } catch (Exception e) {
                    failures.add(ICON_ROOT.relativize(svg).toString().replace('\\', '/') + " :: " + e.getMessage());
                }
            }
        }

        assertTrue(count >= 100, "expected a large icon library, got " + count);
        if (!failures.isEmpty()) {
            fail("SVG rasterize failures (" + failures.size() + "/" + count + "):\n - "
                    + String.join("\n - ", failures.subList(0, Math.min(20, failures.size()))));
        }
    }
}
