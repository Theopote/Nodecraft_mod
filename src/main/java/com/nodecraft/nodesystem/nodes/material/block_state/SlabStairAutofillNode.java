package com.nodecraft.nodesystem.nodes.material.block_state;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "material.block_state.slab_autofill",
    displayName = "Slab / Stair Auto-Fill",
    description = "Generates slab or stair placements from normals to smooth stepped transitions.",
    category = "material.block_state",
    order = 5
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

    public SlabStairAutofillNode() {
        super(UUID.randomUUID(), "material.block_state.slab_autofill");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_NORMALS_ID, "Normals", "Normal vectors aligned with placements", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_DEFAULT_BLOCK_ID, "Default Block", "Fallback full block id", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SLAB_BLOCK_ID, "Slab Block", "Slab block id", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_STAIR_BLOCK_ID, "Stair Block", "Stair block id", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Auto-filled placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with positions", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLAB_COUNT_ID, "Slab Count", "Number of slab placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STAIR_COUNT_ID, "Stair Count", "Number of stair placements", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Generates slab or stair placements from normals to smooth stepped transitions.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String defaultBlock = getInputString(INPUT_DEFAULT_BLOCK_ID, "minecraft:stone");
        String slabBlock = getInputString(INPUT_SLAB_BLOCK_ID, "minecraft:stone_slab");
        String stairBlock = getInputString(INPUT_STAIR_BLOCK_ID, "minecraft:stone_stairs");
        List<BlockPlacementData> base = resolvePlacements(defaultBlock);
        List<Vector3d> normals = resolveNormals(inputValues.get(INPUT_NORMALS_ID));

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
            MaterialChoice choice = chooseMaterial(normal, defaultBlock, slabBlock, stairBlock);

            BlockStateData state = placement.stateData() != null ? placement.stateData() : new BlockStateData();
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
            positions.add(placement.pos());
            blockIds.add(choice.blockId);
        }

        outputValues.put(OUTPUT_PLACEMENTS_ID, resolved);
        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_SLAB_COUNT_ID, slabCount);
        outputValues.put(OUTPUT_STAIR_COUNT_ID, stairCount);
    }

    private MaterialChoice chooseMaterial(@Nullable Vector3d normal, String defaultBlock, String slabBlock, String stairBlock) {
        if (normal == null || normal.lengthSquared() <= 1.0e-9d) {
            return new MaterialChoice(MaterialType.DEFAULT, defaultBlock, null);
        }
        Vector3d n = new Vector3d(normal).normalize();
        double horizontal = Math.sqrt(n.x * n.x + n.z * n.z);
        if (horizontal >= Math.max(0.0d, slopeThreshold)) {
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

    private List<Vector3d> resolveNormals(Object value) {
        List<Vector3d> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                Vector3d normal = resolveDirection(entry);
                if (normal != null) {
                    out.add(normal);
                }
            }
        }
        return out;
    }

    private @Nullable Vector3d resolveDirection(@Nullable Object value) {
        if (value instanceof Vector3d vector) return new Vector3d(vector);
        if (value instanceof PointData point) return point.getPosition();
        if (value instanceof Vec3d vector) return new Vector3d(vector.x, vector.y, vector.z);
        if (value instanceof Coordinate coordinate) return new Vector3d(coordinate.getX(), coordinate.getY(), coordinate.getZ());
        if (value instanceof BlockPos pos) return new Vector3d(pos.getX(), pos.getY(), pos.getZ());
        return null;
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
