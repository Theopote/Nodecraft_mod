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
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.directional_mapping.slab_stair_autofill",
    displayName = "Slab / Stair Auto-Fill",
    description = "Adapts block types to surface normals for slab or stair transitions (remaps blockId only)",
    category = "material.directional_mapping",
    order = 10
)
public class SlabStairAutofillNode extends BaseNode {

    @NodeProperty(displayName = "Slope Threshold", category = "Auto Fill", order = 1)
    private double slopeThreshold = 0.35d;

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
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_SLAB_COUNT_ID = "output_slab_count";
    private static final String OUTPUT_STAIR_COUNT_ID = "output_stair_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public SlabStairAutofillNode() {
        super(UUID.randomUUID(), "material.directional_mapping.slab_stair_autofill");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_NORMALS_ID, "Normals", "Normal vectors index-aligned with placements", NodeDataType.VECTOR_LIST, this));
        addInputPort(new BasePort(INPUT_DEFAULT_BLOCK_ID, "Default Block", "Fallback full block id", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SLAB_BLOCK_ID, "Slab Block", "Slab block id", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_STAIR_BLOCK_ID, "Stair Block", "Stair block id", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Auto-filled placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with positions", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLAB_COUNT_ID, "Slab Count", "Number of slab placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STAIR_COUNT_ID, "Stair Count", "Number of stair placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when normals align with placements", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Adapts block types to surface normals for slab or stair transitions";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double threshold = resolveSlopeThreshold();
        String defaultBlock = getInputString(INPUT_DEFAULT_BLOCK_ID, "minecraft:stone");
        String slabBlock = getInputString(INPUT_SLAB_BLOCK_ID, "minecraft:stone_slab");
        String stairBlock = getInputString(INPUT_STAIR_BLOCK_ID, "minecraft:stone_stairs");
        List<BlockPlacementData> base = resolvePlacements(defaultBlock);

        Object normalsObj = inputValues.get(INPUT_NORMALS_ID);
        List<Vector3d> normals = resolveNormals(normalsObj);
        if (normalsObj != null && !base.isEmpty() && normals.size() != base.size()) {
            outputValues.put(OUTPUT_PLACEMENTS_ID, List.of());
            outputValues.put(OUTPUT_POSITIONS_ID, new BlockPosList());
            outputValues.put(OUTPUT_BLOCK_IDS_ID, List.of());
            outputValues.put(OUTPUT_SLAB_COUNT_ID, 0);
            outputValues.put(OUTPUT_STAIR_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Normals count must match placements count");
            return;
        }

        List<BlockPlacementData> resolved = new ArrayList<>(base.size());
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(base.size());
        int slabCount = 0;
        int stairCount = 0;

        for (int i = 0; i < base.size(); i++) {
            BlockPlacementData placement = base.get(i);
            if (placement.pos() == null) {
                continue;
            }
            Vector3d normal = i < normals.size() ? normals.get(i) : null;
            MaterialChoice choice = chooseMaterial(normal, defaultBlock, slabBlock, stairBlock, threshold);

            BlockStateData state = placement.stateData() != null ? Objects.requireNonNull(placement.stateData()).copy() : new BlockStateData();
            if (choice.type == MaterialType.SLAB) {
                state.setProperty("type", choice.normal != null && choice.normal.y > 0.0d ? "bottom" : "top");
                slabCount++;
            } else if (choice.type == MaterialType.STAIR) {
                state.setProperty("half", choice.normal != null && choice.normal.y > 0.0d ? "bottom" : "top");
                state.setProperty("shape", "straight");
                state.setProperty("facing", resolveHorizontalFacing(choice.normal).asString());
                stairCount++;
            }

            resolved.add(new BlockPlacementData(placement.pos(), choice.blockId, state));
            positions.add(Objects.requireNonNull(placement.pos()));
            blockIds.add(choice.blockId);
        }

        outputValues.put(OUTPUT_PLACEMENTS_ID, resolved);
        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_SLAB_COUNT_ID, slabCount);
        outputValues.put(OUTPUT_STAIR_COUNT_ID, stairCount);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private double resolveSlopeThreshold() {
        if (!Double.isFinite(slopeThreshold)) {
            return 0.35d;
        }
        return Math.max(0.0d, Math.min(1.0d, slopeThreshold));
    }

    private MaterialChoice chooseMaterial(@Nullable Vector3d normal, String defaultBlock, String slabBlock, String stairBlock, double threshold) {
        if (normal == null || normal.lengthSquared() <= 1.0e-9d) {
            return new MaterialChoice(MaterialType.DEFAULT, defaultBlock, null);
        }
        Vector3d n = new Vector3d(normal).normalize();
        double horizontal = Math.sqrt(n.x * n.x + n.z * n.z);
        if (horizontal >= threshold) {
            return new MaterialChoice(MaterialType.STAIR, stairBlock, n);
        }
        if (Math.abs(n.y) < 0.999d) {
            return new MaterialChoice(MaterialType.SLAB, slabBlock, n);
        }
        return new MaterialChoice(MaterialType.DEFAULT, defaultBlock, n);
    }

    private Direction resolveHorizontalFacing(@Nullable Vector3d normal) {
        if (normal == null) {
            return Direction.NORTH;
        }
        double absX = Math.abs(normal.x);
        double absZ = Math.abs(normal.z);
        if (absX >= absZ) {
            return normal.x >= 0.0d ? Direction.EAST : Direction.WEST;
        }
        return normal.z >= 0.0d ? Direction.SOUTH : Direction.NORTH;
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

    private List<BlockPlacementData> resolvePlacements(String fallbackBlockId) {
        List<BlockPlacementData> fromPlacements = MaterialMappingSupport.extractPlacements(inputValues.get(INPUT_PLACEMENTS_ID));
        if (!fromPlacements.isEmpty()) {
            return fromPlacements;
        }

        return MaterialMappingSupport.resolveSourcePlacements(
            null,
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            fallbackBlockId
        );
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private enum MaterialType {
        DEFAULT,
        SLAB,
        STAIR
    }

    private record MaterialChoice(MaterialType type, String blockId, @Nullable Vector3d normal) {
    }
}
