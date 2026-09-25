package com.nodecraft.nodesystem.nodes.material.directional_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.material.block_state.BlockStateValidationUtils;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.directional_mapping.slab_stair_autofill",
    displayName = "Slab / Stair Adapt",
    description = "Adapts block types to surface normals (blockId only). Use Orient Block State and Stair Shape for state properties.",
    category = "material.directional_mapping",
    order = 10
)
public class SlabStairAutofillNode extends BaseNode {

    @NodeProperty(
        displayName = "Slab Angle",
        category = "Classification",
        order = 1,
        description = "Maximum angle from vertical (degrees) for full-block classification"
    )
    private double slabAngleDegrees = 20.0d;

    @NodeProperty(
        displayName = "Stair Angle",
        category = "Classification",
        order = 2,
        description = "Minimum angle from vertical (degrees) for stair classification"
    )
    private double stairAngleDegrees = 35.0d;

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_NORMALS_ID = "input_normals";
    private static final String INPUT_DEFAULT_BLOCK_ID = "input_default_block";
    private static final String INPUT_SLAB_BLOCK_ID = "input_slab_block";
    private static final String INPUT_STAIR_BLOCK_ID = "input_stair_block";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_SLAB_COUNT_ID = "output_slab_count";
    private static final String OUTPUT_STAIR_COUNT_ID = "output_stair_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public SlabStairAutofillNode() {
        super(UUID.randomUUID(), "material.directional_mapping.slab_stair_autofill");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Incoming placements to adapt by normal", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_NORMALS_ID, "Normals", "Normal vectors index-aligned with placements", NodeDataType.VECTOR_LIST, this));
        addInputPort(new BasePort(INPUT_DEFAULT_BLOCK_ID, "Default Block", "Full block id for near-vertical normals", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SLAB_BLOCK_ID, "Slab Block", "Slab block id for moderate slopes", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_STAIR_BLOCK_ID, "Stair Block", "Stair block id for steep slopes", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Adapted placements (blockId only)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLAB_COUNT_ID, "Slab Count", "Number of slab placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STAIR_COUNT_ID, "Stair Count", "Number of stair placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when normals align with placements", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Adapts block types to surface normals (blockId only). Chain with Orient Block State and Stair Shape for state.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double slabAngle = resolveAngle(slabAngleDegrees, 20.0d);
        double stairAngle = resolveAngle(stairAngleDegrees, 35.0d);
        if (stairAngle < slabAngle) {
            stairAngle = slabAngle;
        }

        String defaultMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_DEFAULT_BLOCK_ID));
        String slabMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_SLAB_BLOCK_ID));
        String stairMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_STAIR_BLOCK_ID));

        List<BlockPlacementData> fromPlacements = MaterialMappingSupport.extractPlacements(inputValues.get(INPUT_PLACEMENTS_ID));
        boolean placementSource = !fromPlacements.isEmpty();

        List<BlockPlacementData> base = placementSource
            ? fromPlacements
            : MaterialMappingSupport.resolveSourcePlacements(
                null,
                inputValues.get(INPUT_COORDINATES_ID),
                inputValues.get(INPUT_GEOMETRY_ID),
                inputValues.get(INPUT_BOX_GEOMETRY_ID),
                inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
                inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
                inputValues.get(INPUT_TORUS_GEOMETRY_ID),
                defaultMapped
            );

        if (!placementSource && base.isEmpty() && hasNonPlacementSource()) {
            emitInvalid("Explicit default block required for geometry or coordinates input");
            return;
        }

        Object normalsObj = inputValues.get(INPUT_NORMALS_ID);
        if (!base.isEmpty() && normalsObj == null) {
            emitInvalid("Normals required");
            return;
        }

        List<Vector3d> normals = resolveNormals(normalsObj);
        if (!base.isEmpty()) {
            if (normals.isEmpty() && normalsObj instanceof List<?> list && !list.isEmpty()) {
                emitInvalid("Normals must be a homogeneous VECTOR_LIST");
                return;
            }
            if (normals.size() != base.size()) {
                emitInvalid("Normals count must match placements count");
                return;
            }
        }

        List<BlockPlacementData> resolved = new ArrayList<>(base.size());
        int slabCount = 0;
        int stairCount = 0;

        for (int i = 0; i < base.size(); i++) {
            BlockPlacementData placement = base.get(i);
            if (placement.pos() == null) {
                continue;
            }
            Vector3d normal = i < normals.size() ? normals.get(i) : null;
            MaterialChoice choice = chooseMaterial(
                normal,
                defaultMapped,
                slabMapped,
                stairMapped,
                slabAngle,
                stairAngle,
                placement.blockId()
            );

            if (choice.type == MaterialType.SLAB) {
                slabCount++;
            } else if (choice.type == MaterialType.STAIR) {
                stairCount++;
            }

            resolved.add(MaterialMappingSupport.remapBlockId(placement, choice.blockId()));
        }

        outputValues.put(OUTPUT_PLACEMENTS_ID, resolved);
        outputValues.put(OUTPUT_SLAB_COUNT_ID, slabCount);
        outputValues.put(OUTPUT_STAIR_COUNT_ID, stairCount);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private double resolveAngle(double value, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.0d, Math.min(90.0d, value));
    }

    private MaterialChoice chooseMaterial(
            @Nullable Vector3d normal,
            @Nullable String defaultMapped,
            @Nullable String slabMapped,
            @Nullable String stairMapped,
            double slabAngle,
            double stairAngle,
            @Nullable String sourceBlockId
    ) {
        if (normal == null || normal.lengthSquared() <= 1.0e-9d) {
            String blockId = MaterialMappingSupport.resolveMaterialTarget(defaultMapped, sourceBlockId);
            return new MaterialChoice(MaterialType.DEFAULT, blockId);
        }

        Vector3d n = new Vector3d(normal).normalize();
        double angleFromVertical = Math.toDegrees(Math.acos(Math.min(1.0d, Math.abs(n.y))));

        if (angleFromVertical > stairAngle) {
            return new MaterialChoice(
                MaterialType.STAIR,
                MaterialMappingSupport.resolveMaterialTarget(stairMapped, sourceBlockId)
            );
        }
        if (angleFromVertical > slabAngle) {
            return new MaterialChoice(
                MaterialType.SLAB,
                MaterialMappingSupport.resolveMaterialTarget(slabMapped, sourceBlockId)
            );
        }
        return new MaterialChoice(
            MaterialType.DEFAULT,
            MaterialMappingSupport.resolveMaterialTarget(defaultMapped, sourceBlockId)
        );
    }

    private List<Vector3d> resolveNormals(@Nullable Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Vector3d> out = new ArrayList<>(list.size());
        for (Object entry : list) {
            Vector3d normal = BlockStateValidationUtils.resolveStrictVectorListElement(entry);
            if (normal == null) {
                return List.of();
            }
            out.add(normal);
        }
        return out;
    }

    private boolean hasNonPlacementSource() {
        return inputValues.get(INPUT_COORDINATES_ID) != null
            || inputValues.get(INPUT_GEOMETRY_ID) != null
            || inputValues.get(INPUT_BOX_GEOMETRY_ID) != null
            || inputValues.get(INPUT_CYLINDER_GEOMETRY_ID) != null
            || inputValues.get(INPUT_SPHERE_GEOMETRY_ID) != null
            || inputValues.get(INPUT_TORUS_GEOMETRY_ID) != null;
    }

    private void emitInvalid(String message) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_SLAB_COUNT_ID, 0);
        outputValues.put(OUTPUT_STAIR_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, message);
    }

    private enum MaterialType {
        DEFAULT,
        SLAB,
        STAIR
    }

    private record MaterialChoice(MaterialType type, String blockId) {
    }
}
