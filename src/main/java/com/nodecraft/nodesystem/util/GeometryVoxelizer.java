package com.nodecraft.nodesystem.util;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared geometry-to-voxel bridge for nodes that consume abstract geometry.
 */
public final class GeometryVoxelizer {

    /** Hard cap for geometry voxelization bounding volume (blocks). */
    public static final long MAX_SDF_VOXEL_VOLUME = 262_144L;

    private GeometryVoxelizer() {
    }

    public static BlockPosList resolveBlocks(@Nullable Object blocksObj,
                                             @Nullable Object geometryObj,
                                             @Nullable Object boxGeometryObj,
                                             @Nullable Object cylinderGeometryObj,
                                             @Nullable Object sphereGeometryObj,
                                             @Nullable Object torusGeometryObj,
                                             boolean fillSolid) {
        if (blocksObj instanceof BlockPosList blockPosList) {
            return blockPosList;
        }

        GeometryData geometry = resolveGeometry(geometryObj, boxGeometryObj, cylinderGeometryObj, sphereGeometryObj, torusGeometryObj);
        if (geometry != null) {
            return voxelize(geometry, fillSolid);
        }

        return new BlockPosList();
    }

    public static @Nullable GeometryData resolveGeometry(@Nullable Object geometryObj,
                                                         @Nullable Object boxGeometryObj,
                                                         @Nullable Object cylinderGeometryObj,
                                                         @Nullable Object sphereGeometryObj,
                                                         @Nullable Object torusGeometryObj) {
        if (geometryObj instanceof GeometryData geometry) {
            return geometry;
        }

        if (boxGeometryObj instanceof GeometryData geometry) {
            return geometry;
        }

        if (cylinderGeometryObj instanceof GeometryData geometry) {
            return geometry;
        }

        if (sphereGeometryObj instanceof GeometryData geometry) {
            return geometry;
        }

        if (torusGeometryObj instanceof GeometryData geometry) {
            return geometry;
        }

        return null;
    }

    public static BlockPosList voxelize(GeometryData geometry, boolean fillSolid) {
        if (geometry instanceof CompositeGeometryData compositeGeometry) {
            return voxelizeComposite(compositeGeometry, fillSolid);
        }
        if (geometry instanceof DifferenceGeometryData differenceGeometry) {
            return voxelizeDifference(differenceGeometry, fillSolid);
        }
        if (geometry instanceof IntersectionGeometryData intersectionGeometry) {
            return voxelizeIntersection(intersectionGeometry, fillSolid);
        }
        if (boundingVolumeExceedsLimit(geometry)) {
            return new BlockPosList();
        }
        if (geometry instanceof BoxGeometryData boxGeometry) {
            return voxelizeBox(boxGeometry, fillSolid);
        }
        if (geometry instanceof ConeGeometryData coneGeometry) {
            return voxelizeCone(coneGeometry, fillSolid);
        }
        if (geometry instanceof FrustumConeGeometryData frustumGeometry) {
            return voxelizeFrustumCone(frustumGeometry, fillSolid);
        }
        if (geometry instanceof CylinderGeometryData cylinderGeometry) {
            return voxelizeCylinder(cylinderGeometry, fillSolid);
        }
        if (geometry instanceof EllipsoidGeometryData ellipsoidGeometry) {
            return voxelizeEllipsoid(ellipsoidGeometry, fillSolid);
        }
        if (geometry instanceof HemisphereGeometryData hemisphereGeometry) {
            return voxelizeHemisphere(hemisphereGeometry, fillSolid);
        }
        if (geometry instanceof OctahedronGeometryData octahedronGeometry) {
            return voxelizeOctahedron(octahedronGeometry, fillSolid);
        }
        if (geometry instanceof IcosahedronGeometryData icosahedronGeometry) {
            return voxelizeIcosahedron(icosahedronGeometry, fillSolid);
        }
        if (geometry instanceof DodecahedronGeometryData dodecahedronGeometry) {
            return voxelizeDodecahedron(dodecahedronGeometry, fillSolid);
        }
        if (geometry instanceof PrismGeometryData prismGeometry) {
            return voxelizePrism(prismGeometry, fillSolid);
        }
        if (geometry instanceof SquarePyramidGeometryData squarePyramidGeometry) {
            return voxelizeSquarePyramid(squarePyramidGeometry, fillSolid);
        }
        if (geometry instanceof SphereData sphereGeometry) {
            return voxelizeSphere(sphereGeometry, fillSolid);
        }
        if (geometry instanceof SdfGeometryData sdfGeometry) {
            return voxelizeSdfGeometry(sdfGeometry, fillSolid);
        }
        if (geometry instanceof TetrahedronGeometryData tetrahedronGeometry) {
            return voxelizeTetrahedron(tetrahedronGeometry, fillSolid);
        }
        if (geometry instanceof TorusGeometryData torusGeometry) {
            return voxelizeTorus(torusGeometry, fillSolid);
        }
        return new BlockPosList();
    }

