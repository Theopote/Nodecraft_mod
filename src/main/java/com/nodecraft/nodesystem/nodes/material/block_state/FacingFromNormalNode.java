package com.nodecraft.nodesystem.nodes.material.block_state;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
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
    id = "material.block_state.facing_from_normal",
    displayName = "Facing From Normal",
    description = "Converts normal vectors to nearest facing direction and optionally writes facing state to placements.",
    category = "material.block_state",
    order = 4
)
public class FacingFromNormalNode extends BaseNode {

    private static final String INPUT_NORMAL_ID = "input_normal";
    private static final String INPUT_NORMALS_ID = "input_normals";
    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";

    private static final String OUTPUT_FACING_ID = "output_facing";
    private static final String OUTPUT_FACINGS_ID = "output_facings";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";

    public FacingFromNormalNode() {
        super(UUID.randomUUID(), "material.block_state.facing_from_normal");

        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Single normal vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_NORMALS_ID, "Normals", "List of normal vectors", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional placements to annotate facing state", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Fallback block type when generating placements", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_FACING_ID, "Facing", "Facing derived from single normal", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_FACINGS_ID, "Facings", "Facing list derived from normals", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Placements with facing state", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with positions", NodeDataType.BLOCK_INFO_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Converts normal vectors to nearest facing direction and optionally writes facing state to placements.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d single = resolveDirection(inputValues.get(INPUT_NORMAL_ID));
        String singleFacing = single != null ? resolveFacing(single).asString() : "north";

        List<String> facings = resolveFacingList(inputValues.get(INPUT_NORMALS_ID));
        if (facings.isEmpty()) {
            facings = List.of(singleFacing);
        }

        List<BlockPlacementData> base = resolvePlacements(getInputString(INPUT_BLOCK_TYPE_ID, "minecraft:stone"));
        List<BlockPlacementData> resolved = new ArrayList<>(base.size());
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(base.size());

        for (int i = 0; i < base.size(); i++) {
            BlockPlacementData placement = base.get(i);
            if (placement.pos() == null || placement.blockId() == null || placement.blockId().isBlank()) {
                continue;
            }
            String facing = facings.get(Math.min(i, facings.size() - 1));
            BlockStateData state = placement.stateData() != null ? placement.stateData() : new BlockStateData();
            state.setProperty("facing", facing);

            resolved.add(new BlockPlacementData(placement.pos(), placement.blockId(), state));
            positions.add(placement.pos());
            blockIds.add(placement.blockId());
        }

        outputValues.put(OUTPUT_FACING_ID, singleFacing);
        outputValues.put(OUTPUT_FACINGS_ID, List.copyOf(facings));
        outputValues.put(OUTPUT_PLACEMENTS_ID, resolved);
        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
    }

    private List<String> resolveFacingList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                Vector3d v = resolveDirection(entry);
                if (v != null) {
                    out.add(resolveFacing(v).asString());
                }
            }
        }
        return out;
    }

    private Direction resolveFacing(Vector3d direction) {
        if (direction.lengthSquared() <= 1.0e-9d) {
            return Direction.NORTH;
        }
        double absX = Math.abs(direction.x);
        double absY = Math.abs(direction.y);
        double absZ = Math.abs(direction.z);
        if (absX >= absY && absX >= absZ) {
            return direction.x >= 0.0d ? Direction.EAST : Direction.WEST;
        }
        if (absY >= absX && absY >= absZ) {
            return direction.y >= 0.0d ? Direction.UP : Direction.DOWN;
        }
        return direction.z >= 0.0d ? Direction.SOUTH : Direction.NORTH;
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
}
