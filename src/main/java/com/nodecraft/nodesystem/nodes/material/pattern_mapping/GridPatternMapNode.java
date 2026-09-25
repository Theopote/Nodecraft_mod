package com.nodecraft.nodesystem.nodes.material.pattern_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.pattern_mapping.grid_pattern_map",
    displayName = "Grid Pattern Map",
    description = "Assigns frame/fill materials using an X/Z grid relative to Pattern Origin (Y extruded). Remaps blockId only; preserves stateData.",
    category = "material.pattern_mapping",
    order = 3
)
public class GridPatternMapNode extends BaseNode {

    @NodeProperty(displayName = "Grid Size", category = "Pattern", order = 1)
    private int gridSize = 4;

    @NodeProperty(displayName = "Line Width", category = "Pattern", order = 2)
    private int lineWidth = 1;

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_FRAME_ID = "input_frame";
    private static final String INPUT_FILL_ID = "input_fill";
    private static final String INPUT_PATTERN_ORIGIN_ID = "input_pattern_origin";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GridPatternMapNode() {
        super(UUID.randomUUID(), "material.pattern_mapping.grid_pattern_map");
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to remap (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized first when placements/coordinates are empty", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame", "Grid line block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_FILL_ID, "Fill", "Grid cell fill block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_PATTERN_ORIGIN_ID, "Pattern Origin",
            "BLOCK_POS origin for pattern phase; missing defaults to (0,0,0)", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when grid size/line width and inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Assigns frame/fill materials using an X/Z grid relative to Pattern Origin (Y extruded). Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PatternMaterialUtils.Validation params = PatternMaterialUtils.requireLineWidth(lineWidth, gridSize);
        if (!params.valid()) {
            emitFail(params.message());
            return;
        }

        String frameMapped = PatternMaterialUtils.optionalRole(inputValues.get(INPUT_FRAME_ID));
        String fillMapped = PatternMaterialUtils.optionalRole(inputValues.get(INPUT_FILL_ID));

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
                MaterialMappingSupport.firstMappedBlockType(frameMapped, fillMapped)
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
            && frameMapped == null
            && fillMapped == null) {
            emitFail("Frame or Fill material required for geometry or coordinates input");
            return;
        }

        BlockPos origin = PatternMaterialUtils.resolveOrigin(inputValues.get(INPUT_PATTERN_ORIGIN_ID));
        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        for (BlockPlacementData source : sources) {
            BlockPos pos = source.pos();
            if (pos == null) {
                continue;
            }
            PatternMaterialUtils.Relative rel = PatternMaterialUtils.relative(pos, origin);
            int gx = Math.floorMod(rel.dx(), gridSize);
            int gz = Math.floorMod(rel.dz(), gridSize);
            boolean onLine = gx < lineWidth || gz < lineWidth;
            String mapped = onLine ? frameMapped : fillMapped;
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

    public int getGridSize() {
        return gridSize;
    }

    public void setGridSize(int gridSize) {
        if (this.gridSize != gridSize) {
            this.gridSize = gridSize;
            markDirty();
        }
    }

    public int getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(int lineWidth) {
        if (this.lineWidth != lineWidth) {
            this.lineWidth = lineWidth;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("gridSize", gridSize);
        state.put("lineWidth", lineWidth);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("gridSize") instanceof Number value) {
            this.gridSize = value.intValue();
        }
        if (map.get("lineWidth") instanceof Number value) {
            this.lineWidth = value.intValue();
        }
    }
}
