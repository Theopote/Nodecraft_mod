package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "material.gradient_mapping.sdf_material",
    displayName = "SDF-Driven Material",
    description = "Assigns block types from a palette using sampled SDF distance values.",
    category = "material.gradient_mapping",
    order = 4
)
public class SdfDrivenMaterialNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_PALETTE_ID = "input_palette";
    private static final String INPUT_FALLBACK_BLOCK_ID = "input_fallback_block";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_HALF_WIDTH_ID = "input_half_width";

    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_WEIGHTS_ID = "output_weights";

    public SdfDrivenMaterialNode() {
        super(UUID.randomUUID(), "material.gradient_mapping.sdf_material");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to remap", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field used for material sampling", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Palette", "Ordered block id list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_FALLBACK_BLOCK_ID, "Fallback Block", "Fallback block when palette is empty", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Distance center mapped to 0.5 weight", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HALF_WIDTH_ID, "Half Width", "Half transition width used to normalize distance", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block ids aligned with positions", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "SDF-mapped placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Raw sampled SDF distances", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_WEIGHTS_ID, "Weights", "Smoothed normalized SDF weights", NodeDataType.LIST, this));
    }

    @Override
    public String getDescription() {
        return "Assigns block types from a palette using sampled SDF distance values.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            writeInvalid();
            return;
        }

        String fallback = getInputString(INPUT_FALLBACK_BLOCK_ID, "minecraft:stone");
        List<String> palette = resolvePalette(fallback);
        List<BlockPlacementData> base = resolvePlacements(fallback);

        double center = getInputDouble(INPUT_CENTER_ID, 0.0d);
        double halfWidth = Math.max(1.0e-6d, Math.abs(getInputDouble(INPUT_HALF_WIDTH_ID, 1.0d)));

        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(base.size());
        List<BlockPlacementData> placements = new ArrayList<>(base.size());
        List<Double> distances = new ArrayList<>(base.size());
        List<Double> weights = new ArrayList<>(base.size());

        for (BlockPlacementData placement : base) {
            if (placement.pos() == null) {
                continue;
            }
            Vector3d sample = new Vector3d(
                placement.pos().getX() + 0.5d,
                placement.pos().getY() + 0.5d,
                placement.pos().getZ() + 0.5d
            );
            double distance = sdf.sampleDistance(sample);
            double x = (distance - center) / halfWidth;
            double weight = smoothstep01(0.5d + 0.5d * x);
            int index = Math.min(palette.size() - 1, Math.max(0, (int) Math.floor(weight * palette.size())));
            String selected = palette.get(index);
            String blockId = (selected == null || selected.isBlank()) ? fallback : selected;

            positions.add(placement.pos());
            blockIds.add(blockId);
            placements.add(new BlockPlacementData(placement.pos(), blockId, placement.stateData()));
            distances.add(distance);
            weights.add(weight);
        }

        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_WEIGHTS_ID, List.copyOf(weights));
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POSITIONS_ID, new BlockPosList());
        outputValues.put(OUTPUT_BLOCK_IDS_ID, List.of());
        outputValues.put(OUTPUT_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_DISTANCES_ID, List.of());
        outputValues.put(OUTPUT_WEIGHTS_ID, List.of());
    }

    private List<BlockPlacementData> resolvePlacements(String fallbackBlockId) {
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (placementsObj instanceof List<?> placementList && !placementList.isEmpty()) {
            List<BlockPlacementData> resolved = new ArrayList<>();
            for (Object entry : placementList) {
                if (entry instanceof BlockPlacementData placement && placement.pos() != null) {
                    resolved.add(placement);
                }
            }
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }

        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            true
        );

        List<BlockPlacementData> generated = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            generated.add(new BlockPlacementData(pos, fallbackBlockId));
        }
        return generated;
    }

    private List<String> resolvePalette(String fallback) {
        Object paletteObj = inputValues.get(INPUT_PALETTE_ID);
        List<String> palette = new ArrayList<>();
        if (paletteObj instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String blockId && !blockId.isBlank()) {
                    palette.add(blockId);
                }
            }
        }
        if (palette.isEmpty()) {
            palette.add(fallback);
        }
        return palette;
    }

    private double smoothstep01(double value) {
        double t = Math.max(0.0d, Math.min(1.0d, value));
        return t * t * (3.0d - 2.0d * t);
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
