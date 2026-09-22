package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

abstract class AbstractFaceArrayNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    protected AbstractFaceArrayNode(UUID id, String nodeType) {
        super(id, nodeType);
    }

    protected @Nullable FaceArrayLayout resolveFaceArrayLayout(
        BoxFaceData face,
        int columns,
        int rows,
        double elementWidth,
        double elementHeight,
        double margin,
        VerticalAnchor verticalAnchor
    ) {
        ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
        if (frame == null) {
            return null;
        }
        return resolveFaceArrayLayout(frame, columns, rows, elementWidth, elementHeight, margin, verticalAnchor);
    }

    protected @Nullable FaceArrayLayout resolveFaceArrayLayout(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        int columns,
        int rows,
        double elementWidth,
        double elementHeight,
        double margin,
        VerticalAnchor verticalAnchor
    ) {
        double availableWidth = frame.width() - 2.0d * margin;
        double availableHeight = frame.height() - 2.0d * margin;
        if (availableWidth < elementWidth || availableHeight < elementHeight) {
            return null;
        }

        double spacingX = columns > 1 ? (availableWidth - columns * elementWidth) / (columns - 1) : 0.0d;
        double spacingY = rows > 1 ? (availableHeight - rows * elementHeight) / (rows - 1) : 0.0d;
        if (spacingX < -EPSILON || spacingY < -EPSILON) {
            return null;
        }

        double startX = -frame.width() / 2.0d + margin + elementWidth / 2.0d;
        double startY = switch (verticalAnchor) {
            case TOP -> frame.height() / 2.0d - margin - elementHeight / 2.0d;
            case BOTTOM -> -frame.height() / 2.0d + margin + elementHeight / 2.0d;
        };
        return new FaceArrayLayout(frame, columns, rows, elementWidth, elementHeight, spacingX, spacingY, startX, startY, verticalAnchor);
    }

    protected List<FaceArrayPlacement> enumeratePlacements(FaceArrayLayout layout) {
        List<FaceArrayPlacement> placements = new ArrayList<>(layout.columns() * layout.rows());
        for (int row = 0; row < layout.rows(); row++) {
            double offsetY = layout.verticalAnchor() == VerticalAnchor.TOP
                ? layout.startY() - row * (layout.elementHeight() + layout.spacingY())
                : layout.startY() + row * (layout.elementHeight() + layout.spacingY());
            for (int column = 0; column < layout.columns(); column++) {
                double offsetX = layout.startX() + column * (layout.elementWidth() + layout.spacingX());
                placements.add(new FaceArrayPlacement(layout, row, column, offsetX, offsetY));
            }
        }
        return List.copyOf(placements);
    }

    protected <T extends GeometryData> List<T> buildFaceArray(FaceArrayLayout layout, FaceArrayGeometryFactory<T> factory) {
        List<T> results = new ArrayList<>(layout.columns() * layout.rows());
        for (FaceArrayPlacement placement : enumeratePlacements(layout)) {
            T geometry = factory.create(placement);
            if (geometry != null) {
                results.add(geometry);
            }
        }
        return List.copyOf(results);
    }

    /**
     * Face-aligned placement frame: origin on face, X/Y in face plane, Z = face normal.
     */
    protected FrameData placementFrame(FaceArrayPlacement placement) {
        ArchitecturalPrimitiveSupport.FaceFrame frame = placement.layout().frame();
        return new FrameData(
            placement.centerOnFace(),
            frame.xAxis(),
            frame.yAxis(),
            frame.zAxis()
        );
    }

    protected List<FrameData> buildPlacementFrames(FaceArrayLayout layout) {
        List<FrameData> frames = new ArrayList<>(layout.columns() * layout.rows());
        for (FaceArrayPlacement placement : enumeratePlacements(layout)) {
            frames.add(placementFrame(placement));
        }
        return List.copyOf(frames);
    }

    protected List<PointData> buildCenters(FaceArrayLayout layout) {
        List<PointData> centers = new ArrayList<>(layout.columns() * layout.rows());
        for (FaceArrayPlacement placement : enumeratePlacements(layout)) {
            centers.add(new PointData(placement.centerOnFace()));
        }
        return List.copyOf(centers);
    }

    @FunctionalInterface
    protected interface FaceArrayGeometryFactory<T extends GeometryData> {
        @Nullable T create(FaceArrayPlacement placement);
    }

    protected enum VerticalAnchor {
        TOP,
        BOTTOM
    }

    protected record FaceArrayLayout(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        int columns,
        int rows,
        double elementWidth,
        double elementHeight,
        double spacingX,
        double spacingY,
        double startX,
        double startY,
        VerticalAnchor verticalAnchor
    ) {
    }

    protected record FaceArrayPlacement(
        FaceArrayLayout layout,
        int row,
        int column,
        double offsetX,
        double offsetY
    ) {
        Vector3d centerOnFace() {
            return new Vector3d(layout.frame().center())
                .fma(offsetX, layout.frame().xAxis())
                .fma(offsetY, layout.frame().yAxis());
        }
    }
}
