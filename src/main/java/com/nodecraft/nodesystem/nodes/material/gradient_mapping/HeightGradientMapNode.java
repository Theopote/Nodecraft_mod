package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Voxel height material map across relative Y bands. Remaps blockId only; preserves stateData.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.gradient_mapping.height_gradient_map",
    displayName = "Height Gradient Map",
    description = "Maps voxelized blocks by relative Y height into material bands. Remaps blockId only; preserves stateData. Geometry is voxelized first.",
    category = "material.gradient_mapping",
    order = 0
)
public class HeightGradientMapNode extends BaseNode {

    @NodeProperty(
        displayName = "Lower End Ratio",
        category = "Bands",
        order = 1,
        description = "Relative height where the lower band ends."
    )
    private double lowerEndRatio = 0.30d;

    @NodeProperty(
        displayName = "Middle End Ratio",
        category = "Bands",
        order = 2,
        description = "Relative height where the middle band ends."
    )
    private double middleEndRatio = 0.70d;

    @NodeProperty(
        displayName = "Upper End Ratio",
        category = "Bands",
        order = 3,
        description = "Relative height where the upper band ends and the peak band begins."
    )
    private double upperEndRatio = 0.90d;

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BOTTOM_ID = "input_bottom";
    private static final String INPUT_MIDDLE_ID = "input_middle";
    private static final String INPUT_TOP_ID = "input_top";
    private static final String INPUT_PEAK_ID = "input_peak";

    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";

    public HeightGradientMapNode() {
        super(UUID.randomUUID(), "material.gradient_mapping.height_gradient_map");
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to remap (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized first when placements/coordinates are empty", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Legacy box geometry (voxelized first)", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Legacy cylinder geometry (voxelized first)", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Legacy sphere geometry (voxelized first)", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Legacy torus geometry (voxelized first)", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOTTOM_ID, "Bottom", "Block for the lower third", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_MIDDLE_ID, "Middle", "Block for the middle third", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_TOP_ID, "Top", "Block for the upper band below the peak", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_PEAK_ID, "Peak", "Block for the topmost band", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with the positions list", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Maps voxelized blocks by relative Y height into material bands. Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String bottom = getInputString(INPUT_BOTTOM_ID, "minecraft:stone");
        String middle = getInputString(INPUT_MIDDLE_ID, "minecraft:dirt");
        String top = getInputString(INPUT_TOP_ID, "minecraft:grass_block");
        String peak = getInputString(INPUT_PEAK_ID, top);

        List<BlockPlacementData> sources = MaterialMappingSupport.resolveSourcePlacements(
            inputValues.get(INPUT_PLACEMENTS_ID),
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            bottom
        );

        List<String> blockIds = new ArrayList<>(sources.size());
        List<BlockPlacementData> placements = new ArrayList<>(sources.size());

        if (sources.isEmpty()) {
            publishOutputs(new BlockPosList(), blockIds, placements);
            return;
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPlacementData placement : sources) {
            BlockPos pos = placement.pos();
            if (pos == null) {
                continue;
            }
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
        }

        double span = maxY - minY;
        if (span < 1e-6d) {
            span = 1.0d;
        }

        BlockPosList outputPositions = new BlockPosList();
        double[] bandEnds = resolveBandEnds();
        for (BlockPlacementData source : sources) {
            BlockPos pos = source.pos();
            if (pos == null) {
                continue;
            }
            double t = (pos.getY() - minY) / span;
            String blockId;
            if (t < bandEnds[0]) {
                blockId = bottom;
            } else if (t < bandEnds[1]) {
                blockId = middle;
            } else if (t < bandEnds[2]) {
                blockId = top;
            } else {
                blockId = peak;
            }

            outputPositions.add(pos);
            blockIds.add(blockId);
            placements.add(MaterialMappingSupport.remapBlockId(source, blockId));
        }

        publishOutputs(outputPositions, blockIds, placements);
    }

    private void publishOutputs(BlockPosList positions, List<String> blockIds, List<BlockPlacementData> placements) {
        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isEmpty()) ? text : fallback;
    }

    private double[] resolveBandEnds() {
        double lower = clamp01(lowerEndRatio);
        double middle = Math.max(lower, clamp01(middleEndRatio));
        double upper = Math.max(middle, clamp01(upperEndRatio));
        return new double[]{lower, middle, upper};
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    public double getLowerEndRatio() {
        return lowerEndRatio;
    }

    public void setLowerEndRatio(double lowerEndRatio) {
        double resolved = clamp01(lowerEndRatio);
        if (Double.compare(this.lowerEndRatio, resolved) != 0) {
            this.lowerEndRatio = resolved;
            markDirty();
        }
    }

    public double getMiddleEndRatio() {
        return middleEndRatio;
    }

    public void setMiddleEndRatio(double middleEndRatio) {
        double resolved = clamp01(middleEndRatio);
        if (Double.compare(this.middleEndRatio, resolved) != 0) {
            this.middleEndRatio = resolved;
            markDirty();
        }
    }

    public double getUpperEndRatio() {
        return upperEndRatio;
    }

    public void setUpperEndRatio(double upperEndRatio) {
        double resolved = clamp01(upperEndRatio);
        if (Double.compare(this.upperEndRatio, resolved) != 0) {
            this.upperEndRatio = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("lowerEndRatio", lowerEndRatio);
            put("middleEndRatio", middleEndRatio);
            put("upperEndRatio", upperEndRatio);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("lowerEndRatio") instanceof Number value) {
            this.lowerEndRatio = clamp01(value.doubleValue());
        }
        if (map.get("middleEndRatio") instanceof Number value) {
            this.middleEndRatio = clamp01(value.doubleValue());
        }
        if (map.get("upperEndRatio") instanceof Number value) {
            this.upperEndRatio = clamp01(value.doubleValue());
        }
    }
}
