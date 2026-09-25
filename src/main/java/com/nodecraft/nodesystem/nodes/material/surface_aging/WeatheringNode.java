package com.nodecraft.nodesystem.nodes.material.surface_aging;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Applies deterministic surface weathering (topology-eligible voxels only).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.surface_aging.weathering",
    displayName = "Weathering",
    description = "Ages exposed surface voxels with a deterministic RandomOps mask. Remaps blockId only; preserves stateData.",
    category = "material.surface_aging",
    order = 0
)
public class WeatheringNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BASE_BLOCK_ID = "input_base_block";
    private static final String INPUT_AGED_BLOCK_ID = "input_aged_block";
    private static final String INPUT_AMOUNT_ID = "input_amount";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_AGING_ORIGIN_ID = "input_aging_origin";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_AFFECTED_COUNT_ID = "output_affected_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public WeatheringNode() {
        super(UUID.randomUUID(), "material.surface_aging.weathering");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to age (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized first when placements/coordinates are empty", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BASE_BLOCK_ID, "Base Block",
            "Geometry/coords voxelization base when placements are empty", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_AGED_BLOCK_ID, "Aged Block", "Block type used for weathered surface cells", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_AMOUNT_ID, "Amount", "Weathering ratio in [0, 1]", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Integer seed for deterministic weathering", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_AGING_ORIGIN_ID, "Aging Origin",
            "BLOCK_POS origin for aging phase; missing defaults to (0,0,0)", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_AFFECTED_COUNT_ID, "Affected Count", "Number of voxels remapped to Aged Block", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when amount/origin and inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Ages exposed surface voxels with a deterministic RandomOps mask. Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double amount = readAmount(0.2d);
        SurfaceAgingUtils.Validation amountOk = SurfaceAgingUtils.requireAmount01(amount);
        if (!amountOk.valid()) {
            emitFail(amountOk.message());
            return;
        }

        SurfaceAgingUtils.OriginResult originResult =
            SurfaceAgingUtils.resolveAgingOrigin(inputValues.get(INPUT_AGING_ORIGIN_ID));
        if (!originResult.valid()) {
            emitFail(originResult.error());
            return;
        }
        BlockPos origin = originResult.origin();

        String baseMapped = SurfaceAgingUtils.optionalRole(inputValues.get(INPUT_BASE_BLOCK_ID));
        String agedMapped = SurfaceAgingUtils.optionalRole(inputValues.get(INPUT_AGED_BLOCK_ID));

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
                MaterialMappingSupport.firstMappedBlockType(baseMapped, agedMapped)
            );

        if (!placementSource
            && sources.isEmpty()
            && SurfaceAgingUtils.hasNonPlacementSource(
                inputValues.get(INPUT_COORDINATES_ID),
                inputValues.get(INPUT_GEOMETRY_ID),
                inputValues.get(INPUT_BOX_GEOMETRY_ID),
                inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
                inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
                inputValues.get(INPUT_TORUS_GEOMETRY_ID)
            )
            && baseMapped == null
            && agedMapped == null) {
            emitFail("Base Block or Aged Block required for geometry or coordinates input");
            return;
        }

        int seed = RandomOps.resolveSeed(inputValues.get(INPUT_SEED_ID));
        Set<BlockPos> occupancy = SurfaceAgingUtils.buildOccupancy(sources);
        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        int affected = 0;

        for (BlockPlacementData source : sources) {
            BlockPos pos = source.pos();
            if (pos == null) {
                continue;
            }
            if (!SurfaceAgingUtils.isSurface(pos, occupancy)) {
                placements.add(MaterialMappingSupport.remapBlockId(source, source.blockId()));
                continue;
            }
            SurfaceAgingUtils.SampleResult sample = SurfaceAgingUtils.agingSample(
                SurfaceAgingUtils.relativeX(pos, origin),
                SurfaceAgingUtils.relativeY(pos, origin),
                SurfaceAgingUtils.relativeZ(pos, origin),
                seed
            );
            if (!sample.valid()) {
                emitFail(sample.error());
                return;
            }
            boolean age = SurfaceAgingUtils.shouldAge(sample.sample(), amount);
            String mapped = age ? agedMapped : null;
            String blockId = SurfaceAgingUtils.pickRole(mapped, source.blockId());
            if (age && agedMapped != null && !agedMapped.isBlank()) {
                affected++;
            }
            placements.add(MaterialMappingSupport.remapBlockId(source, blockId));
        }
        emitOk(placements, affected);
    }

    private double readAmount(double fallback) {
        Object value = inputValues.get(INPUT_AMOUNT_ID);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private void emitFail(String message) {
        outputValues.putAll(SurfaceAgingUtils.failResult(message));
    }

    private void emitOk(List<BlockPlacementData> placements, int affected) {
        outputValues.putAll(SurfaceAgingUtils.okResult(placements, affected));
    }
}
