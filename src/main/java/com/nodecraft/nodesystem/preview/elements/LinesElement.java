package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewCurveSmoothing;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.protocol.PreviewCurvePayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewPoint;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class LinesElement extends AbstractPreviewElement {

    /** Samples generated per polyline segment when smoothing is enabled. */
    private static final int SMOOTH_SAMPLES_PER_SEGMENT = 8;
    /** Centripetal Catmull-Rom parameterization exponent. */
    private static final double SMOOTH_ALPHA = 0.5d;
    private static final double SMOOTH_EPSILON = 1.0e-8d;

    private volatile List<Vec3d> points = new ArrayList<>();
    /** Original control points, kept so the smooth toggle can be reversed without a re-execute. */
    private volatile List<Vec3d> sourcePoints = new ArrayList<>();
    private Vector3f color = new Vector3f(1.0f, 0.85f, 0.2f);
    private float lineWidth = 1.5f;
    private boolean showDirection = false;
    private float arrowSize = 0.25f;
    /** When true, polyline corners are rounded with a Catmull-Rom spline. */
    private boolean smoothCurves = false;
    /** True when the source polyline is closed (affects smoothing wrap-around). */
    private volatile boolean closedPath = false;

    public LinesElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 17;

        PreviewOptions opts = options != null ? options : new PreviewOptions();
        if (opts.color != null) {
            this.color = new Vector3f(opts.color);
        }
        if (opts.lineWidth != null) {
            this.lineWidth = Math.max(0.25f, opts.lineWidth);
        }
        if (opts.showArrows != null) {
            this.showDirection = opts.showArrows;
        }
        if (opts.arrowSize != null) {
            this.arrowSize = Math.max(0.05f, opts.arrowSize);
        }
        if (opts.smoothCurves != null) {
            this.smoothCurves = opts.smoothCurves;
        }
    }

    @Override
    protected void processData(Object data) {
        if (data instanceof PreviewCurvePayload curve) {
            List<Vec3d> nextPoints = new ArrayList<>(curve.getPoints().size());
            for (PreviewPoint p : curve.getPoints()) {
                nextPoints.add(new Vec3d(p.x(), p.y(), p.z()));
            }
            boolean closed = curve.closed();
            closedPath = closed;
            sourcePoints = nextPoints;
            points = buildRenderPoints(nextPoints, closed);
            return;
        }

        List<Vec3d> nextPoints = new ArrayList<>();
        boolean closed = false;

        if (data instanceof LineData line) {
            nextPoints.add(line.start());
            nextPoints.add(line.end());
        } else if (data instanceof PolylineData polyline) {
            nextPoints.addAll(polyline.points());
            closed = polyline.isClosed();
        } else if (data instanceof Curve curve) {
            // Already sampled into a dense polyline — smoothing it again would distort it.
            nextPoints.addAll(curve.getSamplePoints());
        } else if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Vec3d vec) {
                    nextPoints.add(vec);
                }
            }
        }

        closedPath = closed;
        sourcePoints = nextPoints;
        points = buildRenderPoints(nextPoints, closed);
    }

    /**
     * Applies the "Smooth Curves" option: polyline corners are replaced by a centripetal
     * Catmull-Rom spline so the preview reads as a continuous curve instead of hard corners.
     * Linear mode keeps the original control points untouched.
     */
    private List<Vec3d> buildRenderPoints(List<Vec3d> controlPoints, boolean closed) {
        return PreviewCurveSmoothing.buildRenderPoints(
            controlPoints,
            closed,
            smoothCurves,
            SMOOTH_SAMPLES_PER_SEGMENT,
            SMOOTH_ALPHA,
            SMOOTH_EPSILON
        );
    }

    @Override
    public void updateOptions(PreviewOptions newOptions) {
        boolean previousSmooth = smoothCurves;
        super.updateOptions(newOptions);
        if (newOptions != null) {
            if (newOptions.color != null) {
                this.color = new Vector3f(newOptions.color);
            }
            if (newOptions.lineWidth != null) {
                this.lineWidth = Math.max(0.25f, newOptions.lineWidth);
            }
            if (newOptions.showArrows != null) {
                this.showDirection = newOptions.showArrows;
            }
            if (newOptions.arrowSize != null) {
                this.arrowSize = Math.max(0.05f, newOptions.arrowSize);
            }
            if (newOptions.smoothCurves != null) {
                this.smoothCurves = newOptions.smoothCurves;
            }
        }
        // Toggling "Smooth Curves" must re-shape the existing geometry immediately.
        if (previousSmooth != smoothCurves) {
            rebuildFromSource();
        }
    }

    /**
     * Re-applies smoothing to the last source geometry. The original control points are kept
     * so the toggle is reversible without waiting for the node to re-execute.
     */
    private void rebuildFromSource() {
        List<Vec3d> source = sourcePoints;
        if (source == null || source.size() < 2) {
            return;
        }
        points = buildRenderPoints(source, closedPath);
    }

    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        List<Vec3d> pointsSnapshot = points;
        if (pointsSnapshot.size() < 2) {
            return;
        }

        float finalOpacity = opacity * globalOpacity;
        if (finalOpacity <= 0.01f) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider provider = PreviewRenderer.getInstance().getActiveVertexConsumers();
        VertexConsumerProvider.Immediate immediate = null;
        boolean flushImmediately = false;
        if (provider == null) {
            immediate = client.getBufferBuilders().getEntityVertexConsumers();
            provider = immediate;
            flushImmediately = true;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cameraPos = camera.getCameraPos();

        for (int i = 0; i < pointsSnapshot.size() - 1; i++) {
            Vec3d start = pointsSnapshot.get(i).subtract(cameraPos);
            Vec3d end = pointsSnapshot.get(i + 1).subtract(cameraPos);
            VertexConsumer segmentConsumer = provider.getBuffer(RenderLayers.LINES);
            drawLine(segmentConsumer, matrix, start, end, finalOpacity);
        }

        if (showDirection) {
            // Arrows follow the original control polyline, not the subdivided spline, so
            // smoothing does not multiply the arrow count.
            List<Vec3d> arrowPoints = sourcePoints != null && sourcePoints.size() >= 2
                ? sourcePoints
                : pointsSnapshot;
            for (int i = 0; i < arrowPoints.size() - 1; i++) {
                Vec3d start = arrowPoints.get(i).subtract(cameraPos);
                Vec3d end = arrowPoints.get(i + 1).subtract(cameraPos);
                VertexConsumer segmentConsumer = provider.getBuffer(RenderLayers.LINES);
                drawArrowHead(segmentConsumer, matrix, start, end, finalOpacity);
            }
        }

        if (flushImmediately && immediate != null) {
            immediate.draw();
        }
    }

    private void drawArrowHead(VertexConsumer vertexConsumer, Matrix4f matrix, Vec3d start, Vec3d end, float alpha) {
        Vec3d direction = end.subtract(start);
        double length = direction.length();
        if (length < 1.0e-6d) {
            return;
        }

        double scaledArrowSize = Math.max(0.05d, arrowSize * Math.max(0.5d, lineWidth * 0.6d));
        Vec3d dir = direction.normalize();
        Vec3d reference = Math.abs(dir.y) < 0.95d ? new Vec3d(0.0d, 1.0d, 0.0d) : new Vec3d(1.0d, 0.0d, 0.0d);
        Vec3d side = dir.crossProduct(reference).normalize().multiply(scaledArrowSize);
        Vec3d back = dir.multiply(-scaledArrowSize * 1.6d);

        drawLine(vertexConsumer, matrix, end, end.add(back).add(side), alpha);
        drawLine(vertexConsumer, matrix, end, end.add(back).subtract(side), alpha);
    }

    private void drawLine(VertexConsumer vertexConsumer, Matrix4f matrix, Vec3d start, Vec3d end, float alpha) {
        Vector3f normal = new Vector3f(0.0f, 1.0f, 0.0f);

        vertexConsumer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
            .color(color.x(), color.y(), color.z(), alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
        vertexConsumer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
            .color(color.x(), color.y(), color.z(), alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
    }

    @Override
    public boolean shouldRender(Camera camera) {
        List<Vec3d> pointsSnapshot = points;
        if (pointsSnapshot.size() < 2 || isExpired()) {
            return false;
        }
        float maxDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        Vec3d cameraPos = camera.getCameraPos();
        for (Vec3d point : pointsSnapshot) {
            if (cameraPos.distanceTo(point) <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cleanup() {
        points = new ArrayList<>();
    }
}
