package com.nodecraft.nodesystem.nodes.material.surface_aging;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.GradientMaterialUtils;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
import com.nodecraft.nodesystem.util.MaterialSpatialUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.surface_aging.moss_growth",
    displayName = "Moss Growth",
    description = "Applies moss to upward-exposed (top-only) voxels with a deterministic RandomOps mask. Remaps blockId only; preserves stateData.",
    category = "material.surface_aging",
    order = 1
)
public class MossGrowthNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BASE_ID = "input_base";
    private static final String INPUT_MOSS_ID = "input_moss";
    private static final String INPUT_AMOUNT_ID = "input_amount";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_AGING_ORIGIN_ID = "input_aging_origin";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_AFFECTED_COUNT_ID = "output_affected_count";
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

    public MossGrowthNode() {
        super(UUID.randomUUID(), "material.surface_aging.moss_growth");
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to moss (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized when no higher-precedence source is driven", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BASE_ID, "Base Block",
            "Required geometry/coords voxelization base when placements are empty", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_MOSS_ID, "Moss Block", "Moss material for aged top-exposed cells", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_AMOUNT_ID, "Amount", "Moss ratio in [0, 1] among top-exposed voxels", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Integer seed for deterministic moss mask", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_AGING_ORIGIN_ID, "Aging Origin",
            "BLOCK_POS origin for aging phase; undriven defaults to (0,0,0)", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_AFFECTED_COUNT_ID, "Affected Count",
            "Voxels where aging mapping ran (mask passed and Moss Block set); not necessarily a blockId change", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when amount/origin and inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Applies moss to upward-exposed (top-only) voxels with a deterministic RandomOps mask. Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GradientMaterialUtils.OptionalDoubleResult amountResult =
            SurfaceAgingUtils.resolveAmount(this, INPUT_AMOUNT_ID, 1.0d, "Amount");
        if (!amountResult.valid()) {
            emitFail(amountResult.error());
            return;
        }
        SurfaceAgingUtils.Validation amountOk = SurfaceAgingUtils.requireAmount01(amountResult.value());
        if (!amountOk.valid()) {
            emitFail(amountOk.message());
            return;
        }
        double amount = amountResult.value();

        SurfaceAgingUtils.OriginResult originResult =
            SurfaceAgingUtils.resolveAgingOrigin(this, INPUT_AGING_ORIGIN_ID);
        if (!originResult.valid()) {
            emitFail(originResult.error());
            return;
        }
        BlockPos origin = originResult.origin();

        MaterialMappingSupport.MappedBlockType base =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_BASE_ID),
                MaterialSourceResolver.isDriven(this, INPUT_BASE_ID));
        if (!base.valid()) {
            emitFail(base.error());
            return;
        }
        MaterialMappingSupport.MappedBlockType moss =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_MOSS_ID),
                MaterialSourceResolver.isDriven(this, INPUT_MOSS_ID));
        if (!moss.valid()) {
            emitFail(moss.error());
            return;
        }
        String mossMapped = moss.blockId();

        MaterialSourceResolver.SourceResolution source =
            MaterialSourceResolver.resolve(this, SOURCE_PORTS, base.blockId());
        if (!source.valid()) {
            emitFail(source.error());
            return;
        }
        if (source.kind() == MaterialSourceResolver.SourceKind.NONE) {
            emitOk(List.of(), 0);
            return;
        }
        if ((source.kind() == MaterialSourceResolver.SourceKind.COORDINATES
            || source.kind() == MaterialSourceResolver.SourceKind.GEOMETRY)
            && base.blockId() == null) {
            emitFail("Base Block required for geometry or coordinates input");
            return;
        }

        List<BlockPlacementData> sources = source.placements();
        SurfaceAgingUtils.Validation uniqueOk = SurfaceAgingUtils.requireUniquePositions(sources);
        if (!uniqueOk.valid()) {
            emitFail(uniqueOk.message());
            return;
        }

        int seed = RandomOps.resolveSeed(inputValues.get(INPUT_SEED_ID));
        Set<BlockPos> occupancy = SurfaceAgingUtils.buildOccupancy(sources);
        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        int affected = 0;

        for (BlockPlacementData sourcePlacement : sources) {
            BlockPos pos = sourcePlacement.pos();
            if (pos == null) {
                continue;
            }
            if (!SurfaceAgingUtils.isTopExposed(pos, occupancy)) {
                MaterialMappingSupport.RemapResult passthrough =
                    MaterialMappingSupport.remapValidated(sourcePlacement, sourcePlacement.blockId());
                if (!passthrough.valid()) {
                    emitFail(passthrough.error());
                    return;
                }
                placements.add(passthrough.placement());
                continue;
            }
            MaterialSpatialUtils.Relative rel = MaterialSpatialUtils.relative(pos, origin);
            SurfaceAgingUtils.SampleResult sample = SurfaceAgingUtils.agingSample(rel, seed);
            if (!sample.valid()) {
                emitFail(sample.error());
                return;
            }
            boolean age = SurfaceAgingUtils.shouldAge(sample.sample(), amount);
            String mapped = age ? mossMapped : null;
            String blockId = SurfaceAgingUtils.pickRole(mapped, sourcePlacement.blockId());
            if (age && mossMapped != null && !mossMapped.isBlank()) {
                affected++;
            }
            MaterialMappingSupport.RemapResult remap =
                MaterialMappingSupport.remapValidated(sourcePlacement, blockId);
            if (!remap.valid()) {
                emitFail(remap.error());
                return;
            }
            placements.add(remap.placement());
        }
        emitOk(placements, affected);
    }

    private void emitFail(String message) {
        outputValues.putAll(SurfaceAgingUtils.failResult(message));
    }

    private void emitOk(List<BlockPlacementData> placements, int affected) {
        outputValues.putAll(SurfaceAgingUtils.okResult(placements, affected));
    }
}
