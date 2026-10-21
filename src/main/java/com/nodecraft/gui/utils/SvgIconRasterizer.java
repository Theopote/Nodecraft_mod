package com.nodecraft.gui.utils;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Pure SVG → raster helper (no Minecraft / OpenGL).
 * <p>
 * Used at build time to emit PNG icon assets and at runtime only as a last-resort
 * fallback when a pre-rasterized PNG is missing.
 */
public final class SvgIconRasterizer {

    public static final int DEFAULT_SIZE = 64;

    private SvgIconRasterizer() {
    }

    public static BufferedImage rasterize(InputStream svgStream, int width, int height) throws IOException {
        Objects.requireNonNull(svgStream, "svgStream");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be positive");
        }

        SVGDocument document;
        try {
            document = new SVGLoader().load(svgStream, null, LoaderContext.createDefault());
        } catch (RuntimeException e) {
            throw new IOException("SVG parse failed: " + e.getMessage(), e);
        }
        if (document == null) {
            throw new IOException("SVG parse returned null document");
        }
        return renderDocument(document, width, height);
    }

    public static BufferedImage rasterizeFile(Path svgFile, int width, int height) throws IOException {
        try (InputStream in = Files.newInputStream(svgFile)) {
            return rasterize(in, width, height);
        }
    }

    public static void writePng(BufferedImage image, Path outputPng) throws IOException {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(outputPng, "outputPng");
        Path parent = outputPng.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(outputPng)) {
            if (!ImageIO.write(ensureArgb(image), "png", out)) {
                throw new IOException("ImageIO failed to encode PNG: " + outputPng);
            }
        }
    }

    public static BufferedImage ensureArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static BufferedImage renderDocument(SVGDocument document, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, width, height);
            graphics.setComposite(AlphaComposite.SrcOver);
            document.render((Component) null, graphics, new ViewBox(0, 0, width, height));
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
