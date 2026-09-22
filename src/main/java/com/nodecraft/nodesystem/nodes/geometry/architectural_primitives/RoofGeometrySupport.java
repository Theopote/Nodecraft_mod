package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;

/**
 * Shared roof construction for Roof Base (core types) and Roof Generator (advanced convenience).
 */
final class RoofGeometrySupport {

    private RoofGeometrySupport() {
    }

    record RoofLayout(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        String roofType,
        double height,
        double thickness,
        double overhang,
        String ridgeDirection,
        double eaveDrop
    ) {
    }

    record RoofResult(
        @Nullable GeometryData geometry,
        @Nullable PathData eavePath,
        @Nullable PathData ridgePath
    ) {
        static RoofResult invalid() {
            return new RoofResult(null, null, null);
        }
    }

    static RoofResult buildCoreRoof(RoofLayout layout) {
        if (layout == null || layout.frame() == null) {
            return RoofResult.invalid();
        }
        double roofWidth = layout.frame().width() + 2.0d * layout.overhang();
        double roofDepth = layout.frame().height() + 2.0d * layout.overhang();
        Vector3d eaveCenter = new Vector3d(layout.frame().center()).fma(-layout.eaveDrop(), layout.frame().zAxis());
        PathData eavePath = eaveLoop(layout.frame(), eaveCenter, roofWidth, roofDepth);

        GeometryData geometry = switch (layout.roofType()) {
            case "flat" -> new BoxGeometryData(
                new Vector3d(eaveCenter).fma(layout.thickness() / 2.0d, layout.frame().zAxis()),
                new Vector3d(roofWidth / 2.0d, layout.thickness() / 2.0d, roofDepth / 2.0d),
                ArchitecturalPrimitiveSupport.createOrientation(
                    layout.frame().xAxis(), layout.frame().yAxis(), layout.frame().zAxis()),
                true
            );
            case "shed" -> new PrismGeometryData(
                List.of(
                    new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, layout.frame().yAxis()),
                    new Vector3d(eaveCenter).fma(roofDepth / 2.0d, layout.frame().yAxis()),
                    new Vector3d(eaveCenter).fma(roofDepth / 2.0d, layout.frame().yAxis())
                        .fma(layout.height(), layout.frame().zAxis()),
                    new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, layout.frame().yAxis())
                        .fma(layout.height(), layout.frame().zAxis())
                ),
                new Vector3d(layout.frame().xAxis()).mul(roofWidth)
            );
            case "gable" -> buildGableRoof(
                layout.frame(), eaveCenter, roofWidth, roofDepth, layout.height(), layout.ridgeDirection());
            default -> null;
        };

        PathData ridgePath = "gable".equals(layout.roofType())
            ? gableRidgePath(layout.frame(), eaveCenter, roofWidth, roofDepth, layout.height(), layout.ridgeDirection())
            : null;

        return geometry == null ? RoofResult.invalid() : new RoofResult(geometry, eavePath, ridgePath);
    }

    static GeometryData buildGableRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection
    ) {
        if ("y".equals(ridgeDirection)) {
            return new PrismGeometryData(
                List.of(
                    new Vector3d(eaveCenter).fma(-roofWidth / 2.0d, frame.xAxis()),
                    new Vector3d(eaveCenter).fma(height, frame.zAxis()),
                    new Vector3d(eaveCenter).fma(roofWidth / 2.0d, frame.xAxis())
                ),
                new Vector3d(frame.yAxis()).mul(roofDepth)
            );
        }
        return new PrismGeometryData(
            List.of(
                new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis()),
                new Vector3d(eaveCenter).fma(height, frame.zAxis()),
                new Vector3d(eaveCenter).fma(roofDepth / 2.0d, frame.yAxis())
            ),
            new Vector3d(frame.xAxis()).mul(roofWidth)
        );
    }

    static PathData gableRidgePath(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection
    ) {
        Vector3d peak = new Vector3d(eaveCenter).fma(height, frame.zAxis());
        if ("y".equals(ridgeDirection)) {
            Vector3d a = new Vector3d(peak).fma(-roofDepth / 2.0d, frame.yAxis());
            Vector3d b = new Vector3d(peak).fma(roofDepth / 2.0d, frame.yAxis());
            return linePath(a, b);
        }
        Vector3d a = new Vector3d(peak).fma(-roofWidth / 2.0d, frame.xAxis());
        Vector3d b = new Vector3d(peak).fma(roofWidth / 2.0d, frame.xAxis());
        return linePath(a, b);
    }

    static PathData eaveLoop(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth
    ) {
        Vector3d hx = new Vector3d(frame.xAxis()).mul(roofWidth / 2.0d);
        Vector3d hy = new Vector3d(frame.yAxis()).mul(roofDepth / 2.0d);
        // Represent eave as the longer primary edge for path consumers; full loop can come later.
        Vector3d a = new Vector3d(eaveCenter).sub(hx).sub(hy);
        Vector3d b = new Vector3d(eaveCenter).add(hx).sub(hy);
        return linePath(a, b);
    }

    static String resolveCoreRoofType(Object value) {
        String type = resolveRoofType(value);
        return switch (type) {
            case "flat", "shed", "gable" -> type;
            default -> "gable";
        };
    }

    static String resolveRoofType(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        }
        return "gable";
    }

    static String resolveRidgeDirection(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("y")) {
                return "y";
            }
        }
        return "x";
    }

    private static PathData linePath(Vector3d a, Vector3d b) {
        return PathData.fromLine(new LineData(
            new Vec3d(a.x, a.y, a.z),
            new Vec3d(b.x, b.y, b.z)
        ));
    }
}
