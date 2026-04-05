package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.TextLabelPreviewData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class TextLabelsElement extends AbstractPreviewElement {

    private final List<TextLabelPreviewData> labels = new ArrayList<>();
    private float fontSize = 0.025f;
    private boolean showBackground = true;
    private Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);

    public TextLabelsElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = -50;

        if (options.fontSize != null) {
            this.fontSize = Math.max(0.01f, options.fontSize);
        }
        if (options.showBackground != null) {
            this.showBackground = options.showBackground;
        }
        if (options.color != null) {
            this.color = new Vector3f(options.color);
        }
    }

    @Override
    protected void processData(Object data) {
        labels.clear();

        if (data instanceof TextLabelPreviewData label) {
            labels.add(label);
        } else if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof TextLabelPreviewData label) {
                    labels.add(label);
                }
            }
        }
    }

    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        if (labels.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        Vec3d cameraPos = camera.getCameraPos();
        int textColor = packColor(globalOpacity);
        int background = showBackground ? ((int) (Math.max(0.0f, Math.min(1.0f, opacity * globalOpacity)) * 255.0f) << 24) : 0;

        for (TextLabelPreviewData label : labels) {
            Vec3d pos = label.getPosition().subtract(cameraPos);
            matrices.push();
            matrices.translate(pos.x, pos.y, pos.z);
            matrices.multiply(camera.getRotation());
            matrices.scale(-fontSize, -fontSize, fontSize);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            float x = -textRenderer.getWidth(label.getText()) / 2.0f;
            textRenderer.draw(
                Text.literal(label.getText()).asOrderedText(),
                x,
                0.0f,
                textColor,
                false,
                matrix,
                immediate,
                TextRenderer.TextLayerType.SEE_THROUGH,
                background,
                LightmapTextureManager.MAX_LIGHT_COORDINATE
            );
            matrices.pop();
        }

        immediate.draw();
    }

    private int packColor(float globalOpacity) {
        int a = (int) (Math.max(0.0f, Math.min(1.0f, opacity * globalOpacity)) * 255.0f) & 0xFF;
        int r = (int) (Math.max(0.0f, Math.min(1.0f, color.x())) * 255.0f) & 0xFF;
        int g = (int) (Math.max(0.0f, Math.min(1.0f, color.y())) * 255.0f) & 0xFF;
        int b = (int) (Math.max(0.0f, Math.min(1.0f, color.z())) * 255.0f) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean shouldRender(Camera camera) {
        if (labels.isEmpty() || isExpired()) {
            return false;
        }
        float maxDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        Vec3d cameraPos = camera.getCameraPos();
        for (TextLabelPreviewData label : labels) {
            if (cameraPos.distanceTo(label.getPosition()) <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cleanup() {
        labels.clear();
    }
}
