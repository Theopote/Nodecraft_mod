package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.EllipsoidBlockGenerator;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.execute.bake_ellipsoid_to_blocks",
    displayName = "Bake Ellipsoid To Blocks",
    description = "Bakes ellipsoid geometry into Minecraft block coordinates for final execution",
    category = "output.execute",
    order = 8
)
@Deprecated
public class EllipsoidGeometryVoxelizerNode extends BaseNode {

    public enum VoxelMode {
        SOLID,
        SHELL
    }

    @NodeProperty(displayName = "Voxel Mode", category = "Shape", order = 1)
    private VoxelMode voxelMode = VoxelMode.SOLID;

    @NodeProperty(displayName = "Shell Thickness", category = "Shape", order = 2)
    private double shellThickness = 1.0d;

    private static final String INPUT_ELLIPSOID_GEOMETRY_ID = "input_ellipsoid_geometry";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public EllipsoidGeometryVoxelizerNode() {
        super(UUID.randomUUID(), "output.execute.bake_ellipsoid_to_blocks");

        addInputPort(new BasePort(INPUT_ELLIPSOID_GEOMETRY_ID, "Ellipsoid Geometry", "Ellipsoid geometry to voxelize", NodeDataType.ELLIPSOID_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the ellipsoid", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Voxelizes EllipsoidGeometryData into Minecraft block coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_ELLIPSOID_GEOMETRY_ID);
        BlockPosList blocks = new BlockPosList();
        RegionData region = null;

        if (geometryObj instanceof EllipsoidGeometryData geometry) {
            blocks = GeometryVoxelizer.voxelizeEllipsoid(
                geometry,
                voxelMode == VoxelMode.SHELL ? EllipsoidBlockGenerator.VoxelMode.SHELL : EllipsoidBlockGenerator.VoxelMode.SOLID,
                shellThickness
            );
            region = GeometryVoxelizer.createBoundingRegion(geometry);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
    }

    public void setVoxelMode(VoxelMode voxelMode) {
        VoxelMode resolved = voxelMode == null ? VoxelMode.SOLID : voxelMode;
        if (this.voxelMode != resolved) {
            this.voxelMode = resolved;
            markDirty();
        }
    }

    public void setVoxelModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setVoxelMode(VoxelMode.SOLID);
            return;
        }
        try {
            setVoxelMode(VoxelMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setVoxelMode(VoxelMode.SOLID);
        }
    }

    public void setShellThickness(double shellThickness) {
        double resolved = Math.max(0.0d, shellThickness);
        if (Double.compare(this.shellThickness, resolved) != 0) {
            this.shellThickness = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("voxelMode", voxelMode.name());
        state.put("shellThickness", shellThickness);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("hollow") instanceof Boolean hollowValue) {
            setVoxelMode(hollowValue ? VoxelMode.SHELL : VoxelMode.SOLID);
        }
        if (map.get("voxelMode") instanceof String value) {
            setVoxelModeString(value);
        }
        if (map.get("shellThickness") instanceof Number value) {
            setShellThickness(value.doubleValue());
        }
        if (map.get("thickness") instanceof Number value) {
            setShellThickness(value.doubleValue());
        }
    }
}
