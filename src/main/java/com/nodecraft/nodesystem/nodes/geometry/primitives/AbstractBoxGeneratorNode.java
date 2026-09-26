package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BoxBlockGenerator;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractBoxGeneratorNode extends BaseNode {

    @NodeProperty(displayName = "Fill Box", category = "Shape", order = 1,
        description = "When disabled, only the outer shell is generated")
    protected boolean fillBox = true;

    @NodeProperty(displayName = "Output Region Only", category = "Output", order = 10,
        description = "When enabled, the node skips block generation and outputs only the region")
    protected boolean outputAsRegion = false;

    protected static final String OUTPUT_BOX_BLOCKS_ID = "output_box_blocks";
    protected static final String OUTPUT_REGION_ID = "output_region";
    protected static final String OUTPUT_MIN_CORNER_ID = "output_min_corner";
    protected static final String OUTPUT_MAX_CORNER_ID = "output_max_corner";
    protected static final String OUTPUT_COUNT_ID = "output_count";
    protected static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    protected static final String OUTPUT_BOX_GEOMETRY_ID = "output_box_geometry";
    protected static final String OUTPUT_CORNERS_ID = "output_corners";
    protected static final String OUTPUT_FACES_ID = "output_faces";

    protected AbstractBoxGeneratorNode(String typeId) {
        super(UUID.randomUUID(), typeId);
        addCommonOutputs();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BoxDefinition definition = resolveBoxDefinition();
        RegionData region = definition != null ? definition.region() : null;
        BlockPosList blocksList = new BlockPosList();
        BlockPos minCorner = null;
        BlockPos maxCorner = null;
        BoxGeometryData geometry = definition != null ? definition.toGeometryData() : null;
        List<PointData> corners = geometry != null ? toPointList(geometry.getCorners()) : List.of();
        List<BoxFaceData> faces = geometry != null ? geometry.getFaces() : List.of();

        if (region != null && region.isComplete()) {
            minCorner = region.getMinCorner();
            maxCorner = region.getMaxCorner();

            if (!outputAsRegion && minCorner != null && maxCorner != null) {
                populateBlocks(blocksList, minCorner, maxCorner, definition);
            }
        }

        outputValues.put(OUTPUT_BOX_BLOCKS_ID, blocksList);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_MIN_CORNER_ID, minCorner);
        outputValues.put(OUTPUT_MAX_CORNER_ID, maxCorner);
        outputValues.put(OUTPUT_COUNT_ID, blocksList.size());
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_BOX_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_CORNERS_ID, corners);
        outputValues.put(OUTPUT_FACES_ID, faces);
    }

    private static List<PointData> toPointList(List<Vector3d> vectors) {
        List<PointData> points = new ArrayList<>(vectors.size());
        for (Vector3d vector : vectors) {
            points.add(new PointData(vector));
        }
        return List.copyOf(points);
    }

    protected abstract BoxDefinition resolveBoxDefinition();

    protected void addCommonOutputs() {
        addOutputPort(new BasePort(OUTPUT_BOX_BLOCKS_ID, "Blocks",
                "Legacy convenience: voxelized blocks from this box. Prefer Geometry → Voxelize for new graphs.",
                NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region",
                "Legacy convenience: axis-aligned bounding region of the box. Prefer Geometry → Voxelize for new graphs.",
                NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_MIN_CORNER_ID, "Min Corner", "Minimum corner of the bounding region", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_MAX_CORNER_ID, "Max Corner", "Maximum corner of the bounding region", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
                "Legacy convenience: number of generated blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Canonical continuous box geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BOX_GEOMETRY_ID, "Box Geometry", "Resolved box geometry for analysis and editing", NodeDataType.BOX_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CORNERS_ID, "Corners",
                "Ordered list of the 8 box corner points. Use Get Box Corner to access one by index 0-7.",
                NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FACES_ID, "Faces", "Ordered list of the 6 box faces. Use Get Box Face to access one by index 0-5.", NodeDataType.LIST, this));
    }

    protected BoxDefinition createContinuousCenterDefinition(
        Vector3d center,
        double sizeX,
        double sizeY,
        double sizeZ,
        Object planeObj,
        double rotationX,
        double rotationY,
        double rotationZ
    ) {
        double resolvedX = Math.abs(sizeX);
        double resolvedY = Math.abs(sizeY);
        double resolvedZ = Math.abs(sizeZ);
        if (!Double.isFinite(resolvedX) || !Double.isFinite(resolvedY) || !Double.isFinite(resolvedZ)
                || resolvedX <= 0.0d || resolvedY <= 0.0d || resolvedZ <= 0.0d) {
            return null;
        }

        Vector3d centerVector = new Vector3d(center);
        Vector3d halfExtents = new Vector3d(resolvedX * 0.5d, resolvedY * 0.5d, resolvedZ * 0.5d);

        Matrix3d orientationMatrix = createOrientationMatrix(planeObj, rotationX, rotationY, rotationZ);
        boolean rotated = hasRotation(rotationX, rotationY, rotationZ) || planeObj instanceof PlaneData;

        // Floor only when deriving Region/Blocks; geometry stays continuous.
        RegionData region = BoxBlockGenerator.createOrientedBoundingRegion(centerVector, halfExtents, orientationMatrix);
        return new BoxDefinition(region, centerVector, halfExtents, orientationMatrix, rotated);
    }

    protected BoxDefinition createContinuousAxisAlignedDefinition(Vector3d cornerA, Vector3d cornerB) {
        double minX = Math.min(cornerA.x, cornerB.x);
        double minY = Math.min(cornerA.y, cornerB.y);
        double minZ = Math.min(cornerA.z, cornerB.z);
        double maxX = Math.max(cornerA.x, cornerB.x);
        double maxY = Math.max(cornerA.y, cornerB.y);
        double maxZ = Math.max(cornerA.z, cornerB.z);
        double extentX = maxX - minX;
        double extentY = maxY - minY;
        double extentZ = maxZ - minZ;
        if (extentX <= 0.0d || extentY <= 0.0d || extentZ <= 0.0d) {
            return null;
        }

        Vector3d center = new Vector3d((minX + maxX) * 0.5d, (minY + maxY) * 0.5d, (minZ + maxZ) * 0.5d);
        Vector3d halfExtents = new Vector3d(extentX * 0.5d, extentY * 0.5d, extentZ * 0.5d);
        BlockPos minCorner = BlockPos.ofFloored(minX, minY, minZ);
        BlockPos maxCorner = BlockPos.ofFloored(maxX, maxY, maxZ);
        RegionData region = new RegionData(minCorner, maxCorner);
        return new BoxDefinition(region, center, halfExtents, new Matrix3d().identity(), false);
    }

    protected BoxDefinition createContinuousCornerAndSizeDefinition(
        Vector3d corner,
        double sizeX,
        double sizeY,
        double sizeZ,
        Object planeObj,
        double rotationX,
        double rotationY,
        double rotationZ
    ) {
        if (!Double.isFinite(sizeX) || !Double.isFinite(sizeY) || !Double.isFinite(sizeZ)
                || sizeX == 0.0d || sizeY == 0.0d || sizeZ == 0.0d) {
            return null;
        }

        Matrix3d orientationMatrix = createOrientationMatrix(planeObj, rotationX, rotationY, rotationZ);
        Vector3d startOffset = new Vector3d(0, 0, 0);
        Vector3d endOffset = new Vector3d(sizeX, sizeY, sizeZ);
        orientationMatrix.transform(startOffset);
        orientationMatrix.transform(endOffset);
        Vector3d cornerVector = new Vector3d(corner);
        Vector3d startCorner = new Vector3d(cornerVector).add(startOffset);
        Vector3d endCorner = new Vector3d(cornerVector).add(endOffset);
        Vector3d center = new Vector3d(startCorner).add(endCorner).mul(0.5d);
        Vector3d halfExtents = new Vector3d(
            Math.abs(sizeX) / 2.0d,
            Math.abs(sizeY) / 2.0d,
            Math.abs(sizeZ) / 2.0d
        );
        RegionData region = BoxBlockGenerator.createOrientedBoundingRegion(center, halfExtents, orientationMatrix);
        boolean rotated = hasRotation(rotationX, rotationY, rotationZ) || planeObj instanceof PlaneData;
        return new BoxDefinition(region, center, halfExtents, orientationMatrix, rotated);
    }

    protected BoxDefinition createAxisAlignedDefinition(BlockPos minCorner, BlockPos maxCorner) {
        RegionData region = new RegionData(minCorner, maxCorner);
        Vector3d center = new Vector3d(
            (minCorner.getX() + maxCorner.getX()) / 2.0d,
            (minCorner.getY() + maxCorner.getY()) / 2.0d,
            (minCorner.getZ() + maxCorner.getZ()) / 2.0d
        );
        Vector3d halfExtents = new Vector3d(
            (maxCorner.getX() - minCorner.getX()) / 2.0d,
            (maxCorner.getY() - minCorner.getY()) / 2.0d,
            (maxCorner.getZ() - minCorner.getZ()) / 2.0d
        );
        return new BoxDefinition(region, center, halfExtents, new Matrix3d().identity(), false);
    }

    protected BoxDefinition createCenterDefinition(
        BlockPos center,
        int sizeX,
        int sizeY,
        int sizeZ,
        Object planeObj,
        double rotationX,
        double rotationY,
        double rotationZ
    ) {
        int resolvedX = Math.abs(sizeX);
        int resolvedY = Math.abs(sizeY);
        int resolvedZ = Math.abs(sizeZ);
        if (resolvedX == 0 || resolvedY == 0 || resolvedZ == 0) {
            return null;
        }

        Vector3d centerVector = new Vector3d(center.getX(), center.getY(), center.getZ());
        Vector3d halfExtents = new Vector3d(
            resolvedX / 2.0d,
            resolvedY / 2.0d,
            resolvedZ / 2.0d
        );

        Matrix3d orientationMatrix = createOrientationMatrix(planeObj, rotationX, rotationY, rotationZ);
        boolean rotated = hasRotation(rotationX, rotationY, rotationZ) || planeObj instanceof PlaneData;

        RegionData region = rotated
            ? BoxBlockGenerator.createOrientedBoundingRegion(centerVector, halfExtents, orientationMatrix)
            : BoxBlockGenerator.createAxisAlignedRegion(center, resolvedX, resolvedY, resolvedZ);

        return new BoxDefinition(region, centerVector, halfExtents, orientationMatrix, rotated);
    }

    protected BoxDefinition createCornerAndSizeDefinition(
        BlockPos corner,
        int sizeX,
        int sizeY,
        int sizeZ,
        Object planeObj,
        double rotationX,
        double rotationY,
        double rotationZ
    ) {
        int resolvedX = normalizeSignedSize(sizeX);
        int resolvedY = normalizeSignedSize(sizeY);
        int resolvedZ = normalizeSignedSize(sizeZ);
        if (resolvedX == 0 || resolvedY == 0 || resolvedZ == 0) {
            return null;
        }

        Matrix3d orientationMatrix = createOrientationMatrix(planeObj, rotationX, rotationY, rotationZ);
        // 使 size 直接等于几何长度
        Vector3d startOffset = new Vector3d(0, 0, 0);
        Vector3d endOffset = new Vector3d(resolvedX, resolvedY, resolvedZ);
        orientationMatrix.transform(startOffset);
        orientationMatrix.transform(endOffset);
        Vector3d cornerVector = new Vector3d(corner.getX(), corner.getY(), corner.getZ());
        Vector3d startCorner = new Vector3d(cornerVector).add(startOffset);
        Vector3d endCorner = new Vector3d(cornerVector).add(endOffset);
        Vector3d center = new Vector3d(startCorner).add(endCorner).mul(0.5d);
        Vector3d halfExtents = new Vector3d(
            Math.abs(resolvedX) / 2.0d,
            Math.abs(resolvedY) / 2.0d,
            Math.abs(resolvedZ) / 2.0d
        );
        RegionData region = BoxBlockGenerator.createOrientedBoundingRegion(center, halfExtents, orientationMatrix);
        boolean rotated = hasRotation(rotationX, rotationY, rotationZ) || planeObj instanceof PlaneData;
        return new BoxDefinition(region, center, halfExtents, orientationMatrix, rotated);
    }

    protected int normalizeSignedSize(int value) {
        return Math.abs(value);
    }

    protected @Nullable Integer resolveInt(@Nullable Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double asDouble = number.doubleValue();
        if (!Double.isFinite(asDouble)) {
            return null;
        }
        return number.intValue();
    }

    protected double resolveFiniteDouble(@Nullable Object value, double fallback) {
        if (!(value instanceof Number number)) {
            return fallback;
        }
        double resolved = number.doubleValue();
        return Double.isFinite(resolved) ? resolved : fallback;
    }

    protected void populateBlocks(BlockPosList blocksList, BlockPos minCorner, BlockPos maxCorner, BoxDefinition definition) {
        if (!definition.rotated()) {
            BoxBlockGenerator.populateAxisAlignedBox(blocksList, minCorner, maxCorner, fillBox);
            return;
        }

        BoxBlockGenerator.populateOrientedBox(
            blocksList,
            minCorner,
            maxCorner,
            definition.center(),
            definition.halfExtents(),
            definition.orientationMatrix(),
            fillBox
        );
    }

    protected boolean hasRotation(double rotationX, double rotationY, double rotationZ) {
        return Math.abs(rotationX) > 1e-9d
            || Math.abs(rotationY) > 1e-9d
            || Math.abs(rotationZ) > 1e-9d;
    }

    protected Matrix3d createRotationMatrix(double rotationX, double rotationY, double rotationZ) {
        return new Matrix3d()
            .rotateXYZ(
                Math.toRadians(rotationX),
                Math.toRadians(rotationY),
                Math.toRadians(rotationZ)
            );
    }

    protected Matrix3d createOrientationMatrix(Object planeObj, double rotationX, double rotationY, double rotationZ) {
        Matrix3d orientationMatrix = planeObj instanceof PlaneData planeData
            ? createPlaneAlignmentMatrix(planeData)
            : new Matrix3d().identity();

        orientationMatrix.mul(createRotationMatrix(rotationX, rotationY, rotationZ));
        return orientationMatrix;
    }

    protected Matrix3d createPlaneAlignmentMatrix(PlaneData planeData) {
        Vector3d up = new Vector3d(planeData.getNormal());
        if (up.lengthSquared() < 1e-9d) {
            return new Matrix3d().identity();
        }

        up.normalize();
        Vector3d reference = Math.abs(up.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);

        Vector3d xAxis = reference.cross(up, new Vector3d());
        if (xAxis.lengthSquared() < 1e-9d) {
            xAxis.set(0.0d, 0.0d, 1.0d);
        } else {
            xAxis.normalize();
        }

        Vector3d zAxis = new Vector3d(xAxis).cross(up).normalize();

        return new Matrix3d(
            xAxis.x, up.x, zAxis.x,
            xAxis.y, up.y, zAxis.y,
            xAxis.z, up.z, zAxis.z
        );
    }

    public boolean isFillBox() {
        return fillBox;
    }

    public void setFillBox(boolean fillBox) {
        if (this.fillBox != fillBox) {
            this.fillBox = fillBox;
            markDirty();
        }
    }

    public boolean isOutputAsRegion() {
        return outputAsRegion;
    }

    public void setOutputAsRegion(boolean outputAsRegion) {
        if (this.outputAsRegion != outputAsRegion) {
            this.outputAsRegion = outputAsRegion;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("fillBox", fillBox);
        state.put("outputAsRegion", outputAsRegion);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }

        if (stateMap.get("fillBox") instanceof Boolean fillBoxValue) {
            setFillBox(fillBoxValue);
        }
        if (stateMap.get("outputAsRegion") instanceof Boolean outputAsRegionValue) {
            setOutputAsRegion(outputAsRegionValue);
        }
    }

    protected @Nullable BlockPos resolveBlockPosInput(@Nullable Object value) {
        if (value instanceof LineData lineData) {
            Vec3d start = lineData.start();
            return BlockPos.ofFloored(start.x, start.y, start.z);
        }
        if (value instanceof PlaneData planeData) {
            Vector3d point = planeData.getPoint();
            return BlockPos.ofFloored(point.x, point.y, point.z);
        }
        return SpatialValueResolver.resolveBlockPos(value);
    }

    protected @Nullable Vector3d resolveVectorInput(@Nullable Object value) {
        if (value instanceof LineData lineData) {
            Vec3d start = lineData.start();
            return new Vector3d(start.x, start.y, start.z);
        }
        if (value instanceof PlaneData planeData) {
            return planeData.getPoint();
        }
        return SpatialValueResolver.resolveVector3d(value);
    }

    protected record BoxDefinition(
        RegionData region,
        Vector3d center,
        Vector3d halfExtents,
        Matrix3d orientationMatrix,
        boolean rotated
    ) {
        private BoxGeometryData toGeometryData() {
            if (center == null || halfExtents == null || orientationMatrix == null) {
                return null;
            }
            return new BoxGeometryData(center, halfExtents, orientationMatrix, rotated);
        }
    }
}
