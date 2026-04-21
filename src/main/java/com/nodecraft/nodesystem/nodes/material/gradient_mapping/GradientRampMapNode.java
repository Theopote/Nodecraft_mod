package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "material.gradient_mapping.gradient_ramp_map",
    displayName = "Gradient Ramp Map",
    description = "Assigns block types by height using a custom multi-stop ramp list.",
    category = "material.gradient_mapping",
    order = 2
)
public class GradientRampMapNode extends BaseNode {

    @Override
    public String getDescription() {
        return "Assigns block types by height using a custom multi-stop ramp list.";
    }

    @NodeProperty(displayName = "Ramp Blocks", category = "Ramp", order = 1,
        description = "Comma-separated block ids from low to high, e.g. stone,dirt,grass_block,snow_block")
    private String rampBlocks = "minecraft:stone,minecraft:dirt,minecraft:grass_block,minecraft:snow_block";

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";

    public GradientRampMapNode() {
        super(UUID.randomUUID(), "material.gradient_mapping.gradient_ramp_map");
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with the positions list", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Position and block pairs for baking", NodeDataType.BLOCK_PLACEMENT_LIST, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            inputValues.get(INPUT_COORDINATES_ID), inputValues.get(INPUT_GEOMETRY_ID), inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID), inputValues.get(INPUT_SPHERE_GEOMETRY_ID), inputValues.get(INPUT_TORUS_GEOMETRY_ID), true
        );
        List<String> ramp = parseRamp(rampBlocks);
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
        }
        double span = Math.max(1.0d, maxY - minY);

        BlockPosList outPos = new BlockPosList();
        List<String> ids = new ArrayList<>();
        List<BlockPlacementData> placements = new ArrayList<>();
        for (BlockPos pos : positions) {
            double t = (pos.getY() - minY) / span;
            int idx = Math.min(ramp.size() - 1, Math.max(0, (int) Math.floor(t * ramp.size())));
            String id = ramp.get(idx);
            outPos.add(pos);
            ids.add(id);
            placements.add(new BlockPlacementData(pos, id));
        }
        outputValues.put(OUTPUT_POSITIONS_ID, outPos);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, ids);
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
    }

    private List<String> parseRamp(String rampText) {
        List<String> blocks = new ArrayList<>();
        if (rampText != null) {
            for (String token : rampText.split(",")) {
                String value = token.trim();
                if (!value.isEmpty()) {
                    blocks.add(value.contains(":") ? value : "minecraft:" + value);
                }
            }
        }
        if (blocks.isEmpty()) {
            blocks.add("minecraft:stone");
        }
        return blocks;
    }
}