    public static @Nullable RegionData createBoundingRegion(GeometryData geometry) {
        if (geometry instanceof CompositeGeometryData compositeGeometry) {
            return createCompositeBoundingRegion(compositeGeometry);
        }
        if (geometry instanceof DifferenceGeometryData differenceGeometry) {
            return unionBoundingRegions(
                createBoundingRegion(differenceGeometry.getMinuend()),
                createBoundingRegion(differenceGeometry.getSubtrahend())
            );
        }
        if (geometry instanceof IntersectionGeometryData intersectionGeometry) {
            return createIntersectionBoundingRegion(intersectionGeometry);
        }
        if (geometry instanceof BoxGeometryData boxGeometry) {
            return boxGeometry.isOriented()
                ? BoxBlockGenerator.createOrientedBoundingRegion(
                    boxGeometry.getCenter(),
                    boxGeometry.getHalfExtents(),
                    boxGeometry.getOrientationMatrix()
                )
                : createAxisAlignedRegion(boxGeometry);
        }
        if (geometry instanceof ConeGeometryData coneGeometry) {
            return ConeBlockGenerator.createBoundingRegion(coneGeometry);
        }
        if (geometry instanceof FrustumConeGeometryData frustumGeometry) {
            return FrustumConeBlockGenerator.createBoundingRegion(frustumGeometry);
        }
        if (geometry instanceof CylinderGeometryData cylinderGeometry) {
            return CylinderBlockGenerator.createBoundingRegion(cylinderGeometry);
        }
        if (geometry instanceof EllipsoidGeometryData ellipsoidGeometry) {
            return EllipsoidBlockGenerator.createBoundingRegion(ellipsoidGeometry);
        }
        if (geometry instanceof HemisphereGeometryData hemisphereGeometry) {
            return HemisphereBlockGenerator.createBoundingRegion(hemisphereGeometry);
        }
        if (geometry instanceof OctahedronGeometryData octahedronGeometry) {
            return OctahedronBlockGenerator.createBoundingRegion(octahedronGeometry);
        }
        if (geometry instanceof IcosahedronGeometryData icosahedronGeometry) {
            return IcosahedronBlockGenerator.createBoundingRegion(icosahedronGeometry);
        }
        if (geometry instanceof DodecahedronGeometryData dodecahedronGeometry) {
            return DodecahedronBlockGenerator.createBoundingRegion(dodecahedronGeometry);
        }
        if (geometry instanceof PrismGeometryData prismGeometry) {
            return createPrismBoundingRegion(prismGeometry);
        }
        if (geometry instanceof SquarePyramidGeometryData squarePyramidGeometry) {
            return createSquarePyramidBoundingRegion(squarePyramidGeometry);
        }
        if (geometry instanceof SphereData sphereGeometry) {
            return SphereBlockGenerator.createBoundingRegion(sphereGeometry);
        }
        if (geometry instanceof SdfGeometryData sdfGeometry) {
            return new RegionData(
                BlockPos.ofFloored(sdfGeometry.getMin().x, sdfGeometry.getMin().y, sdfGeometry.getMin().z),
                BlockPos.ofFloored(sdfGeometry.getMax().x, sdfGeometry.getMax().y, sdfGeometry.getMax().z)
            );
        }
        if (geometry instanceof TetrahedronGeometryData tetrahedronGeometry) {
            return TetrahedronBlockGenerator.createBoundingRegion(tetrahedronGeometry);
        }
        if (geometry instanceof TorusGeometryData torusGeometry) {
            return TorusBlockGenerator.createBoundingRegion(torusGeometry);
        }
        return null;
    }

