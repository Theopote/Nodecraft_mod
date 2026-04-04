package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BoxBlockGenerator;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a box / cuboid either from center + size or from two corners.
 */
@NodeInfo(
    id = "spatial.generators.box_blocks",
    displayName = "Box (Blocks)",
    description = "Generates a filled or hollow box from center + size or from two corner points",
    category = "spatial.generators"
)
public class BoxBlocksNode extends BaseNode {

    @NodeProperty(displayName = "Fill Box", category = "Shape", order = 1,
        description = "When disabled, only the outer shell is generated")
    private boolean fillBox = true;

    @NodeProperty(displayName = "Output Region Only", category = "Output", order = 10,
        description = "When enabled, the node skips block generation and outputs only the region")
    private boolean outputAsRegion = false;

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_SIZE_X_ID = "input_size_x";
    private static final String INPUT_SIZE_Y_ID = "input_size_y";
    private static final String INPUT_SIZE_Z_ID = "input_size_z";
    private static final String INPUT_ROT_X_ID = "input_rotation_x";
    private static final String INPUT_ROT_Y_ID = "input_rotation_y";
    private static final String INPUT_ROT_Z_ID = "input_rotation_z";
    private static final String INPUT_CORNER_A_ID = "input_corner_a";
    private static final String INPUT_CORNER_B_ID = "input_corner_b";

    private static final String OUTPUT_BOX_BLOCKS_ID = "output_box_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_MIN_CORNER_ID = "output_min_corner";
    private static final String OUTPUT_MAX_CORNER_ID = "output_max_corner";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_BOX_GEOMETRY_ID = "output_box_geometry";

    public BoxBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.box_blocks");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Center point of the box", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional plane used to orient the box", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_SIZE_X_ID, "Size X", "Width in blocks on the X axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SIZE_Y_ID, "Size Y", "Height in blocks on the Y axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SIZE_Z_ID, "Size Z", "Depth in blocks on the Z axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROT_X_ID, "Rotation X", "Rotation around the X axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "Rotation Y", "Rotation around the Y axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "Rotation Z", "Rotation around the Z axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CORNER_A_ID, "Corner A", "Optional first corner of the box", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_CORNER_B_ID, "Corner B", "Optional second corner of the box", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_BOX_BLOCKS_ID, "Blocks", "Blocks forming the box", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Box region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_MIN_CORNER_ID, "Min Corner", "Minimum corner of the box", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_MAX_CORNER_ID, "Max Corner", "Maximum corner of the box", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BOX_GEOMETRY_ID, "Box Geometry", "Resolved box geometry", NodeDataType.BOX_GEOMETRY, this));
    }

    @Override
    public String getDescription() {
        return "Generates a filled or hollow box from center + size or from two corner points";
    }

    @Override
    public String getDisplayName() {
        return "Box (Blocks)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BoxDefinition definition = resolveBoxDefinition();
        RegionData region = definition != null ? definition.region() : null;
        BlockPosList blocksList = new BlockPosList();
        BlockPos minCorner = null;
        BlockPos maxCorner = null;

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
        BoxGeometryData geometry = definition != null ? definition.toGeometryData() : null;
        outputValues.put(OUTPUT_COUNT_ID, blocksList.size());
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_BOX_GEOMETRY_ID, geometry);

    }

    private BoxDefinition resolveBoxDefinition() {
        Object cornerAObj = inputValues.get(INPUT_CORNER_A_ID);
        Object cornerBObj = inputValues.get(INPUT_CORNER_B_ID);

        if (cornerAObj instanceof BlockPos cornerA && cornerBObj instanceof BlockPos cornerB) {
            RegionData region = new RegionData(cornerA.toImmutable(), cornerB.toImmutable());
            return new BoxDefinition(region, null, null, null, false);
        }

        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object sizeXObj = inputValues.get(INPUT_SIZE_X_ID);
        Object sizeYObj = inputValues.get(INPUT_SIZE_Y_ID);
        Object sizeZObj = inputValues.get(INPUT_SIZE_Z_ID);
        Object rotXObj = inputValues.get(INPUT_ROT_X_ID);
        Object rotYObj = inputValues.get(INPUT_ROT_Y_ID);
        Object rotZObj = inputValues.get(INPUT_ROT_Z_ID);

        if (!(centerObj instanceof BlockPos center) ||
            !(sizeXObj instanceof Number sizeXNumber) ||
            !(sizeYObj instanceof Number sizeYNumber) ||
            !(sizeZObj instanceof Number sizeZNumber)) {
            return null;
        }

        int sizeX = Math.max(1, sizeXNumber.intValue());
        int sizeY = Math.max(1, sizeYNumber.intValue());
        int sizeZ = Math.max(1, sizeZNumber.intValue());
        double rotationX = rotXObj instanceof Number rotXNumber ? rotXNumber.doubleValue() : 0.0d;
        double rotationY = rotYObj instanceof Number rotYNumber ? rotYNumber.doubleValue() : 0.0d;
        double rotationZ = rotZObj instanceof Number rotZNumber ? rotZNumber.doubleValue() : 0.0d;

        Vector3d centerVector = new Vector3d(center.getX(), center.getY(), center.getZ());
        Vector3d halfExtents = new Vector3d(
            (sizeX - 1) / 2.0d,
            (sizeY - 1) / 2.0d,
            (sizeZ - 1) / 2.0d
        );
        Matrix3d orientationMatrix = createOrientationMatrix(planeObj, rotationX, rotationY, rotationZ);
        boolean rotated = hasRotation(rotationX, rotationY, rotationZ) || planeObj instanceof PlaneData;

        RegionData region = rotated
            ? BoxBlockGenerator.createOrientedBoundingRegion(centerVector, halfExtents, orientationMatrix)
            : BoxBlockGenerator.createAxisAlignedRegion(center, sizeX, sizeY, sizeZ);

        return new BoxDefinition(region, centerVector, halfExtents, orientationMatrix, rotated);
    }

    private void populateBlocks(BlockPosList blocksList, BlockPos minCorner, BlockPos maxCorner, BoxDefinition definition) {
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

    private boolean hasRotation(double rotationX, double rotationY, double rotationZ) {
        return Math.abs(rotationX) > 1e-9d
            || Math.abs(rotationY) > 1e-9d
            || Math.abs(rotationZ) > 1e-9d;
    }

    private Matrix3d createRotationMatrix(double rotationX, double rotationY, double rotationZ) {
        return new Matrix3d()
            .rotateXYZ(
                Math.toRadians(rotationX),
                Math.toRadians(rotationY),
                Math.toRadians(rotationZ)
            );
    }

    private Matrix3d createOrientationMatrix(Object planeObj, double rotationX, double rotationY, double rotationZ) {
        Matrix3d orientationMatrix = planeObj instanceof PlaneData planeData
            ? createPlaneAlignmentMatrix(planeData)
            : new Matrix3d().identity();

        orientationMatrix.mul(createRotationMatrix(rotationX, rotationY, rotationZ));
        return orientationMatrix;
    }

    private Matrix3d createPlaneAlignmentMatrix(PlaneData planeData) {
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

    private record BoxDefinition(
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
