package com.nodecraft.nodesystem.nodes.material.pattern_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BrickPatternMapping;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.pattern_mapping.brick_pattern_map",
    displayName = "Brick Pattern Map",
    description = "Assigns two materials using a staggered brick pattern relative to Pattern Origin. Remaps blockId only; preserves stateData.",
    category = "material.pattern_mapping",
    order = 2
)
public class BrickPatternMapNode extends BaseNode {

    public enum BrickDirection {
        Auto,
        X,
        Z
    }

    @NodeProperty(displayName = "Brick Length", category = "Pattern", order = 1)
    private int brickLength = 2;

    @NodeProperty(displayName = "Course Height", category = "Pattern", order = 2)
    private int courseHeight = 1;

    @NodeProperty(displayName = "Brick Direction", category = "Pattern", order = 3)
    private BrickDirection brickDirection = BrickDirection.Auto;

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

    public BrickPatternMapNode() {
        super(UUID.randomUUID(), "material.pattern_mapping.brick_pattern_map");
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements",
            "Canonical placements to remap (blockId only; stateData preserved)", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Optional geometry — voxelized when no higher-precedence source is driven", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PRIMARY_ID, "Primary", "Primary brick block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SECONDARY_ID, "Secondary", "Secondary mortar/variation block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_PATTERN_ORIGIN_ID, "Pattern Origin",
            "BLOCK_POS origin for pattern phase; undriven defaults to (0,0,0)", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Canonical material payload", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when brick dimensions and inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Assigns two materials using a staggered brick pattern relative to Pattern Origin. Remaps blockId only; preserves stateData.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PatternMaterialUtils.Validation lengthOk = PatternMaterialUtils.requirePositiveInt(brickLength, "Brick Length");
        if (!lengthOk.valid()) {
            emitFail(lengthOk.message());
            return;
        }
        PatternMaterialUtils.Validation heightOk = PatternMaterialUtils.requirePositiveInt(courseHeight, "Course Height");
        if (!heightOk.valid()) {
            emitFail(heightOk.message());
            return;
        }

        PatternMaterialUtils.OriginResult originResult =
            PatternMaterialUtils.resolveOrigin(this, INPUT_PATTERN_ORIGIN_ID);
        if (!originResult.valid()) {
            emitFail(originResult.error());
            return;
        }
        BlockPos origin = originResult.origin();

        MaterialMappingSupport.MappedBlockType primary =
            PatternMaterialUtils.resolveRole(this, INPUT_PRIMARY_ID);
        if (!primary.valid()) {
            emitFail(primary.error());
            return;
        }
        MaterialMappingSupport.MappedBlockType secondary =
            PatternMaterialUtils.resolveRole(this, INPUT_SECONDARY_ID);
        if (!secondary.valid()) {
            emitFail(secondary.error());
            return;
        }
        String primaryMapped = primary.blockId();
        String secondaryMapped = secondary.blockId();
        String fallback = MaterialMappingSupport.firstMappedBlockType(primaryMapped, secondaryMapped);

        MaterialSourceResolver.SourceResolution source =
            MaterialSourceResolver.resolve(this, SOURCE_PORTS, fallback);
        if (!source.valid()) {
            emitFail(source.error());
            return;
        }
        if (source.kind() == MaterialSourceResolver.SourceKind.NONE) {
            emitOk(List.of());
            return;
        }
        if ((source.kind() == MaterialSourceResolver.SourceKind.COORDINATES
            || source.kind() == MaterialSourceResolver.SourceKind.GEOMETRY)
            && fallback == null) {
            emitFail("Primary or Secondary material required for geometry or coordinates input");
            return;
        }

        List<BlockPlacementData> sources = source.placements();
        BrickPatternMapping.Axis axis = resolveBrickAxis(sources, origin);

        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        for (BlockPlacementData sourcePlacement : sources) {
            BlockPos pos = sourcePlacement.pos();
            PatternMaterialUtils.Relative rel = PatternMaterialUtils.relative(pos, origin);
            int brick = BrickPatternMapping.brickIndex(
                rel.dx(), rel.dy(), rel.dz(), brickLength, courseHeight, axis
            );
            String mapped = BrickPatternMapping.isPrimaryBrick(brick) ? primaryMapped : secondaryMapped;
            String blockId = PatternMaterialUtils.pickRole(mapped, sourcePlacement.blockId());
            MaterialMappingSupport.RemapResult remap =
                MaterialMappingSupport.remapValidated(sourcePlacement, blockId);
            if (!remap.valid()) {
                emitFail(remap.error());
                return;
            }
            placements.add(remap.placement());
        }
        emitOk(placements);
    }

    private BrickPatternMapping.Axis resolveBrickAxis(List<BlockPlacementData> sources, BlockPos origin) {
        if (brickDirection == BrickDirection.X) {
            return BrickPatternMapping.Axis.X;
        }
        if (brickDirection == BrickDirection.Z) {
            return BrickPatternMapping.Axis.Z;
        }
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        boolean any = false;
        for (BlockPlacementData source : sources) {
            if (source.pos() == null) {
                continue;
            }
            PatternMaterialUtils.Relative rel = PatternMaterialUtils.relative(source.pos(), origin);
            any = true;
            minX = Math.min(minX, rel.dx());
            maxX = Math.max(maxX, rel.dx());
            minZ = Math.min(minZ, rel.dz());
            maxZ = Math.max(maxZ, rel.dz());
        }
        if (!any) {
            return BrickPatternMapping.Axis.X;
        }
        return BrickPatternMapping.resolveAxisFromSpans(minX, maxX, minZ, maxZ);
    }

    private void emitFail(String message) {
        outputValues.putAll(PatternMaterialUtils.failResult(message));
    }

    private void emitOk(List<BlockPlacementData> placements) {
        outputValues.putAll(PatternMaterialUtils.okResult(placements));
    }

    public int getBrickLength() {
        return brickLength;
    }

    public void setBrickLength(int brickLength) {
        if (this.brickLength != brickLength) {
            this.brickLength = brickLength;
            markDirty();
        }
    }

    public int getCourseHeight() {
        return courseHeight;
    }

    public void setCourseHeight(int courseHeight) {
        if (this.courseHeight != courseHeight) {
            this.courseHeight = courseHeight;
            markDirty();
        }
    }

    public BrickDirection getBrickDirection() {
        return brickDirection;
    }

    public void setBrickDirection(BrickDirection brickDirection) {
        BrickDirection resolved = brickDirection == null ? BrickDirection.Auto : brickDirection;
        if (this.brickDirection != resolved) {
            this.brickDirection = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("brickLength", brickLength);
        state.put("courseHeight", courseHeight);
        state.put("brickDirection", brickDirection.name());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("brickLength") instanceof Integer value) {
            this.brickLength = value;
        }
        if (map.get("courseHeight") instanceof Integer value) {
            this.courseHeight = value;
        }
        if (map.get("brickDirection") instanceof String name) {
            try {
                this.brickDirection = BrickDirection.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                this.brickDirection = BrickDirection.Auto;
            }
        }
    }
}
