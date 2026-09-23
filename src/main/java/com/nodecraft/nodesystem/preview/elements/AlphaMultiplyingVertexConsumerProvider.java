package com.nodecraft.nodesystem.preview.elements;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

/**
 * Multiplies vertex alpha so ghost block models can share the preview opacity slider.
 */
final class AlphaMultiplyingVertexConsumerProvider implements VertexConsumerProvider {

    private final VertexConsumerProvider delegate;
    private final float alphaScale;

    AlphaMultiplyingVertexConsumerProvider(VertexConsumerProvider delegate, float alphaScale) {
        this.delegate = delegate;
        this.alphaScale = Math.max(0.0f, Math.min(1.0f, alphaScale));
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return new AlphaMultiplyingVertexConsumer(delegate.getBuffer(layer), alphaScale);
    }

    private static final class AlphaMultiplyingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alphaScale;

        private AlphaMultiplyingVertexConsumer(VertexConsumer delegate, float alphaScale) {
            this.delegate = delegate;
            this.alphaScale = alphaScale;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * alphaScale)));
            delegate.color(red, green, blue, scaledAlpha);
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            return color(r, g, b, a);
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            delegate.lineWidth(width);
            return this;
        }
    }
}
