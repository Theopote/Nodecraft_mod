package com.nodecraft.tools;

import com.nodecraft.gui.utils.SvgIconRasterizer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Build-time CLI: rasterize every node-library SVG under {@code svgRoot} to PNG under {@code pngRoot}.
 * <p>
 * Args: {@code <svgRoot> <pngRoot> [size]}
 */
public final class RasterizeNodeIconsMain {

    private RasterizeNodeIconsMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: RasterizeNodeIconsMain <svgRoot> <pngRoot> [size]");
            System.exit(2);
        }

        Path svgRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path pngRoot = Path.of(args[1]).toAbsolutePath().normalize();
        int size = args.length >= 3 ? Integer.parseInt(args[2]) : SvgIconRasterizer.DEFAULT_SIZE;

        if (!Files.isDirectory(svgRoot)) {
            throw new IllegalArgumentException("SVG root is not a directory: " + svgRoot);
        }

        Files.createDirectories(pngRoot);

        List<String> failures = new ArrayList<>();
        AtomicInteger converted = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        long started = System.nanoTime();

        Files.walkFileTree(svgRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".svg")) {
                    return FileVisitResult.CONTINUE;
                }

                Path relative = svgRoot.relativize(file);
                String pngRelative = relative.toString().replace('\\', '/');
                if (pngRelative.toLowerCase(Locale.ROOT).endsWith(".svg")) {
                    pngRelative = pngRelative.substring(0, pngRelative.length() - 4) + ".png";
                }
                Path out = pngRoot.resolve(pngRelative);

                try {
                    if (Files.exists(out)
                            && Files.getLastModifiedTime(out).compareTo(Files.getLastModifiedTime(file)) >= 0
                            && Files.size(out) > 0) {
                        skipped.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }
                    BufferedImage image = SvgIconRasterizer.rasterizeFile(file, size, size);
                    SvgIconRasterizer.writePng(image, out);
                    converted.incrementAndGet();
                } catch (IOException | RuntimeException e) {
                    failures.add(relative.toString().replace('\\', '/') + " :: " + e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });

        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        System.out.printf(
                Locale.ROOT,
                "Node icon rasterize: converted=%d skipped=%d failed=%d size=%d took=%.2fs%n",
                converted.get(),
                skipped.get(),
                failures.size(),
                size,
                seconds
        );

        if (!failures.isEmpty()) {
            System.err.println("Failed SVG resources:");
            for (String failure : failures) {
                System.err.println("  - " + failure);
            }
            System.exit(1);
        }
    }
}
