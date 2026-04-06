package com.nodecraft.nodesystem.nodes.spatial.voxel;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.SphereBlockGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "spatial.voxel.sphere_geometry_voxelizer",
    displayName = "Sphere Geometry To Blocks",
    description = "Voxelizes SphereData into Minecraft block coordinates",
    category = "spatial.voxel"
)
public class SphereGeometryVoxelizerNode extends BaseNode {

    public enum VoxelMode {
        SOLID,
        SHELL
    }

    @NodeProperty(displayName = "Voxel Mode", category = "Shape", order = 1)
    private VoxelMode voxelMode = VoxelMode.SOLID;

    @NodeProperty(displayName = "Shell Thickness", category = "Shape", order = 2)
    private double shellThickness = 1.0d;

    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public SphereGeometryVoxelizerNode() {
        super(UUID.randomUUID(), "spatial.voxel.sphere_geometry_voxelizer");

        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry to voxelize", NodeDataType.SPHERE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the sphere", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Voxelizes SphereData into Minecraft block coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_SPHERE_GEOMETRY_ID);
        BlockPosList blocks = new BlockPosList();
        RegionData region = null;

        if (geometryObj instanceof SphereData geometry) {
            blocks = GeometryVoxelizer.voxelizeSphere(
                geometry,
                voxelMode == VoxelMode.SHELL ? SphereBlockGenerator.VoxelMode.SHELL : SphereBlockGenerator.VoxelMode.SOLID,
                shellThickness
            );
            region = GeometryVoxelizer.createBoundingRegion(geometry);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
    }

    public VoxelMode getVoxelMode() {
        return voxelMode;
    }

    public void setVoxelMode(VoxelMode voxelMode) {
        VoxelMode resolvedMode = voxelMode == null ? VoxelMode.SOLID : voxelMode;
        if (this.voxelMode != resolvedMode) {
            this.voxelMode = resolvedMode;
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

    public double getShellThickness() {
        return shellThickness;
    }

    public void setShellThickness(double shellThickness) {
        double resolvedThickness = Math.max(0.0d, shellThickness);
        if (Double.compare(this.shellThickness, resolvedThickness) != 0) {
            this.shellThickness = resolvedThickness;
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
        if (map.get("fillSphere") instanceof Boolean fillValue) {
            setVoxelMode(fillValue ? VoxelMode.SOLID : VoxelMode.SHELL);
        }
        if (map.get("voxelMode") instanceof String voxelModeValue) {
            setVoxelModeString(voxelModeValue);
        }
        if (map.get("shellThickness") instanceof Number shellThicknessValue) {
            setShellThickness(shellThicknessValue.doubleValue());
        }
    }
}
