package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies a single block type uniformly to placements, coordinates, or voxelized geometry.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.basic_assignment.assign_block_type",
    displayName = "Assign Block Type",
    description = "Assigns a single block type to placements or geometry. Remaps blockId only; preserves stateData.",
    category = "material.basic_assignment",
    order = 0
)
public class AssignBlockTypeNode extends BaseNode {

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_BLOCKS_TREE_ID = "input_blocks_tree";
    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_PLACEMENTS_TREE_ID = "output_placements_tree";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public AssignBlockTypeNode() {
        super(UUID.randomUUID(), "material.basic_assignment.assign_block_type");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCKS_TREE_ID, "Blocks Tree", "Optional block positions grouped by branch", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to remap (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized first when placements/coordinates are empty", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Required block type applied to every resolved position", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_TREE_ID, "Block Placements Tree", "Placements grouped by source branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when Block Type and inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Assigns a single block type to placements or geometry. Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BasicAssignmentUtils.Validation blockTypeOk =
            BasicAssignmentUtils.requireBlockType(inputValues.get(INPUT_BLOCK_TYPE_ID));
        if (!blockTypeOk.valid()) {
            emitFail(blockTypeOk.message());
            return;
        }
        String blockType = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_BLOCK_TYPE_ID));

        Object blocksTreeObj = inputValues.get(INPUT_BLOCKS_TREE_ID);
        if (blocksTreeObj instanceof DataTreeData blocksTree && blocksTree.getBranchCount() > 0) {
            writeTreeAssignments(blocksTree, blockType);
            return;
        }

        List<BlockPlacementData> sources = MaterialMappingSupport.resolveSourcePlacements(
            inputValues.get(INPUT_PLACEMENTS_ID),
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            blockType
        );

        if (sources.isEmpty()
            && BasicAssignmentUtils.hasNonPlacementSource(
                inputValues.get(INPUT_COORDINATES_ID),
                inputValues.get(INPUT_GEOMETRY_ID),
                inputValues.get(INPUT_BOX_GEOMETRY_ID),
                inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
                inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
                inputValues.get(INPUT_TORUS_GEOMETRY_ID)
            )) {
            emitFail("No geometry or coordinates resolved");
            return;
        }

        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        for (BlockPlacementData source : sources) {
            if (source.pos() == null) {
                continue;
            }
            placements.add(MaterialMappingSupport.remapBlockId(source, blockType));
        }

        DataTreeData tree = new DataTreeData(List.of(
            new DataTreeData.Branch(List.of(0), new ArrayList<Object>(placements))
        ));
        emitOk(placements, tree);
    }

    private void writeTreeAssignments(DataTreeData blocksTree, String blockType) {
        List<BlockPlacementData> placements = new ArrayList<>();
        List<DataTreeData.Branch> placementBranches = new ArrayList<>();

        for (DataTreeData.Branch branch : blocksTree.getBranches()) {
            List<Object> branchPlacements = new ArrayList<>();
            for (Object item : branch.items()) {
                if (item instanceof BlockPlacementData existing) {
                    BlockPlacementData remapped = MaterialMappingSupport.remapBlockId(existing, blockType);
                    if (remapped.pos() == null) {
                        continue;
                    }
                    placements.add(remapped);
                    branchPlacements.add(remapped);
                } else if (item instanceof BlockPos pos) {
                    BlockPlacementData placement = new BlockPlacementData(pos, blockType);
                    placements.add(placement);
                    branchPlacements.add(placement);
                }
            }
            placementBranches.add(new DataTreeData.Branch(branch.path(), branchPlacements));
        }

        emitOk(placements, new DataTreeData(placementBranches));
    }

    private void emitFail(String message) {
        outputValues.putAll(BasicAssignmentUtils.assignFailResult(message));
    }

    private void emitOk(List<BlockPlacementData> placements, DataTreeData tree) {
        outputValues.putAll(BasicAssignmentUtils.assignOkResult(placements, tree));
    }
}