    public static @Nullable BoundingBoxData createBoundingBox(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return null;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return null;
        }

        return new BoundingBoxData(
            new Vector3d(minCorner.getX(), minCorner.getY(), minCorner.getZ()),
            new Vector3d(maxCorner.getX() + 1.0d, maxCorner.getY() + 1.0d, maxCorner.getZ() + 1.0d)
        );
    }

    public static @Nullable RegionData unionBoundingRegions(@Nullable RegionData first, @Nullable RegionData second) {
        if (first == null || !first.isComplete()) {
            return second;
        }
        if (second == null || !second.isComplete()) {
            return first;
        }

        BlockPos firstMin = first.getMinCorner();
        BlockPos firstMax = first.getMaxCorner();
        BlockPos secondMin = second.getMinCorner();
        BlockPos secondMax = second.getMaxCorner();
        if (firstMin == null || firstMax == null || secondMin == null || secondMax == null) {
            return first;
        }

        return new RegionData(
            new BlockPos(
                Math.min(firstMin.getX(), secondMin.getX()),
                Math.min(firstMin.getY(), secondMin.getY()),
                Math.min(firstMin.getZ(), secondMin.getZ())
            ),
            new BlockPos(
                Math.max(firstMax.getX(), secondMax.getX()),
                Math.max(firstMax.getY(), secondMax.getY()),
                Math.max(firstMax.getZ(), secondMax.getZ())
            )
        );
    }

    public static BlockPosList voxelizeComposite(CompositeGeometryData geometry, boolean fillSolid) {
        Set<BlockPos> mergedPositions = new LinkedHashSet<>();

        for (GeometryData child : geometry.getGeometries()) {
            BlockPosList childBlocks = voxelize(child, fillSolid);
            for (BlockPos pos : childBlocks) {
                mergedPositions.add(pos.toImmutable());
            }
        }

        return new BlockPosList(mergedPositions);
    }

    public static BlockPosList voxelizeDifference(DifferenceGeometryData geometry, boolean fillSolid) {
        BlockPosList baseBlocks = voxelize(geometry.getMinuend(), fillSolid);
        BlockPosList cutterBlocks = voxelize(geometry.getSubtrahend(), true);

        Set<BlockPos> result = new LinkedHashSet<>();
        for (BlockPos pos : baseBlocks) {
            result.add(pos.toImmutable());
        }
        for (BlockPos pos : cutterBlocks) {
            result.remove(pos);
        }
        return new BlockPosList(result);
    }

    public static BlockPosList voxelizeIntersection(IntersectionGeometryData geometry, boolean fillSolid) {
        BlockPosList leftBlocks = voxelize(geometry.getLeft(), fillSolid);
        BlockPosList rightBlocks = voxelize(geometry.getRight(), fillSolid);

        Set<BlockPos> rightSet = new HashSet<>();
        for (BlockPos pos : rightBlocks) {
            rightSet.add(pos.toImmutable());
        }

        Set<BlockPos> result = new LinkedHashSet<>();
        for (BlockPos pos : leftBlocks) {
            if (rightSet.contains(pos)) {
                result.add(pos.toImmutable());
            }
        }
        return new BlockPosList(result);
    }

    public static @Nullable RegionData createCompositeBoundingRegion(CompositeGeometryData geometry) {
        RegionData merged = null;
        for (GeometryData child : geometry.getGeometries()) {
            merged = unionBoundingRegions(merged, createBoundingRegion(child));
        }
        return merged;
    }

    public static @Nullable RegionData createIntersectionBoundingRegion(IntersectionGeometryData geometry) {
        RegionData leftRegion = createBoundingRegion(geometry.getLeft());
        RegionData rightRegion = createBoundingRegion(geometry.getRight());
        if (leftRegion == null || rightRegion == null || !leftRegion.isComplete() || !rightRegion.isComplete()) {
            return null;
        }

        BlockPos leftMin = leftRegion.getMinCorner();
        BlockPos leftMax = leftRegion.getMaxCorner();
        BlockPos rightMin = rightRegion.getMinCorner();
        BlockPos rightMax = rightRegion.getMaxCorner();
        if (leftMin == null || leftMax == null || rightMin == null || rightMax == null) {
            return null;
        }

        BlockPos minCorner = new BlockPos(
            Math.max(leftMin.getX(), rightMin.getX()),
            Math.max(leftMin.getY(), rightMin.getY()),
            Math.max(leftMin.getZ(), rightMin.getZ())
        );
        BlockPos maxCorner = new BlockPos(
            Math.min(leftMax.getX(), rightMax.getX()),
            Math.min(leftMax.getY(), rightMax.getY()),
            Math.min(leftMax.getZ(), rightMax.getZ())
        );

        if (minCorner.getX() > maxCorner.getX()
            || minCorner.getY() > maxCorner.getY()
            || minCorner.getZ() > maxCorner.getZ()) {
            return null;
        }

        return new RegionData(minCorner, maxCorner);
    }

    public static BlockPosList voxelizeBox(BoxGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();

        if (geometry.isOriented()) {
            RegionData region = BoxBlockGenerator.createOrientedBoundingRegion(
                geometry.getCenter(),
                geometry.getHalfExtents(),
                geometry.getOrientationMatrix()
            );
            if (!region.isComplete()) {
                return blocks;
            }

            BlockPos minCorner = region.getMinCorner();
            BlockPos maxCorner = region.getMaxCorner();
            if (minCorner == null || maxCorner == null) {
                return blocks;
            }

            BoxBlockGenerator.populateOrientedBox(
                blocks,
                minCorner,
                maxCorner,
                geometry.getCenter(),
                geometry.getHalfExtents(),
                geometry.getOrientationMatrix(),
                fillSolid
            );
            return blocks;
        }

        RegionData region = createAxisAlignedRegion(geometry);
        if (!region.isComplete()) {
            return blocks;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return blocks;
        }

        BoxBlockGenerator.populateAxisAlignedBox(blocks, minCorner, maxCorner, fillSolid);
        return blocks;
    }

    public static BlockPosList voxelizeTorus(TorusGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = TorusBlockGenerator.createBoundingRegion(geometry);
        TorusBlockGenerator.populateTorus(blocks, region, geometry, fillSolid);
        return blocks;
    }

    public static BlockPosList voxelizeCylinder(CylinderGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = CylinderBlockGenerator.createBoundingRegion(geometry);
        CylinderBlockGenerator.populateCylinder(blocks, region, geometry, fillSolid);
        return blocks;
    }

    public static BlockPosList voxelizeEllipsoid(EllipsoidGeometryData geometry, boolean fillSolid) {
        return voxelizeEllipsoid(
            geometry,
            fillSolid ? EllipsoidBlockGenerator.VoxelMode.SOLID : EllipsoidBlockGenerator.VoxelMode.SHELL,
            1.0d
        );
    }

    public static BlockPosList voxelizeHemisphere(HemisphereGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = HemisphereBlockGenerator.createBoundingRegion(geometry);
        HemisphereBlockGenerator.populateHemisphere(blocks, region, geometry, fillSolid);
        return blocks;
    }

    public static BlockPosList voxelizeEllipsoid(EllipsoidGeometryData geometry,
                                                 EllipsoidBlockGenerator.VoxelMode voxelMode,
                                                 double shellThickness) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = EllipsoidBlockGenerator.createBoundingRegion(geometry);
        EllipsoidBlockGenerator.populateEllipsoid(blocks, region, geometry, voxelMode, shellThickness);
        return blocks;
    }

    public static BlockPosList voxelizeCone(ConeGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = ConeBlockGenerator.createBoundingRegion(geometry);
        ConeBlockGenerator.populateCone(blocks, region, geometry, fillSolid);
        return blocks;
    }

    public static BlockPosList voxelizeFrustumCone(FrustumConeGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = FrustumConeBlockGenerator.createBoundingRegion(geometry);
        FrustumConeBlockGenerator.populateFrustum(blocks, region, geometry, fillSolid);
        return blocks;
    }

    public static BlockPosList voxelizePrism(PrismGeometryData geometry, boolean fillSolid) {
        final double eps = 1.0e-9;
        Vector3d extrusion = geometry.getExtrusionVector();
        double extrusionLengthSquared = extrusion.lengthSquared();
        if (extrusionLengthSquared <= eps) {
            return new BlockPosList();
        }

        RegionData region = createPrismBoundingRegion(geometry);
        if (!region.isComplete()) {
            return new BlockPosList();
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return new BlockPosList();
        }

        List<Vector3d> baseVertices = geometry.getBaseVertices();
        Vector3d baseOrigin = new Vector3d(baseVertices.get(0));
        Vector3d axis = new Vector3d(extrusion).normalize();

        Vector3d u = buildPrismPlaneU(baseVertices, baseOrigin, axis, eps);
        Vector3d v = new Vector3d(axis).cross(u).normalize();

        List<Double> polygonX = new ArrayList<>(baseVertices.size());
        List<Double> polygonY = new ArrayList<>(baseVertices.size());
        for (Vector3d vertex : baseVertices) {
            Vector3d rel = new Vector3d(vertex).sub(baseOrigin);
            polygonX.add(rel.dot(u));
            polygonY.add(rel.dot(v));
        }

        Set<BlockPos> solidBlocks = new LinkedHashSet<>();
        Vector3d sample = new Vector3d();
        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    sample.set(x + 0.5d, y + 0.5d, z + 0.5d);

                    Vector3d fromBase = new Vector3d(sample).sub(baseOrigin);
                    double t = fromBase.dot(extrusion) / extrusionLengthSquared;
                    if (t < -eps || t > 1.0d + eps) {
                        continue;
                    }

                    Vector3d projected = new Vector3d(sample).sub(new Vector3d(extrusion).mul(t));
                    Vector3d inBasePlane = projected.sub(baseOrigin);
                    double px = inBasePlane.dot(u);
                    double py = inBasePlane.dot(v);

                    if (isPointInsideOrOnPolygon2D(px, py, polygonX, polygonY, eps)) {
                        solidBlocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        if (fillSolid) {
            return new BlockPosList(solidBlocks);
        }

        Set<BlockPos> shellBlocks = new LinkedHashSet<>();
        for (BlockPos pos : solidBlocks) {
            if (isBoundaryBlock(pos, solidBlocks)) {
                shellBlocks.add(pos.toImmutable());
            }
        }
        return new BlockPosList(shellBlocks);
    }

    public static BlockPosList voxelizeSquarePyramid(SquarePyramidGeometryData geometry, boolean fillSolid) {
        RegionData region = createSquarePyramidBoundingRegion(geometry);
        if (region == null || !region.isComplete()) {
            return new BlockPosList();
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return new BlockPosList();
        }

        final double eps = 1.0e-6d;
        final double shell = 0.75d;
        final double half = geometry.getBaseSize() * 0.5d;
        final double height = geometry.getHeight();
        Vector3d baseCenter = geometry.getBaseCenter();
        Vector3d xAxis = geometry.getXAxis();
        Vector3d yAxis = geometry.getYAxis();
        Vector3d normal = geometry.getNormal();

        Set<BlockPos> blocks = new LinkedHashSet<>();
        Vector3d sample = new Vector3d();
        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    sample.set(x + 0.5d, y + 0.5d, z + 0.5d);
                    Vector3d relative = new Vector3d(sample).sub(baseCenter);

                    double localX = relative.dot(xAxis);
                    double localY = relative.dot(yAxis);
                    double localZ = relative.dot(normal);
                    if (localZ < -eps || localZ > height + eps) {
                        continue;
                    }

                    double scale = 1.0d - (localZ / height);
                    double limit = half * scale;
                    if (Math.abs(localX) > limit + eps || Math.abs(localY) > limit + eps) {
                        continue;
                    }

                    if (fillSolid) {
                        blocks.add(new BlockPos(x, y, z));
                        continue;
                    }

                    boolean nearBase = localZ <= shell;
                    boolean nearFaceX = limit - Math.abs(localX) <= shell;
                    boolean nearFaceY = limit - Math.abs(localY) <= shell;
                    boolean nearApex = height - localZ <= shell;
                    if (nearBase || nearFaceX || nearFaceY || nearApex) {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        return new BlockPosList(blocks);
    }

    private static Vector3d buildPrismPlaneU(List<Vector3d> baseVertices,
                                             Vector3d baseOrigin,
                                             Vector3d axis,
                                             double eps) {
        for (int i = 1; i < baseVertices.size(); i++) {
            Vector3d edge = new Vector3d(baseVertices.get(i)).sub(baseOrigin);
            Vector3d projectedEdge = new Vector3d(axis).mul(edge.dot(axis));
            edge.sub(projectedEdge);
            if (edge.lengthSquared() > eps) {
                return edge.normalize();
            }
        }

        Vector3d fallback = Math.abs(axis.x) < 0.9d
            ? new Vector3d(1.0d, 0.0d, 0.0d)
            : new Vector3d(0.0d, 1.0d, 0.0d);
        return fallback.sub(new Vector3d(axis).mul(fallback.dot(axis))).normalize();
    }

    private static boolean isBoundaryBlock(BlockPos pos, Set<BlockPos> solidBlocks) {
        return !solidBlocks.contains(pos.add(1, 0, 0))
            || !solidBlocks.contains(pos.add(-1, 0, 0))
            || !solidBlocks.contains(pos.add(0, 1, 0))
            || !solidBlocks.contains(pos.add(0, -1, 0))
            || !solidBlocks.contains(pos.add(0, 0, 1))
            || !solidBlocks.contains(pos.add(0, 0, -1));
    }

    private static boolean isPointInsideOrOnPolygon2D(double px,
                                                      double py,
                                                      List<Double> polygonX,
                                                      List<Double> polygonY,
                                                      double eps) {
        int size = polygonX.size();
        if (size < 3) {
            return false;
        }

        for (int i = 0, j = size - 1; i < size; j = i++) {
            if (isPointOnSegment2D(
                px, py,
                polygonX.get(j), polygonY.get(j),
                polygonX.get(i), polygonY.get(i),
                eps
            )) {
                return true;
            }
        }

        boolean inside = false;
        for (int i = 0, j = size - 1; i < size; j = i++) {
            double xi = polygonX.get(i);
            double yi = polygonY.get(i);
            double xj = polygonX.get(j);
            double yj = polygonY.get(j);

            boolean intersects = ((yi > py) != (yj > py))
                && (px < (xj - xi) * (py - yi) / ((yj - yi) + eps) + xi);
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static boolean isPointOnSegment2D(double px,
                                              double py,
                                              double x1,
                                              double y1,
                                              double x2,
                                              double y2,
                                              double eps) {
        double cross = (px - x1) * (y2 - y1) - (py - y1) * (x2 - x1);
        if (Math.abs(cross) > eps) {
            return false;
        }

        double dot = (px - x1) * (px - x2) + (py - y1) * (py - y2);
        return dot <= eps;
    }

    public static BlockPosList voxelizeOctahedron(OctahedronGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = OctahedronBlockGenerator.createBoundingRegion(geometry);
        OctahedronBlockGenerator.populateOctahedron(blocks, region, geometry, fillSolid);
        return blocks;
    }

    public static BlockPosList voxelizeIcosahedron(IcosahedronGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = IcosahedronBlockGenerator.createBoundingRegion(geometry);
        IcosahedronBlockGenerator.populateIcosahedron(blocks, region, geometry);
        return blocks;
    }

    public static BlockPosList voxelizeDodecahedron(DodecahedronGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = DodecahedronBlockGenerator.createBoundingRegion(geometry);
        DodecahedronBlockGenerator.populateDodecahedron(blocks, region, geometry);
        return blocks;
    }

    public static BlockPosList voxelizeSphere(SphereData geometry, boolean fillSolid) {
        return voxelizeSphere(
            geometry,
            fillSolid ? SphereBlockGenerator.VoxelMode.SOLID : SphereBlockGenerator.VoxelMode.SHELL,
            1.0d
        );
    }

    public static BlockPosList voxelizeSphere(SphereData geometry,
                                              SphereBlockGenerator.VoxelMode voxelMode,
                                              double shellThickness) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = SphereBlockGenerator.createBoundingRegion(geometry);
        SphereBlockGenerator.populateSphere(blocks, region, geometry, voxelMode, shellThickness);
        return blocks;
    }

    public static BlockPosList voxelizeTetrahedron(TetrahedronGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = TetrahedronBlockGenerator.createBoundingRegion(geometry);
        // First pass matches the legacy generator, which only exposed a solid tetrahedron.
        TetrahedronBlockGenerator.populateTetrahedron(blocks, region, geometry);
        return blocks;
    }

    public static BlockPosList voxelizeSdfGeometry(SdfGeometryData geometry, boolean fillSolid) {
        RegionData region = createBoundingRegion(geometry);
        if (region == null || !region.isComplete()) {
            return new BlockPosList();
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return new BlockPosList();
        }

        Set<BlockPos> solid = new LinkedHashSet<>();
        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    double d = geometry.getSdf().sampleDistance(new Vector3d(x + 0.5d, y + 0.5d, z + 0.5d));
                    if (d <= geometry.getIsoValue()) {
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        if (fillSolid) {
            return new BlockPosList(solid);
        }

        Set<BlockPos> shell = new LinkedHashSet<>();
        for (BlockPos pos : solid) {
            if (isBoundaryBlock(pos, solid)) {
                shell.add(pos.toImmutable());
            }
        }
        return new BlockPosList(shell);
    }

    private static boolean boundingVolumeExceedsLimit(GeometryData geometry) {
        RegionData region = createBoundingRegion(geometry);
        if (region == null || !region.isComplete()) {
            return false;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return false;
        }

        long volume = regionVolume(minCorner, maxCorner);
        if (volume > MAX_SDF_VOXEL_VOLUME) {
            NodeCraft.LOGGER.warn(
                "Geometry voxelization skipped: bounds volume {} exceeds limit {} for {}.",
                volume,
                MAX_SDF_VOXEL_VOLUME,
                geometry.getClass().getSimpleName()
            );
            return true;
        }
        return false;
    }

    private static long regionVolume(BlockPos minCorner, BlockPos maxCorner) {
        long sizeX = (long) maxCorner.getX() - minCorner.getX() + 1L;
        long sizeY = (long) maxCorner.getY() - minCorner.getY() + 1L;
        long sizeZ = (long) maxCorner.getZ() - minCorner.getZ() + 1L;
        return sizeX * sizeY * sizeZ;
    }

    public static RegionData createAxisAlignedRegion(BoxGeometryData geometry) {
        Vector3d center = geometry.getCenter();
        Vector3d halfExtents = geometry.getHalfExtents();

        // Box voxelization here samples integer grid points (same convention as BoxBlockGenerator).
        // Use ceil/floor to keep one-unit extents from expanding to two blocks.
        final double epsilon = 1.0e-9d;
        int minX = (int) Math.ceil(center.x - halfExtents.x - epsilon);
        int minY = (int) Math.ceil(center.y - halfExtents.y - epsilon);
        int minZ = (int) Math.ceil(center.z - halfExtents.z - epsilon);
        int maxX = (int) Math.floor(center.x + halfExtents.x + epsilon);
        int maxY = (int) Math.floor(center.y + halfExtents.y + epsilon);
        int maxZ = (int) Math.floor(center.z + halfExtents.z + epsilon);

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return new RegionData(null, null);
        }

        BlockPos minCorner = new BlockPos(minX, minY, minZ);
        BlockPos maxCorner = new BlockPos(maxX, maxY, maxZ);
        return new RegionData(minCorner, maxCorner);
    }

    public static RegionData createPrismBoundingRegion(PrismGeometryData geometry) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Vector3d point : geometry.getBaseVertices()) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        for (Vector3d point : geometry.getTopVertices()) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }

        return new RegionData(
            BlockPos.ofFloored(minX, minY, minZ),
            BlockPos.ofFloored(maxX, maxY, maxZ)
        );
    }

    public static RegionData createSquarePyramidBoundingRegion(SquarePyramidGeometryData geometry) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Vector3d point : geometry.getBaseVertices()) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }

        Vector3d apex = geometry.getApex();
        minX = Math.min(minX, apex.x);
        minY = Math.min(minY, apex.y);
        minZ = Math.min(minZ, apex.z);
        maxX = Math.max(maxX, apex.x);
        maxY = Math.max(maxY, apex.y);
        maxZ = Math.max(maxZ, apex.z);

        return new RegionData(
            BlockPos.ofFloored(minX, minY, minZ),
            BlockPos.ofFloored(maxX, maxY, maxZ)
        );
    }
}
