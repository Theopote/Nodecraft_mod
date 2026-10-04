package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.material.block_state.BlockStateValidationUtils;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
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

    private static final MaterialSourceResolver.SourcePorts SOURCE_PORTS = new MaterialSourceResolver.SourcePorts(
        null,
        INPUT_BLOCKS_TREE_ID,
        INPUT_PLACEMENTS_ID,
        INPUT_COORDINATES_ID,
        INPUT_GEOMETRY_ID,
        INPUT_BOX_GEOMETRY_ID,
        INPUT_CYLINDER_GEOMETRY_ID,
        INPUT_SPHERE_GEOMETRY_ID,
        INPUT_TORUS_GEOMETRY_ID
    );

    public AssignBlockTypeNode() {
        super(UUID.randomUUID(), "material.basic_assignment.assign_block_type");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCKS_TREE_ID, "Blocks Tree", "Optional block positions grouped by branch", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to remap (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized when no higher-precedence source is driven", NodeDataType.GEOMETRY, this));
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

        MaterialSourceResolver.SourceResolution source =
            MaterialSourceResolver.resolve(this, SOURCE_PORTS, blockType);
        if (!source.valid()) {
            emitFail(source.error());
            return;
        }

        switch (source.kind()) {
            case BLOCKS_TREE -> writeTreeAssignments(source.tree(), blockType);
            case PLACEMENTS, COORDINATES, GEOMETRY -> {
                List<BlockPlacementData> placements = new ArrayList<>(source.placements().size());
                for (BlockPlacementData sourcePlacement : source.placements()) {
                    BlockPlacementData remapped = MaterialMappingSupport.remapBlockId(sourcePlacement, blockType);
                    String error = BlockStateValidationUtils.remapIncompatibility(remapped);
                    if (error != null) {
                        emitFail(error);
                        return;
                    }
                    placements.add(remapped);
                }
                DataTreeData tree = new DataTreeData(List.of(
                    new DataTreeData.Branch(List.of(0), new ArrayList<Object>(placements))
                ));
                emitOk(placements, tree);
            }
            case NONE, PLACEMENTS_TREE -> emitOk(List.of(), new DataTreeData(List.of()));
        }
    }

    private void writeTreeAssignments(DataTreeData blocksTree, String blockType) {
        List<BlockPlacementData> placements = new ArrayList<>();
        List<DataTreeData.Branch> placementBranches = new ArrayList<>();

        for (DataTreeData.Branch branch : blocksTree.getBranches()) {
            List<Object> branchPlacements = new ArrayList<>();
            for (Object item : branch.items()) {
                if (item instanceof BlockPlacementData existing) {
                    BlockPlacementData remapped = MaterialMappingSupport.remapBlockId(existing, blockType);
                    String error = BlockStateValidationUtils.remapIncompatibility(remapped);
                    if (error != null) {
                        emitFail(error);
                        return;
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
