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
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
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

    private static final double NORMAL_EPS_SQ = 1.0e-9d;

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
        addOutputPort(new BasePort(OUTPUT_SLAB_COUNT_ID, "Slab-Classified",
            "Count of placements classified as slab by normal angle (not necessarily remapped to slab block)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STAIR_COUNT_ID, "Stair-Classified",
            "Count of placements classified as stair by normal angle (not necessarily remapped to stair block)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when normals align with placements", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Adapts block types to surface normals (blockId only). Chain with Orient Block State and Stair Shape for state.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!Double.isFinite(slabAngleDegrees) || slabAngleDegrees < 0.0d || slabAngleDegrees > 90.0d) {
            emitInvalid("Slab Angle must be finite and in [0, 90]");
            return;
        }
        if (!Double.isFinite(stairAngleDegrees) || stairAngleDegrees < 0.0d || stairAngleDegrees > 90.0d) {
            emitInvalid("Stair Angle must be finite and in [0, 90]");
            return;
        }
        if (stairAngleDegrees < slabAngleDegrees) {
            emitInvalid("Stair Angle must be >= Slab Angle");
            return;
        }
        double slabAngle = slabAngleDegrees;
        double stairAngle = stairAngleDegrees;

        MaterialMappingSupport.MappedBlockType defaultMapped =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_DEFAULT_BLOCK_ID),
                MaterialSourceResolver.isDriven(this, INPUT_DEFAULT_BLOCK_ID));
        if (!defaultMapped.valid()) {
            emitInvalid(defaultMapped.error());
            return;
        }
        MaterialMappingSupport.MappedBlockType slabMapped =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_SLAB_BLOCK_ID),
                MaterialSourceResolver.isDriven(this, INPUT_SLAB_BLOCK_ID));
        if (!slabMapped.valid()) {
            emitInvalid(slabMapped.error());
            return;
        }
        MaterialMappingSupport.MappedBlockType stairMapped =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_STAIR_BLOCK_ID),
                MaterialSourceResolver.isDriven(this, INPUT_STAIR_BLOCK_ID));
        if (!stairMapped.valid()) {
            emitInvalid(stairMapped.error());
            return;
        }

        MaterialSourceResolver.SourceResolution source =
            MaterialSourceResolver.resolve(this, SOURCE_PORTS, defaultMapped.blockId());
        if (!source.valid()) {
            emitInvalid(source.error());
            return;
        }

        if (source.kind() == MaterialSourceResolver.SourceKind.NONE) {
            emitSuccess(List.of(), 0, 0);
            return;
        }

        if ((source.kind() == MaterialSourceResolver.SourceKind.COORDINATES
            || source.kind() == MaterialSourceResolver.SourceKind.GEOMETRY)
            && defaultMapped.blockId() == null) {
            emitInvalid("Explicit default block required for geometry or coordinates input");
            return;
        }

        List<BlockPlacementData> base = source.placements();
        Object normalsObj = inputValues.get(INPUT_NORMALS_ID);
        if (!base.isEmpty() && normalsObj == null) {
            emitInvalid("Normals required");
            return;
        }

        NormalsResult normalsResult = resolveNormals(normalsObj);
        if (!normalsResult.valid()) {
            emitInvalid(normalsResult.error());
            return;
        }
        List<Vector3d> normals = normalsResult.normals();
        if (!base.isEmpty() && normals.size() != base.size()) {
            emitInvalid("Normals count must match placements count");
            return;
        }

        List<BlockPlacementData> resolved = new ArrayList<>(base.size());
        int slabCount = 0;
        int stairCount = 0;

        for (int i = 0; i < base.size(); i++) {
            BlockPlacementData placement = base.get(i);
            Vector3d normal = normals.get(i);
            MaterialChoice choice = chooseMaterial(
                normal,
                defaultMapped.blockId(),
                slabMapped.blockId(),
                stairMapped.blockId(),
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

        emitSuccess(resolved, slabCount, stairCount);
    }

    private MaterialChoice chooseMaterial(
            Vector3d normal,
            @Nullable String defaultMapped,
            @Nullable String slabMapped,
            @Nullable String stairMapped,
            double slabAngle,
            double stairAngle,
            @Nullable String sourceBlockId
    ) {
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

    private NormalsResult resolveNormals(@Nullable Object value) {
        if (value == null) {
            return NormalsResult.ok(List.of());
        }
        if (!(value instanceof List<?> list)) {
            return NormalsResult.fail("Normals must be a VECTOR_LIST");
        }
        List<Vector3d> out = new ArrayList<>(list.size());
        for (Object entry : list) {
            Vector3d normal = BlockStateValidationUtils.resolveStrictVectorListElement(entry);
            if (normal == null || normal.lengthSquared() <= NORMAL_EPS_SQ) {
                return NormalsResult.fail("Normals must be finite non-zero vectors");
            }
            out.add(normal);
        }
        return NormalsResult.ok(out);
    }

    private void emitInvalid(String message) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_SLAB_COUNT_ID, 0);
        outputValues.put(OUTPUT_STAIR_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, message);
    }

    private void emitSuccess(List<BlockPlacementData> placements, int slabCount, int stairCount) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_SLAB_COUNT_ID, slabCount);
        outputValues.put(OUTPUT_STAIR_COUNT_ID, stairCount);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    public double getSlabAngleDegrees() {
        return slabAngleDegrees;
    }

    public void setSlabAngleDegrees(double slabAngleDegrees) {
        if (Double.isFinite(slabAngleDegrees)) {
            this.slabAngleDegrees = Math.max(0.0d, Math.min(90.0d, slabAngleDegrees));
        }
    }

    public double getStairAngleDegrees() {
        return stairAngleDegrees;
    }

    public void setStairAngleDegrees(double stairAngleDegrees) {
        if (Double.isFinite(stairAngleDegrees)) {
            this.stairAngleDegrees = Math.max(0.0d, Math.min(90.0d, stairAngleDegrees));
        }
    }

    /** Contract hook: write raw angle fields without UI clamp. */
    public void forceAnglesForTest(double slabAngle, double stairAngle) {
        this.slabAngleDegrees = slabAngle;
        this.stairAngleDegrees = stairAngle;
    }

    private enum MaterialType {
        DEFAULT,
        SLAB,
        STAIR
    }

    private record MaterialChoice(MaterialType type, String blockId) {
    }

    private record NormalsResult(boolean valid, List<Vector3d> normals, String error) {
        static NormalsResult ok(List<Vector3d> normals) {
            return new NormalsResult(true, normals, "");
        }

        static NormalsResult fail(String error) {
            return new NormalsResult(false, List.of(), error);
        }
    }
}
