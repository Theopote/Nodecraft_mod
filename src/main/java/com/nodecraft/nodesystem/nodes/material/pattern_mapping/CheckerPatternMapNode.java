package com.nodecraft.nodesystem.nodes.material.pattern_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies a 3D two-material checker pattern. Remaps blockId only; preserves stateData.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.pattern_mapping.checker_pattern_map",
    displayName = "Checker Pattern Map",
    description = "Assigns alternating block types with a 3D checker (parity of relative X+Y+Z). Remaps blockId only; preserves stateData.",
    category = "material.pattern_mapping",
    order = 0
)
public class CheckerPatternMapNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_PRIMARY_ID = "input_primary";
    private static final String INPUT_SECONDARY_ID = "input_secondary";
    private static final String INPUT_PATTERN_ORIGIN_ID = "input_pattern_origin";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public CheckerPatternMapNode() {
        super(UUID.randomUUID(), "material.pattern_mapping.checker_pattern_map");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to remap (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized first when placements/coordinates are empty", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Legacy box geometry (voxelized first)", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Legacy cylinder geometry (voxelized first)", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Legacy sphere geometry (voxelized first)", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Legacy torus geometry (voxelized first)", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PRIMARY_ID, "Primary", "Primary block type for even-parity cells", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SECONDARY_ID, "Secondary", "Secondary block type for odd-parity cells", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_PATTERN_ORIGIN_ID, "Pattern Origin",
            "BLOCK_POS origin for pattern phase; missing defaults to (0,0,0)", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when pattern inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Assigns alternating block types with a 3D checker (parity of relative X+Y+Z). Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String primaryMapped = PatternMaterialUtils.optionalRole(inputValues.get(INPUT_PRIMARY_ID));
        String secondaryMapped = PatternMaterialUtils.optionalRole(inputValues.get(INPUT_SECONDARY_ID));

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
                MaterialMappingSupport.firstMappedBlockType(primaryMapped, secondaryMapped)
            );

        if (!placementSource
            && sources.isEmpty()
            && PatternMaterialUtils.hasNonPlacementSource(
                inputValues.get(INPUT_COORDINATES_ID),
                inputValues.get(INPUT_GEOMETRY_ID),
                inputValues.get(INPUT_BOX_GEOMETRY_ID),
                inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
                inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
                inputValues.get(INPUT_TORUS_GEOMETRY_ID)
            )
            && primaryMapped == null
            && secondaryMapped == null) {
            emitFail("Primary or Secondary material required for geometry or coordinates input");
            return;
        }

        BlockPos origin;
        {
            PatternMaterialUtils.OriginResult originResult =
                PatternMaterialUtils.resolveOrigin(inputValues.get(INPUT_PATTERN_ORIGIN_ID));
            if (!originResult.valid()) {
                emitFail(originResult.error());
                return;
            }
            origin = originResult.origin();
        }
        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        for (BlockPlacementData source : sources) {
            BlockPos pos = source.pos();
            if (pos == null) {
                continue;
            }
            PatternMaterialUtils.Relative rel = PatternMaterialUtils.relative(pos, origin);
            boolean primaryCell = ((rel.dx() + rel.dy() + rel.dz()) & 1) == 0;
            String mapped = primaryCell ? primaryMapped : secondaryMapped;
            String blockId = PatternMaterialUtils.pickRole(mapped, source.blockId());
            placements.add(MaterialMappingSupport.remapBlockId(source, blockId));
        }
        emitOk(placements);
    }

    private void emitFail(String message) {
        outputValues.putAll(PatternMaterialUtils.failResult(message));
    }

    private void emitOk(List<BlockPlacementData> placements) {
        outputValues.putAll(PatternMaterialUtils.okResult(placements));
    }
}
