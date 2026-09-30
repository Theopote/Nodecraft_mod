package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
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

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_WEIGHTS_ID = "output_weights";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private static final MaterialSourceResolver.SourcePorts SOURCE_PORTS = new MaterialSourceResolver.SourcePorts(
        null,
        null,
        INPUT_PLACEMENTS_ID,
        INPUT_COORDINATES_ID,
        INPUT_GEOMETRY_ID,
        INPUT_BOX_GEOMETRY_ID,
        INPUT_CYLINDER_GEOMETRY_ID,
        INPUT_SPHERE_GEOMETRY_ID,
        INPUT_TORUS_GEOMETRY_ID
    );

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
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Palette", "Typed block palette (BLOCK_PALETTE)", NodeDataType.BLOCK_PALETTE, this));
        addInputPort(new BasePort(INPUT_FALLBACK_BLOCK_ID, "Fallback Block", "Geometry voxelization base when palette is empty", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Distance center mapped to 0.5 weight", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HALF_WIDTH_ID, "Half Width", "Half transition width used to normalize distance", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "SDF-mapped placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Raw sampled SDF distances", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_WEIGHTS_ID, "Weights", "Smoothed normalized SDF weights", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when SDF and half-width are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Assigns block types from a palette using sampled SDF distance values.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            emitFail("SDF input is required");
            return;
        }

        GradientMaterialUtils.OptionalDoubleResult centerResult =
            GradientMaterialUtils.resolveOptionalStrictDouble(this, INPUT_CENTER_ID, 0.0d, "Center");
        if (!centerResult.valid()) {
            emitFail(centerResult.error());
            return;
        }
        GradientMaterialUtils.OptionalDoubleResult halfWidthResult =
            GradientMaterialUtils.resolveOptionalStrictDouble(this, INPUT_HALF_WIDTH_ID, 1.0d, "Half Width");
        if (!halfWidthResult.valid()) {
            emitFail(halfWidthResult.error());
            return;
        }
        double center = centerResult.value();
        double halfWidth = halfWidthResult.value();
        GradientMaterialUtils.Validation halfWidthOk = GradientMaterialUtils.requirePositive(halfWidth, "Half Width");
        if (!halfWidthOk.valid()) {
            emitFail(halfWidthOk.message());
            return;
        }

        BlockPaletteData palette = GradientMaterialUtils.resolvePalette(inputValues.get(INPUT_PALETTE_ID));
        String fallbackMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_FALLBACK_BLOCK_ID));
        String fallback = MaterialMappingSupport.firstMappedBlockType(
            fallbackMapped,
            palette.isEmpty() ? null : palette.entries().getFirst().blockId()
        );

        MaterialSourceResolver.SourceResolution source =
            MaterialSourceResolver.resolve(this, SOURCE_PORTS, fallback);
        if (!source.valid()) {
            emitFail(source.error());
            return;
        }
        if (source.kind() == MaterialSourceResolver.SourceKind.NONE) {
            emitOk(List.of(), List.of(), List.of());
            return;
        }
        if ((source.kind() == MaterialSourceResolver.SourceKind.COORDINATES
            || source.kind() == MaterialSourceResolver.SourceKind.GEOMETRY)
            && fallback == null) {
            emitFail("Palette or fallback block required for geometry or coordinates input");
            return;
        }

        List<BlockPlacementData> sources = source.placements();
        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        List<Double> distances = new ArrayList<>(sources.size());
        List<Double> weights = new ArrayList<>(sources.size());

        for (BlockPlacementData sourcePlacement : sources) {
            BlockPos pos = sourcePlacement.pos();
            Vector3d sample = new Vector3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);
            double distance = sdf.sampleDistance(sample);
            if (!Double.isFinite(distance)) {
                emitFail("SDF sample produced a non-finite value");
                return;
            }
            double x = (distance - center) / halfWidth;
            double weight = smoothstep01(0.5d + 0.5d * x);
            if (!Double.isFinite(weight)) {
                emitFail("SDF weight produced a non-finite value");
                return;
            }
            String blockId = GradientMaterialUtils.pickByNormalized(palette, weight, sourcePlacement.blockId());
            placements.add(MaterialMappingSupport.remapBlockId(sourcePlacement, blockId));
            distances.add(distance);
            weights.add(weight);
        }

        emitOk(placements, distances, weights);
    }

    private void emitFail(String message) {
        outputValues.putAll(GradientMaterialUtils.failResult(message, OUTPUT_DISTANCES_ID, OUTPUT_WEIGHTS_ID));
    }

    private void emitOk(List<BlockPlacementData> placements, List<Double> distances, List<Double> weights) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_WEIGHTS_ID, List.copyOf(weights));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private static double smoothstep01(double value) {
        double t = Math.max(0.0d, Math.min(1.0d, value));
        return t * t * (3.0d - 2.0d * t);
    }
}
