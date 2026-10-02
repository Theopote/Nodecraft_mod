package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
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

    protected enum LayoutMode {
        DISTRIBUTE,
        FIXED_GAP,
        BAY;

        static @Nullable LayoutMode fromString(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return DISTRIBUTE;
            }
            return switch (value.trim().toLowerCase()) {
                case "distribute" -> DISTRIBUTE;
                case "fixed_gap", "fixed-gap", "fixedgap" -> FIXED_GAP;
                case "bay" -> BAY;
                default -> null;
            };
        }
    }

    protected record LayoutSpacingOptions(
        LayoutMode mode,
        double horizontalGap,
        double verticalGap,
        double bayWidth
    ) {
        public LayoutSpacingOptions {
            mode = mode == null ? LayoutMode.DISTRIBUTE : mode;
        }

        public static LayoutSpacingOptions distribute() {
            return new LayoutSpacingOptions(LayoutMode.DISTRIBUTE, 0.0d, 0.0d, 0.0d);
        }
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
        return resolveFaceArrayLayout(face, columns, rows, elementWidth, elementHeight, margin, verticalAnchor,
            LayoutSpacingOptions.distribute());
    }

    protected @Nullable FaceArrayLayout resolveFaceArrayLayout(
        BoxFaceData face,
        int columns,
        int rows,
        double elementWidth,
        double elementHeight,
        double margin,
        VerticalAnchor verticalAnchor,
        LayoutSpacingOptions spacingOptions
    ) {
        ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
        if (frame == null) {
            return null;
        }
        return resolveFaceArrayLayout(frame, columns, rows, elementWidth, elementHeight, margin, verticalAnchor, spacingOptions);
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
        return resolveFaceArrayLayout(frame, columns, rows, elementWidth, elementHeight, margin, verticalAnchor,
            LayoutSpacingOptions.distribute());
    }

    protected @Nullable FaceArrayLayout resolveFaceArrayLayout(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        int columns,
        int rows,
        double elementWidth,
        double elementHeight,
        double margin,
        VerticalAnchor verticalAnchor,
        LayoutSpacingOptions spacingOptions
    ) {
        double availableWidth = frame.width() - 2.0d * margin;
        double availableHeight = frame.height() - 2.0d * margin;
        if (availableWidth < elementWidth || availableHeight < elementHeight) {
            return null;
        }

        if (!GeometryOutputUtils.fitsArchitecturalInstanceBudget(columns, rows)) {
            return null;
        }

        LayoutSpacingOptions options = spacingOptions == null ? LayoutSpacingOptions.distribute() : spacingOptions;
        // BAY applies Bay Width only on the horizontal axis; vertical uses FIXED_GAP (if
        // verticalGap > 0) or DISTRIBUTE so floor rhythm is not driven by bay width.
        LayoutMode horizontalMode = options.mode();
        LayoutMode verticalMode = options.mode() == LayoutMode.BAY
            ? (options.verticalGap() > EPSILON ? LayoutMode.FIXED_GAP : LayoutMode.DISTRIBUTE)
            : options.mode();
        double spacingX = resolveAxisSpacing(
            columns, elementWidth, availableWidth, horizontalMode, options.horizontalGap(), options.bayWidth());
        double spacingY = resolveAxisSpacing(
            rows, elementHeight, availableHeight, verticalMode, options.verticalGap(), options.bayWidth());
        if (spacingX == Double.NEGATIVE_INFINITY || spacingY == Double.NEGATIVE_INFINITY) {
            return null;
        }

        double startX = -frame.width() / 2.0d + margin + elementWidth / 2.0d;
        double startY = switch (verticalAnchor) {
            case TOP -> frame.height() / 2.0d - margin - elementHeight / 2.0d;
            case BOTTOM -> -frame.height() / 2.0d + margin + elementHeight / 2.0d;
        };
        return new FaceArrayLayout(frame, columns, rows, elementWidth, elementHeight, spacingX, spacingY, startX, startY, verticalAnchor);
    }

    private static double resolveAxisSpacing(
        int count,
        double elementSize,
        double availableSize,
        LayoutMode mode,
        double gapOrBay,
        double bayWidthForBayMode
    ) {
        if (count <= 1) {
            return 0.0d;
        }
        return switch (mode) {
            case DISTRIBUTE -> {
                double spacing = (availableSize - count * elementSize) / (count - 1);
                yield spacing < -EPSILON ? Double.NEGATIVE_INFINITY : spacing;
            }
            case FIXED_GAP -> {
                if (gapOrBay < -EPSILON) {
                    yield Double.NEGATIVE_INFINITY;
                }
                double required = count * elementSize + (count - 1) * gapOrBay;
                yield required > availableSize + EPSILON ? Double.NEGATIVE_INFINITY : gapOrBay;
            }
            case BAY -> {
                if (bayWidthForBayMode <= elementSize + EPSILON) {
                    yield Double.NEGATIVE_INFINITY;
                }
                double required = elementSize + (count - 1) * bayWidthForBayMode;
                yield required > availableSize + EPSILON ? Double.NEGATIVE_INFINITY : bayWidthForBayMode - elementSize;
            }
        };
    }

    protected List<FaceArrayPlacement> enumeratePlacements(FaceArrayLayout layout) {
        int capacity = GeometryOutputUtils.architecturalInstanceCount(layout.columns(), layout.rows());
        if (capacity < 0) {
            return List.of();
        }
        List<FaceArrayPlacement> placements = new ArrayList<>(capacity);
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

    protected @Nullable <T extends GeometryData> List<T> buildFaceArray(
        FaceArrayLayout layout,
        FaceArrayGeometryFactory<T> factory
    ) {
        int capacity = GeometryOutputUtils.architecturalInstanceCount(layout.columns(), layout.rows());
        if (capacity < 0) {
            return null;
        }
        List<T> results = new ArrayList<>(capacity);
        for (FaceArrayPlacement placement : enumeratePlacements(layout)) {
            T geometry = factory.create(placement);
            if (geometry == null) {
                return null;
            }
            results.add(geometry);
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
        int capacity = GeometryOutputUtils.architecturalInstanceCount(layout.columns(), layout.rows());
        if (capacity < 0) {
            return List.of();
        }
        List<FrameData> frames = new ArrayList<>(capacity);
        for (FaceArrayPlacement placement : enumeratePlacements(layout)) {
            frames.add(placementFrame(placement));
        }
        return List.copyOf(frames);
    }

    protected List<PointData> buildCenters(FaceArrayLayout layout) {
        int capacity = GeometryOutputUtils.architecturalInstanceCount(layout.columns(), layout.rows());
        if (capacity < 0) {
            return List.of();
        }
        List<PointData> centers = new ArrayList<>(capacity);
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
