package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Voxel height material map across relative Y bands. Remaps blockId only; preserves stateData.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.gradient_mapping.height_gradient_map",
    displayName = "Height Gradient Map",
    description = "Maps blocks by relative Y height into Bottom/Middle/Top/Peak bands. Remaps blockId only; preserves stateData.",
    category = "material.gradient_mapping",
    order = 0
)
public class HeightGradientMapNode extends BaseNode {

    @NodeProperty(
        displayName = "Lower End Ratio",
        category = "Bands",
        order = 1,
        description = "Relative height where the lower band ends [0,1]."
    )
    private double lowerEndRatio = 0.30d;

    @NodeProperty(
        displayName = "Middle End Ratio",
        category = "Bands",
        order = 2,
        description = "Relative height where the middle band ends [0,1]."
    )
    private double middleEndRatio = 0.70d;

    @NodeProperty(
        displayName = "Upper End Ratio",
        category = "Bands",
        order = 3,
        description = "Relative height where the upper band ends and the peak begins [0,1]."
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

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

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
        addInputPort(new BasePort(INPUT_BOTTOM_ID, "Bottom", "Block for the lower band", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_MIDDLE_ID, "Middle", "Block for the middle band", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_TOP_ID, "Top", "Block for the upper band below the peak", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_PEAK_ID, "Peak", "Block for the topmost band", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when band ratios and inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Maps blocks by relative Y height into Bottom/Middle/Top/Peak bands. Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GradientMaterialUtils.Validation ratios = validateRatios();
        if (!ratios.valid()) {
            emitFail(ratios.message());
            return;
        }

        String bottomMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_BOTTOM_ID));
        String middleMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_MIDDLE_ID));
        String topMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_TOP_ID));
        String peakMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_PEAK_ID));

        List<BlockPlacementData> fromPlacements = MaterialMappingSupport.extractPlacements(inputValues.get(INPUT_PLACEMENTS_ID));
        boolean placementSource = !fromPlacements.isEmpty();

        List<BlockPlacementData> sources = placementSource
            ? fromPlacements
            : MaterialMappingSupport.resolveSourcePlacements(
                null,
                inputValues.get(INPUT_COORDINATES_ID),
                inputValues.get(INPUT_GEOMETRY_ID),
                inputValues.get(INPUT_BOX_GEOMETRY_ID),
                inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
                inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
                inputValues.get(INPUT_TORUS_GEOMETRY_ID),
                MaterialMappingSupport.firstMappedBlockType(bottomMapped, middleMapped, topMapped, peakMapped)
            );

        if (!placementSource && sources.isEmpty() && GradientMaterialUtils.hasNonPlacementSource(
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID)
        )) {
            emitFail("Explicit band material required for geometry or coordinates input");
            return;
        }

        if (sources.isEmpty()) {
            emitOk(List.of());
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

        boolean singleHeight = maxY == minY;
        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        for (BlockPlacementData source : sources) {
            BlockPos pos = source.pos();
            if (pos == null) {
                continue;
            }
            double t = singleHeight ? 0.0d : (double) (pos.getY() - minY) / (double) (maxY - minY);
            String roleMapped;
            if (t < lowerEndRatio) {
                roleMapped = bottomMapped;
            } else if (t < middleEndRatio) {
                roleMapped = middleMapped;
            } else if (t < upperEndRatio) {
                roleMapped = topMapped;
            } else {
                roleMapped = peakMapped;
            }
            String blockId = MaterialMappingSupport.resolveMaterialTarget(roleMapped, source.blockId());
            placements.add(MaterialMappingSupport.remapBlockId(source, blockId));
        }

        emitOk(placements);
    }

    private GradientMaterialUtils.Validation validateRatios() {
        if (!Double.isFinite(lowerEndRatio) || !Double.isFinite(middleEndRatio) || !Double.isFinite(upperEndRatio)) {
            return GradientMaterialUtils.Validation.fail("Band ratios must be finite");
        }
        if (lowerEndRatio < 0.0d || upperEndRatio > 1.0d) {
            return GradientMaterialUtils.Validation.fail("Band ratios must be within [0, 1]");
        }
        if (!(lowerEndRatio <= middleEndRatio && middleEndRatio <= upperEndRatio)) {
            return GradientMaterialUtils.Validation.fail("Band ratios must satisfy 0 ≤ lower ≤ middle ≤ upper ≤ 1");
        }
        return GradientMaterialUtils.Validation.ok();
    }

    private void emitFail(String message) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, message);
    }

    private void emitOk(List<BlockPlacementData> placements) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    public double getLowerEndRatio() {
        return lowerEndRatio;
    }

    public void setLowerEndRatio(double lowerEndRatio) {
        if (Double.compare(this.lowerEndRatio, lowerEndRatio) != 0) {
            this.lowerEndRatio = lowerEndRatio;
            markDirty();
        }
    }

    public double getMiddleEndRatio() {
        return middleEndRatio;
    }

    public void setMiddleEndRatio(double middleEndRatio) {
        if (Double.compare(this.middleEndRatio, middleEndRatio) != 0) {
            this.middleEndRatio = middleEndRatio;
            markDirty();
        }
    }

    public double getUpperEndRatio() {
        return upperEndRatio;
    }

    public void setUpperEndRatio(double upperEndRatio) {
        if (Double.compare(this.upperEndRatio, upperEndRatio) != 0) {
            this.upperEndRatio = upperEndRatio;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("lowerEndRatio", lowerEndRatio);
        state.put("middleEndRatio", middleEndRatio);
        state.put("upperEndRatio", upperEndRatio);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("lowerEndRatio") instanceof Number value) {
            this.lowerEndRatio = value.doubleValue();
        }
        if (map.get("middleEndRatio") instanceof Number value) {
            this.middleEndRatio = value.doubleValue();
        }
        if (map.get("upperEndRatio") instanceof Number value) {
            this.upperEndRatio = value.doubleValue();
        }
    }
}
