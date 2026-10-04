package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import com.nodecraft.nodesystem.util.ProfileConstructionUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;

/**
 * Shared roof construction for Roof Base (core types) and Roof Generator (advanced convenience).
 * <p>
 * Prism-based roof builders return {@link RoofResult} so topology edges are derived from the same
 * profile key points and extrusion vector used for geometry.
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

    record SpecialtyRoofParams(
        String roofType,
        double height,
        String ridgeDirection,
        double ridgeRatio,
        double inset,
        double mPeakRatio,
        double valleyDrop,
        double asymmetricLeftHeightRatio,
        double asymmetricRightHeightRatio,
        double crossGableRatio,
        double secondaryHeightRatio,
        double crossGableOffset
    ) {
    }

    record RoofTopology(
        List<PathData> eaves,
        List<PathData> ridges,
        List<PathData> valleys,
        List<PlanarRegionData> faces,
        List<VectorData> slopes
    ) {
        RoofTopology {
            eaves = eaves == null ? List.of() : List.copyOf(eaves);
            ridges = ridges == null ? List.of() : List.copyOf(ridges);
            valleys = valleys == null ? List.of() : List.copyOf(valleys);
            faces = faces == null ? List.of() : List.copyOf(faces);
            slopes = slopes == null ? List.of() : List.copyOf(slopes);
        }

        RoofTopology(List<PathData> eaves, List<PathData> ridges, List<PathData> valleys) {
            this(eaves, ridges, valleys, List.of(), List.of());
        }

        RoofTopology withFaces(List<PlanarRegionData> faces, List<VectorData> slopes) {
            return new RoofTopology(eaves(), ridges(), valleys(), faces, slopes);
        }

        static RoofTopology empty() {
            return new RoofTopology(List.of(), List.of(), List.of(), List.of(), List.of());
        }

        @Nullable PathData primaryEave() {
            return longestPath(eaves);
        }

        @Nullable PathData primaryRidge() {
            return ridges.isEmpty() ? null : ridges.getFirst();
        }

        private static @Nullable PathData longestPath(List<PathData> paths) {
            PathData best = null;
            double bestLength = -1.0d;
            for (PathData path : paths) {
                double length = pathLength(path);
                if (length > bestLength) {
                    bestLength = length;
                    best = path;
                }
            }
            return best;
        }

        private static double pathLength(@Nullable PathData path) {
            if (path == null || path.getLine() == null) {
                return 0.0d;
            }
            LineData line = path.getLine();
            return line.start().distanceTo(line.end());
        }
    }

    record RoofResult(
        @Nullable GeometryData geometry,
        RoofTopology topology
    ) {
        static RoofResult invalid() {
            return new RoofResult(null, RoofTopology.empty());
        }

        @Nullable PathData eavePath() {
            return topology.primaryEave();
        }

        @Nullable PathData ridgePath() {
            return topology.primaryRidge();
        }
    }

    static RoofResult buildCoreRoof(RoofLayout layout) {
        if (layout == null || layout.frame() == null) {
            return RoofResult.invalid();
        }
        double roofWidth = layout.frame().width() + 2.0d * layout.overhang();
        double roofDepth = layout.frame().height() + 2.0d * layout.overhang();
        if (!ArchitecturalNodeOutputs.allFinite(
            roofWidth, roofDepth, layout.height(), layout.thickness(), layout.overhang(), layout.eaveDrop()
        )) {
            return RoofResult.invalid();
        }
        Vector3d eaveCenter = new Vector3d(layout.frame().center()).fma(-layout.eaveDrop(), layout.frame().zAxis());

        GeometryData geometry = switch (layout.roofType()) {
            case "flat" -> new BoxGeometryData(
                new Vector3d(eaveCenter).fma(layout.thickness() / 2.0d, layout.frame().zAxis()),
                new Vector3d(roofWidth / 2.0d, roofDepth / 2.0d, layout.thickness() / 2.0d),
                ArchitecturalPrimitiveSupport.createOrientation(
                    layout.frame().xAxis(), layout.frame().yAxis(), layout.frame().zAxis()),
                true
            );
            case "shed" -> buildShedRoof(
                layout.frame(), eaveCenter, roofWidth, roofDepth, layout.height(), layout.thickness());
            case "gable" -> buildGableRoof(
                layout.frame(), eaveCenter, roofWidth, roofDepth, layout.height(), layout.ridgeDirection());
            default -> null;
        };

        if (geometry == null) {
            return RoofResult.invalid();
        }

        RoofTopology topology = switch (layout.roofType()) {
            case "flat" -> perimeterTopology(layout.frame(), eaveCenter, roofWidth, roofDepth, List.of(), List.of());
            case "shed" -> shedTopology(
                layout.frame(), eaveCenter, roofWidth, roofDepth, layout.height());
            case "gable" -> gableTopology(
                layout.frame(), eaveCenter, roofWidth, roofDepth, layout.height(), layout.ridgeDirection());
            default -> RoofTopology.empty();
        };
        RoofFaceBundle faces = switch (layout.roofType()) {
            case "flat" -> flatFaces(layout.frame(), eaveCenter, roofWidth, roofDepth, layout.thickness());
            case "shed" -> shedFaces(layout.frame(), eaveCenter, roofWidth, roofDepth, layout.height());
            case "gable" -> gableFaces(
                layout.frame(), eaveCenter, roofWidth, roofDepth, layout.height(), layout.ridgeDirection());
            default -> RoofFaceBundle.empty();
        };
        if (faces == null) {
            return RoofResult.invalid();
        }
        return new RoofResult(geometry, topology.withFaces(faces.faces(), faces.slopes()));
    }

    static RoofResult buildSpecialtyRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        SpecialtyRoofParams params
    ) {
        if (frame == null || params == null) {
            return RoofResult.invalid();
        }
        if (!ArchitecturalNodeOutputs.allFinite(roofWidth, roofDepth, params.height())) {
            return RoofResult.invalid();
        }
        return switch (params.roofType()) {
            case "asymmetric_gable" -> buildAsymmetricGableRoof(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(), params.ridgeRatio(),
                params.asymmetricLeftHeightRatio(), params.asymmetricRightHeightRatio());
            case "hip" -> buildHipRoof(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(),
                params.ridgeRatio(), params.inset());
            case "cross_gable" -> buildCrossGableRoof(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(),
                params.crossGableRatio(), params.secondaryHeightRatio(), params.crossGableOffset());
            case "m" -> buildMRoof(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(),
                params.mPeakRatio(), params.valleyDrop());
            default -> RoofResult.invalid();
        };
    }

    private static final double ROOF_EPSILON = 1.0e-9d;

    static GeometryData buildShedRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        double thickness
    ) {
        Vector3d lowOuterTop = new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis());
        Vector3d highOuterTop = new Vector3d(eaveCenter)
            .fma(roofDepth / 2.0d, frame.yAxis())
            .fma(height, frame.zAxis());
        Vector3d extrusion = new Vector3d(frame.xAxis()).mul(roofWidth);

        if (thickness <= ROOF_EPSILON || thickness >= height - ROOF_EPSILON) {
            return new PrismGeometryData(
                List.of(
                    lowOuterTop,
                    highOuterTop,
                    new Vector3d(eaveCenter).fma(roofDepth / 2.0d, frame.yAxis())
                ),
                extrusion
            );
        }

        Vector3d alongSlope = new Vector3d(highOuterTop).sub(lowOuterTop);
        Vector3d slopeNormal = VectorUtils.safeCross(frame.xAxis(), alongSlope);
        if (slopeNormal == null || !VectorUtils.isNonZero(slopeNormal)) {
            slopeNormal = new Vector3d(frame.zAxis());
        } else {
            slopeNormal = VectorUtils.safeNormalize(slopeNormal);
            if (slopeNormal == null) {
                slopeNormal = new Vector3d(frame.zAxis());
            }
        }
        if (slopeNormal.dot(frame.zAxis()) < 0.0d) {
            slopeNormal.negate();
        }

        Vector3d lowInnerBottom = new Vector3d(lowOuterTop).fma(-thickness, frame.zAxis());
        Vector3d highInnerBottom = new Vector3d(highOuterTop).fma(-thickness, slopeNormal);
        return new PrismGeometryData(
            List.of(lowOuterTop, highOuterTop, highInnerBottom, lowInnerBottom),
            extrusion
        );
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

    private static RoofTopology shedTopology(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height
    ) {
        // Outer shed rim only: low eave, high eave, left/right rakes (no false footprint high edge).
        Vector3d hx = new Vector3d(frame.xAxis()).mul(roofWidth / 2.0d);
        Vector3d hy = new Vector3d(frame.yAxis()).mul(roofDepth / 2.0d);
        Vector3d highZ = new Vector3d(frame.zAxis()).mul(height);
        Vector3d lowLeft = new Vector3d(eaveCenter).sub(hx).sub(hy);
        Vector3d lowRight = new Vector3d(eaveCenter).add(hx).sub(hy);
        Vector3d highLeft = new Vector3d(eaveCenter).sub(hx).add(hy).add(highZ);
        Vector3d highRight = new Vector3d(eaveCenter).add(hx).add(hy).add(highZ);
        return new RoofTopology(
            List.of(
                linePath(lowLeft, lowRight),
                linePath(highLeft, highRight),
                linePath(lowLeft, highLeft),
                linePath(lowRight, highRight)
            ),
            List.of(),
            List.of()
        );
    }

    private static RoofTopology gableTopology(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection
    ) {
        PathData ridge = gableRidgePath(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection);
        return perimeterTopology(frame, eaveCenter, roofWidth, roofDepth, List.of(ridge), List.of());
    }

    private static RoofTopology perimeterTopology(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        List<PathData> ridges,
        List<PathData> valleys
    ) {
        return new RoofTopology(rectangleEaves(frame, eaveCenter, roofWidth, roofDepth), ridges, valleys);
    }

    private static List<PathData> rectangleEaves(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth
    ) {
        Vector3d hx = new Vector3d(frame.xAxis()).mul(roofWidth / 2.0d);
        Vector3d hy = new Vector3d(frame.yAxis()).mul(roofDepth / 2.0d);
        Vector3d c00 = new Vector3d(eaveCenter).sub(hx).sub(hy);
        Vector3d c10 = new Vector3d(eaveCenter).add(hx).sub(hy);
        Vector3d c11 = new Vector3d(eaveCenter).add(hx).add(hy);
        Vector3d c01 = new Vector3d(eaveCenter).sub(hx).add(hy);
        return List.of(linePath(c00, c10), linePath(c10, c11), linePath(c11, c01), linePath(c01, c00));
    }

    private static RoofResult buildHipRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double ridgeRatio,
        double inset
    ) {
        double insetX = Math.min(inset, roofWidth * 0.45d);
        double insetY = Math.min(inset, roofDepth * 0.45d);
        List<Vector3d> profile;
        List<PathData> eaves;
        if ("y".equals(ridgeDirection)) {
            double halfRidge = Math.max(roofDepth * ridgeRatio * 0.5d - insetY, roofDepth * 0.1d);
            profile = List.of(
                new Vector3d(eaveCenter).fma(-(roofWidth / 2.0d - insetX), frame.xAxis()),
                new Vector3d(eaveCenter).fma(-halfRidge, frame.yAxis()).fma(height, frame.zAxis()),
                new Vector3d(eaveCenter).fma(halfRidge, frame.yAxis()).fma(height, frame.zAxis()),
                new Vector3d(eaveCenter).fma(roofWidth / 2.0d - insetX, frame.xAxis())
            );
            Vector3d hx = new Vector3d(frame.xAxis()).mul(roofWidth / 2.0d - insetX);
            Vector3d hy = new Vector3d(frame.yAxis()).mul(roofDepth / 2.0d - insetY);
            Vector3d c00 = new Vector3d(eaveCenter).sub(hx).sub(hy);
            Vector3d c10 = new Vector3d(eaveCenter).add(hx).sub(hy);
            Vector3d c11 = new Vector3d(eaveCenter).add(hx).add(hy);
            Vector3d c01 = new Vector3d(eaveCenter).sub(hx).add(hy);
            eaves = List.of(linePath(c00, c10), linePath(c10, c11), linePath(c11, c01), linePath(c01, c00));
        } else {
            double halfRidge = Math.max(roofWidth * ridgeRatio * 0.5d - insetX, roofWidth * 0.1d);
            profile = List.of(
                new Vector3d(eaveCenter).fma(-(roofDepth / 2.0d - insetY), frame.yAxis()),
                new Vector3d(eaveCenter).fma(-halfRidge, frame.xAxis()).fma(height, frame.zAxis()),
                new Vector3d(eaveCenter).fma(halfRidge, frame.xAxis()).fma(height, frame.zAxis()),
                new Vector3d(eaveCenter).fma(roofDepth / 2.0d - insetY, frame.yAxis())
            );
            Vector3d hx = new Vector3d(frame.xAxis()).mul(roofWidth / 2.0d - insetX);
            Vector3d hy = new Vector3d(frame.yAxis()).mul(roofDepth / 2.0d - insetY);
            Vector3d c00 = new Vector3d(eaveCenter).sub(hx).sub(hy);
            Vector3d c10 = new Vector3d(eaveCenter).add(hx).sub(hy);
            Vector3d c11 = new Vector3d(eaveCenter).add(hx).add(hy);
            Vector3d c01 = new Vector3d(eaveCenter).sub(hx).add(hy);
            eaves = List.of(linePath(c00, c10), linePath(c10, c11), linePath(c11, c01), linePath(c01, c00));
        }

        Vector3d extrusion = "y".equals(ridgeDirection)
            ? new Vector3d(frame.yAxis()).mul(roofDepth - 2.0d * insetY)
            : new Vector3d(frame.xAxis()).mul(roofWidth - 2.0d * insetX);
        GeometryData geometry = new PrismGeometryData(profile, extrusion);
        PathData ridge = linePath(profile.get(1), profile.get(2));
        return new RoofResult(geometry, new RoofTopology(eaves, List.of(ridge), List.of()));
    }

    private static RoofResult buildAsymmetricGableRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double ridgeRatio,
        double leftHeightRatio,
        double rightHeightRatio
    ) {
        double ridgeHalfWidth = Math.max(Math.min(roofWidth, roofDepth) * 0.04d, 0.05d);
        double leftHeight = height * leftHeightRatio;
        double rightHeight = height * rightHeightRatio;
        List<Vector3d> profile;
        Vector3d extrusion;
        Vector3d leftPeak;
        Vector3d rightPeak;
        if ("y".equals(ridgeDirection)) {
            double ridgeCenter = (-roofWidth / 2.0d) + roofWidth * ridgeRatio;
            double leftPeakCoord = Math.max(-roofWidth / 2.0d + ridgeHalfWidth, ridgeCenter - ridgeHalfWidth);
            double rightPeakCoord = Math.min(roofWidth / 2.0d - ridgeHalfWidth, ridgeCenter + ridgeHalfWidth);
            leftPeak = new Vector3d(eaveCenter).fma(leftPeakCoord, frame.xAxis()).fma(leftHeight, frame.zAxis());
            rightPeak = new Vector3d(eaveCenter).fma(rightPeakCoord, frame.xAxis()).fma(rightHeight, frame.zAxis());
            profile = List.of(
                new Vector3d(eaveCenter).fma(-roofWidth / 2.0d, frame.xAxis()),
                leftPeak,
                rightPeak,
                new Vector3d(eaveCenter).fma(roofWidth / 2.0d, frame.xAxis())
            );
            extrusion = new Vector3d(frame.yAxis()).mul(roofDepth);
        } else {
            double ridgeCenter = (-roofDepth / 2.0d) + roofDepth * ridgeRatio;
            double leftPeakCoord = Math.max(-roofDepth / 2.0d + ridgeHalfWidth, ridgeCenter - ridgeHalfWidth);
            double rightPeakCoord = Math.min(roofDepth / 2.0d - ridgeHalfWidth, ridgeCenter + ridgeHalfWidth);
            leftPeak = new Vector3d(eaveCenter).fma(leftPeakCoord, frame.yAxis()).fma(leftHeight, frame.zAxis());
            rightPeak = new Vector3d(eaveCenter).fma(rightPeakCoord, frame.yAxis()).fma(rightHeight, frame.zAxis());
            profile = List.of(
                new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis()),
                leftPeak,
                rightPeak,
                new Vector3d(eaveCenter).fma(roofDepth / 2.0d, frame.yAxis())
            );
            extrusion = new Vector3d(frame.xAxis()).mul(roofWidth);
        }

        GeometryData geometry = new PrismGeometryData(profile, extrusion);
        List<PathData> ridges = List.of(
            extrusionEdge(leftPeak, extrusion),
            extrusionEdge(rightPeak, extrusion)
        );
        RoofTopology topology = perimeterTopology(frame, eaveCenter, roofWidth, roofDepth, ridges, List.of());
        return new RoofResult(geometry, topology);
    }

    private static RoofResult buildCrossGableRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double crossGableRatio,
        double secondaryHeightRatio,
        double crossGableOffset
    ) {
        GeometryData primary = buildGableRoof(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection);
        String secondaryDirection = "y".equals(ridgeDirection) ? "x" : "y";
        Vector3d secondaryCenter = new Vector3d(eaveCenter);
        if ("y".equals(ridgeDirection)) {
            secondaryCenter.fma(crossGableOffset * roofDepth * 0.5d, frame.yAxis());
        } else {
            secondaryCenter.fma(crossGableOffset * roofWidth * 0.5d, frame.xAxis());
        }
        double secondaryWidth = roofWidth * crossGableRatio;
        double secondaryDepth = roofDepth * crossGableRatio;
        double secondaryHeight = height * secondaryHeightRatio;
        GeometryData secondary = buildGableRoof(
            frame,
            secondaryCenter,
            secondaryWidth,
            secondaryDepth,
            secondaryHeight,
            secondaryDirection
        );
        GeometryData geometry = GeometryOutputUtils.packGeometry(List.of(primary, secondary));

        PathData primaryRidge = gableRidgePath(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection);
        PathData secondaryRidge = gableRidgePath(
            frame,
            secondaryCenter,
            secondaryWidth,
            secondaryDepth,
            secondaryHeight,
            secondaryDirection
        );
        PathData valley = crossGableValleyLine(
            frame,
            eaveCenter,
            roofWidth,
            roofDepth,
            height,
            ridgeDirection,
            secondaryCenter,
            secondaryWidth,
            secondaryDepth,
            secondaryHeight,
            secondaryDirection
        );
        RoofTopology topology = perimeterTopology(
            frame, eaveCenter, roofWidth, roofDepth,
            List.of(primaryRidge, secondaryRidge),
            valley == null ? List.of() : List.of(valley)
        );
        return new RoofResult(geometry, topology);
    }

    /**
     * Valley line where the primary roof half facing the secondary gable meets the secondary roof half
     * facing the primary footprint center. Derived from the same plane geometry used by each gable prism.
     */
    private static @Nullable PathData crossGableValleyLine(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double primaryHeight,
        String primaryRidgeDirection,
        Vector3d secondaryCenter,
        double secondaryWidth,
        double secondaryDepth,
        double secondaryHeight,
        String secondaryRidgeDirection
    ) {
        Vector3d hx = new Vector3d(frame.xAxis());
        Vector3d hy = new Vector3d(frame.yAxis());
        Vector3d hz = new Vector3d(frame.zAxis());

        Plane primaryPlane;
        Plane secondaryPlane;
        if ("y".equals(primaryRidgeDirection) && "x".equals(secondaryRidgeDirection)) {
            Vector3d towardSecondary = new Vector3d(secondaryCenter).sub(eaveCenter);
            boolean secondaryNorth = towardSecondary.dot(hy) >= 0.0d;
            Vector3d primaryRidgeNorth = new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, hy).fma(primaryHeight, hz);
            Vector3d primaryRidgeSouth = new Vector3d(eaveCenter).fma(roofDepth / 2.0d, hy).fma(primaryHeight, hz);
            Vector3d primaryEave = secondaryNorth
                ? new Vector3d(eaveCenter).fma(roofWidth / 2.0d, hx).fma(roofDepth / 2.0d, hy)
                : new Vector3d(eaveCenter).fma(roofWidth / 2.0d, hx).fma(-roofDepth / 2.0d, hy);
            primaryPlane = Plane.fromPoints(primaryRidgeNorth, primaryRidgeSouth, primaryEave);
            if (primaryPlane == null) {
                return null;
            }

            Vector3d secondaryRidgeWest = new Vector3d(secondaryCenter)
                .fma(-secondaryWidth / 2.0d, hx).fma(secondaryHeight, hz);
            Vector3d secondaryRidgeEast = new Vector3d(secondaryCenter)
                .fma(secondaryWidth / 2.0d, hx).fma(secondaryHeight, hz);
            Vector3d secondaryEave = secondaryNorth
                ? new Vector3d(secondaryCenter).fma(-secondaryWidth / 2.0d, hx).fma(secondaryDepth / 2.0d, hy)
                : new Vector3d(secondaryCenter).fma(-secondaryWidth / 2.0d, hx).fma(-secondaryDepth / 2.0d, hy);
            secondaryPlane = Plane.fromPoints(secondaryRidgeWest, secondaryRidgeEast, secondaryEave);
        } else if ("x".equals(primaryRidgeDirection) && "y".equals(secondaryRidgeDirection)) {
            Vector3d towardSecondary = new Vector3d(secondaryCenter).sub(eaveCenter);
            boolean secondaryEast = towardSecondary.dot(hx) >= 0.0d;
            Vector3d primaryRidgeWest = new Vector3d(eaveCenter).fma(-roofWidth / 2.0d, hx).fma(primaryHeight, hz);
            Vector3d primaryRidgeEast = new Vector3d(eaveCenter).fma(roofWidth / 2.0d, hx).fma(primaryHeight, hz);
            Vector3d primaryEave = secondaryEast
                ? new Vector3d(eaveCenter).fma(roofWidth / 2.0d, hx).fma(roofDepth / 2.0d, hy)
                : new Vector3d(eaveCenter).fma(-roofWidth / 2.0d, hx).fma(roofDepth / 2.0d, hy);
            primaryPlane = Plane.fromPoints(primaryRidgeWest, primaryRidgeEast, primaryEave);
            if (primaryPlane == null) {
                return null;
            }

            Vector3d secondaryRidgeNorth = new Vector3d(secondaryCenter)
                .fma(-secondaryDepth / 2.0d, hy).fma(secondaryHeight, hz);
            Vector3d secondaryRidgeSouth = new Vector3d(secondaryCenter)
                .fma(secondaryDepth / 2.0d, hy).fma(secondaryHeight, hz);
            Vector3d secondaryEave = secondaryEast
                ? new Vector3d(secondaryCenter).fma(secondaryDepth / 2.0d, hy).fma(secondaryWidth / 2.0d, hx)
                : new Vector3d(secondaryCenter).fma(secondaryDepth / 2.0d, hy).fma(-secondaryWidth / 2.0d, hx);
            secondaryPlane = Plane.fromPoints(secondaryRidgeNorth, secondaryRidgeSouth, secondaryEave);
        } else {
            return null;
        }
        if (secondaryPlane == null) {
            return null;
        }

        Vector3d direction = VectorUtils.safeCross(primaryPlane.normal(), secondaryPlane.normal());
        if (direction == null || !VectorUtils.isNonZero(direction)) {
            return null;
        }
        direction = VectorUtils.safeNormalize(direction);
        if (direction == null) {
            return null;
        }

        Vector3d pointOnLine = primaryPlane.intersectionPointWith(secondaryPlane, direction);
        if (pointOnLine == null) {
            return null;
        }

        double tEave = intersectParamAtEaveHeight(frame, eaveCenter, pointOnLine, direction);
        double tRidge = intersectParamAtHeight(frame, eaveCenter, pointOnLine, direction, Math.max(primaryHeight, secondaryHeight));
        if (!Double.isFinite(tEave) || !Double.isFinite(tRidge)) {
            return null;
        }
        if (Math.abs(tEave - tRidge) <= 1.0e-9d) {
            return null;
        }
        Vector3d start = new Vector3d(pointOnLine).fma(tEave, direction);
        Vector3d end = new Vector3d(pointOnLine).fma(tRidge, direction);
        if (start.distanceSquared(end) <= 1.0e-12d) {
            return null;
        }
        return linePath(start, end);
    }

    private static double intersectParamAtEaveHeight(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        Vector3d pointOnLine,
        Vector3d direction
    ) {
        return intersectParamAtHeight(frame, eaveCenter, pointOnLine, direction, 0.0d);
    }

    private static double intersectParamAtHeight(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        Vector3d pointOnLine,
        Vector3d direction,
        double heightAboveEave
    ) {
        Vector3d z = frame.zAxis();
        double target = heightAboveEave + new Vector3d(pointOnLine).sub(eaveCenter).dot(z);
        double denom = direction.dot(z);
        if (Math.abs(denom) <= 1.0e-12d) {
            return Double.NaN;
        }
        double current = new Vector3d(pointOnLine).sub(eaveCenter).dot(z);
        return (target - current) / denom;
    }

    private static RoofResult buildMRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double mPeakRatio,
        double valleyDrop
    ) {
        double valleyHeight = Math.max(0.0d, height - valleyDrop);
        List<Vector3d> profile;
        Vector3d extrusion;
        Vector3d leftPeak;
        Vector3d rightPeak;
        Vector3d valleyPoint;
        if ("y".equals(ridgeDirection)) {
            double ridgeOffset = roofWidth * mPeakRatio;
            leftPeak = new Vector3d(eaveCenter).fma(-ridgeOffset, frame.xAxis()).fma(height, frame.zAxis());
            valleyPoint = new Vector3d(eaveCenter).fma(valleyHeight, frame.zAxis());
            rightPeak = new Vector3d(eaveCenter).fma(ridgeOffset, frame.xAxis()).fma(height, frame.zAxis());
            profile = List.of(
                new Vector3d(eaveCenter).fma(-roofWidth / 2.0d, frame.xAxis()),
                leftPeak,
                valleyPoint,
                rightPeak,
                new Vector3d(eaveCenter).fma(roofWidth / 2.0d, frame.xAxis())
            );
            extrusion = new Vector3d(frame.yAxis()).mul(roofDepth);
        } else {
            double ridgeOffset = roofDepth * mPeakRatio;
            leftPeak = new Vector3d(eaveCenter).fma(-ridgeOffset, frame.yAxis()).fma(height, frame.zAxis());
            valleyPoint = new Vector3d(eaveCenter).fma(valleyHeight, frame.zAxis());
            rightPeak = new Vector3d(eaveCenter).fma(ridgeOffset, frame.yAxis()).fma(height, frame.zAxis());
            profile = List.of(
                new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis()),
                leftPeak,
                valleyPoint,
                rightPeak,
                new Vector3d(eaveCenter).fma(roofDepth / 2.0d, frame.yAxis())
            );
            extrusion = new Vector3d(frame.xAxis()).mul(roofWidth);
        }

        GeometryData geometry = new PrismGeometryData(profile, extrusion);
        List<PathData> ridges = List.of(
            extrusionEdge(leftPeak, extrusion),
            extrusionEdge(rightPeak, extrusion)
        );
        PathData valley = extrusionEdge(valleyPoint, extrusion);
        RoofTopology topology = perimeterTopology(frame, eaveCenter, roofWidth, roofDepth, ridges, List.of(valley));
        return new RoofResult(geometry, topology);
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
            Vector3d extrusion = new Vector3d(frame.yAxis()).mul(roofDepth);
            return extrusionEdge(peak, extrusion);
        }
        Vector3d extrusion = new Vector3d(frame.xAxis()).mul(roofWidth);
        return extrusionEdge(peak, extrusion);
    }

    private static PathData extrusionEdge(Vector3d profilePoint, Vector3d extrusionVector) {
        Vector3d end = new Vector3d(profilePoint).add(extrusionVector);
        return linePath(profilePoint, end);
    }

    /** @deprecated use {@link RoofTopology#primaryEave()} via {@link RoofResult#topology()} */
    static PathData eaveLoop(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth
    ) {
        return perimeterTopology(frame, eaveCenter, roofWidth, roofDepth, List.of(), List.of()).primaryEave();
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

    private record RoofFaceBundle(List<PlanarRegionData> faces, List<VectorData> slopes) {
        static RoofFaceBundle empty() {
            return new RoofFaceBundle(List.of(), List.of());
        }
    }

    private static @Nullable RoofFaceBundle flatFaces(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double thickness
    ) {
        Vector3d hx = new Vector3d(frame.xAxis()).mul(roofWidth / 2.0d);
        Vector3d hy = new Vector3d(frame.yAxis()).mul(roofDepth / 2.0d);
        Vector3d top = new Vector3d(eaveCenter).fma(thickness, frame.zAxis());
        Vector3d c00 = new Vector3d(top).sub(hx).sub(hy);
        Vector3d c10 = new Vector3d(top).add(hx).sub(hy);
        Vector3d c11 = new Vector3d(top).add(hx).add(hy);
        Vector3d c01 = new Vector3d(top).sub(hx).add(hy);
        PlanarRegionData face = tryQuadRegion(c00, c10, c11, c01);
        VectorData slope = VectorData.canonical(new Vector3d());
        if (face == null || slope == null) {
            return null;
        }
        return new RoofFaceBundle(List.of(face), List.of(slope));
    }

    private static @Nullable RoofFaceBundle shedFaces(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height
    ) {
        Vector3d low = new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis());
        Vector3d high = new Vector3d(eaveCenter)
            .fma(roofDepth / 2.0d, frame.yAxis())
            .fma(height, frame.zAxis());
        Vector3d extrusion = new Vector3d(frame.xAxis()).mul(roofWidth);
        Vector3d lowEnd = new Vector3d(low).add(extrusion);
        Vector3d highEnd = new Vector3d(high).add(extrusion);
        PlanarRegionData face = tryQuadRegion(low, high, highEnd, lowEnd);
        VectorData slope = slopeToward(high, low);
        if (face == null || slope == null) {
            return null;
        }
        return new RoofFaceBundle(List.of(face), List.of(slope));
    }

    private static @Nullable RoofFaceBundle gableFaces(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection
    ) {
        Vector3d peak = new Vector3d(eaveCenter).fma(height, frame.zAxis());
        if ("y".equals(ridgeDirection)) {
            Vector3d left = new Vector3d(eaveCenter).fma(-roofWidth / 2.0d, frame.xAxis());
            Vector3d right = new Vector3d(eaveCenter).fma(roofWidth / 2.0d, frame.xAxis());
            Vector3d extrusion = new Vector3d(frame.yAxis()).mul(roofDepth);
            return twoSlopeFaces(left, peak, right, extrusion);
        }
        Vector3d low = new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis());
        Vector3d high = new Vector3d(eaveCenter).fma(roofDepth / 2.0d, frame.yAxis());
        Vector3d extrusion = new Vector3d(frame.xAxis()).mul(roofWidth);
        return twoSlopeFaces(low, peak, high, extrusion);
    }

    private static @Nullable RoofFaceBundle twoSlopeFaces(
        Vector3d left,
        Vector3d peak,
        Vector3d right,
        Vector3d extrusion
    ) {
        Vector3d leftEnd = new Vector3d(left).add(extrusion);
        Vector3d peakEnd = new Vector3d(peak).add(extrusion);
        Vector3d rightEnd = new Vector3d(right).add(extrusion);
        PlanarRegionData leftFace = tryQuadRegion(left, peak, peakEnd, leftEnd);
        PlanarRegionData rightFace = tryQuadRegion(peak, right, rightEnd, peakEnd);
        VectorData leftSlope = slopeToward(peak, left);
        VectorData rightSlope = slopeToward(peak, right);
        if (leftFace == null || rightFace == null || leftSlope == null || rightSlope == null) {
            return null;
        }
        return new RoofFaceBundle(List.of(leftFace, rightFace), List.of(leftSlope, rightSlope));
    }

    private static @Nullable VectorData slopeToward(Vector3d from, Vector3d to) {
        Vector3d direction = VectorUtils.safeSubtract(to, from);
        Vector3d unit = VectorUtils.safeNormalize(direction);
        if (unit == null) {
            return VectorData.canonical(new Vector3d());
        }
        return VectorData.canonical(unit);
    }

    private static @Nullable PlanarRegionData tryQuadRegion(Vector3d a, Vector3d b, Vector3d c, Vector3d d) {
        PlanarRegionData region = tryClosedLoop(List.of(a, b, c, d, new Vector3d(a)));
        if (region != null) {
            return region;
        }
        return tryClosedLoop(List.of(a, d, c, b, new Vector3d(a)));
    }

    private static @Nullable PlanarRegionData tryClosedLoop(List<Vector3d> points) {
        if (points.size() < 4) {
            return null;
        }
        Vector3d ab = VectorUtils.safeSubtract(points.get(1), points.get(0));
        Vector3d ac = VectorUtils.safeSubtract(points.get(2), points.get(0));
        Vector3d normal = VectorUtils.safeNormalize(VectorUtils.safeCross(ab, ac));
        if (normal == null) {
            return null;
        }
        PlaneData plane = PlaneData.canonical(points.get(0), normal);
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(points, plane, null);
        if (profile == null) {
            return null;
        }
        return PlanarRegionData.tryCreate(profile, List.of(), plane, null);
    }

    private record Plane(Vector3d normal, double distance) {
        private static @Nullable Plane fromPoints(Vector3d a, Vector3d b, Vector3d c) {
            Vector3d ab = new Vector3d(b).sub(a);
            Vector3d ac = new Vector3d(c).sub(a);
            Vector3d normal = VectorUtils.safeCross(ab, ac);
            if (normal == null || !VectorUtils.isNonZero(normal)) {
                return null;
            }
            normal = VectorUtils.safeNormalize(normal);
            if (normal == null) {
                return null;
            }
            double distance = normal.dot(a);
            return new Plane(normal, distance);
        }

        private @Nullable Vector3d intersectionPointWith(Plane other, Vector3d directionHint) {
            Vector3d direction = VectorUtils.safeCross(normal, other.normal);
            if (direction == null || !VectorUtils.isNonZero(direction)) {
                return null;
            }
            direction = VectorUtils.safeNormalize(direction);
            if (direction == null) {
                return null;
            }
            Vector3d n1xN2 = direction;
            Vector3d n2xN1 = VectorUtils.safeCross(other.normal, normal);
            if (n2xN1 == null) {
                return null;
            }
            Vector3d point = new Vector3d(n1xN2).mul(distance);
            point.fma(other.distance, n2xN1);
            double denom = n1xN2.dot(n2xN1);
            if (Math.abs(denom) <= 1.0e-18d) {
                return null;
            }
            point.mul(1.0d / denom);
            return point;
        }
    }
}
