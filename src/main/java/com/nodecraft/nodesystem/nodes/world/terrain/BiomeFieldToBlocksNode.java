package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.BlockStateResolver;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PlacementPreflight;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.biome_field_to_blocks",
    displayName = "Biome Field To Blocks",
    description = "Maps biome id field to surface block placements using a BLOCK_PALETTE. "
        + "Height field is normalized [-1,1] (height=-1 → minY, height=+1 → maxY).",
    category = "world.terrain",
    order = 16
)
public class BiomeFieldToBlocksNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_BIOME_ID_FIELD_ID = "input_biome_id_field";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_PALETTE_ID = "input_palette";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_FILL_TILES_ID = "input_fill_tiles";
    private static final String INPUT_MAX_COLUMNS_ID = "input_max_columns";
    private static final String INPUT_MAX_PLACEMENTS_ID = "input_max_placements";

    private static final String OUTPUT_BLOCK_PLACEMENTS_ID = "output_block_placements";
    private static final String OUTPUT_SURFACE_BLOCKS_ID = "output_surface_blocks";
    private static final String OUTPUT_COLUMN_COUNT_ID = "output_column_count";
    private static final String OUTPUT_PLACEMENT_COUNT_ID = "output_placement_count";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_COMPLETE_ID = "output_complete";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Step", category = "Sampling", order = 1,
        description = "Surface sampling stride for preview downsampling")
    private int step = 1;

    @NodeProperty(displayName = "Fill Tiles", category = "Sampling", order = 2,
        description = "Expands each sampled value across its Step-sized tile to avoid sparse preview gaps")
    private boolean fillTiles = false;

    @NodeProperty(displayName = "Max Columns", category = "Safety", order = 3)
    private int maxColumns = TerrainNodeUtils.DEFAULT_MAX_COLUMNS;

    @NodeProperty(displayName = "Max Placements", category = "Safety", order = 4)
    private int maxPlacements = TerrainNodeUtils.DEFAULT_MAX_PLACEMENTS;

    public BiomeFieldToBlocksNode() {
        super(UUID.randomUUID(), "world.terrain.biome_field_to_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region",
            "Optional region to materialize; defaults to modeling domain (−32..31, Y −64..319) when omitted",
            NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_BIOME_ID_FIELD_ID, "Biome Id Field",
            "Biome class id encoded as scalar", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field",
            "Normalized terrain height field in [-1,1] for surface Y", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Biome Palette",
            "Block ids ordered by biome class id", NodeDataType.BLOCK_PALETTE, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step",
            "Surface sampling stride in blocks", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_FILL_TILES_ID, "Fill Tiles",
            "Expands each sampled value across its Step-sized tile to avoid sparse preview gaps",
            NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_COLUMNS_ID, "Max Columns",
            "Maximum output terrain columns before stopping (hard-capped; includes Fill Tiles expansion)",
            NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MAX_PLACEMENTS_ID, "Max Placements",
            "Maximum block placements before stopping (hard-capped)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_PLACEMENTS_ID, "Block Placements",
            "Biome-based surface placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_BLOCKS_ID, "Surface Blocks",
            "Resolved surface blocks", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COLUMN_COUNT_ID, "Column Count",
            "Sampled output columns", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENT_COUNT_ID, "Placement Count",
            "Generated block placement count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit",
            "True when a user safety budget stopped generation", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete",
            "False when Hit Limit stopped generation early", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason",
            "Reason generation stopped early", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "Whether generation succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when generation failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!(inputValues.get(INPUT_BIOME_ID_FIELD_ID) instanceof ScalarFieldData biomeField)
            || !(inputValues.get(INPUT_HEIGHT_FIELD_ID) instanceof ScalarFieldData heightField)) {
            writeInvalid("Missing biome id field or height field input.");
            return;
        }

        RegionData region = TerrainNodeUtils.resolveOptionalRegion(this, INPUT_REGION_ID);
        if (TerrainNodeUtils.isInvalidRegionMarker(region)) {
            writeInvalid("Region is connected but incomplete or invalid.");
            return;
        }
        RegionBounds bounds = resolveBounds(region);

        BlockPaletteData palette = BlockPaletteData.requireTyped(inputValues.get(INPUT_PALETTE_ID));
        if (palette.isEmpty()) {
            writeInvalid("Biome Palette is required and must contain at least one block.");
            return;
        }
        for (String paletteBlockId : palette.blockIds()) {
            if (paletteBlockId == null || paletteBlockId.isBlank()) {
                writeInvalid("Biome Palette entry is empty.");
                return;
            }
            if (BlockStateResolver.resolveDefault(paletteBlockId) == null) {
                writeInvalid(PlacementPreflight.ERROR_UNRESOLVABLE_BLOCK + ": " + paletteBlockId);
                return;
            }
        }

        Integer resolvedStep = TerrainNodeUtils.resolveOptionalExactInteger(this, INPUT_STEP_ID, step);
        if (resolvedStep == null || resolvedStep < 1) {
            writeInvalid("Step must be an exact INTEGER >= 1.");
            return;
        }

        Boolean resolvedFillTiles = TerrainNodeUtils.resolveOptionalBoolean(this, INPUT_FILL_TILES_ID, fillTiles);
        if (resolvedFillTiles == null) {
            writeInvalid("Fill Tiles is connected but null or invalid.");
            return;
        }

        Integer resolvedMaxColumns = TerrainNodeUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_COLUMNS_ID, maxColumns, GenerationLimits.MAX_TERRAIN_SAMPLES);
        if (resolvedMaxColumns == null) {
            writeInvalid("Max Columns must be an exact INTEGER between 1 and "
                + GenerationLimits.MAX_TERRAIN_SAMPLES + ".");
            return;
        }

        Integer resolvedMaxPlacements = TerrainNodeUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_PLACEMENTS_ID, maxPlacements, GenerationLimits.MAX_TERRAIN_PLACEMENTS);
        if (resolvedMaxPlacements == null) {
            writeInvalid("Max Placements must be an exact INTEGER between 1 and "
                + GenerationLimits.MAX_TERRAIN_PLACEMENTS + ".");
            return;
        }

        List<BlockPlacementData> placements = new ArrayList<>();
        BlockPosList surfaceBlocks = new BlockPosList();
        Vector3d samplePoint = new Vector3d();
        int columnCount = 0;
        boolean hitLimit = false;
        String stoppedReason = "";
        int paletteLast = palette.size() - 1;

        int x = bounds.minX;
        while (true) {
            int z = bounds.minZ;
            while (true) {
                if (columnCount >= resolvedMaxColumns) {
                    hitLimit = true;
                    stoppedReason = "max_columns";
                    break;
                }

                samplePoint.set(
                    x + BlockSpace.CELL_CENTER_OFFSET,
                    bounds.minY + BlockSpace.CELL_CENTER_OFFSET,
                    z + BlockSpace.CELL_CENTER_OFFSET
                );
                double h = heightField.sampleScalar(samplePoint);
                if (!Double.isFinite(h)) {
                    writeInvalid("Height field returned a non-finite sample.");
                    return;
                }
                int y = toColumnTopY(h, bounds.minY, bounds.maxY);

                samplePoint.set(
                    x + BlockSpace.CELL_CENTER_OFFSET,
                    y + BlockSpace.CELL_CENTER_OFFSET,
                    z + BlockSpace.CELL_CENTER_OFFSET
                );
                double biomeSample = biomeField.sampleScalar(samplePoint);
                if (!Double.isFinite(biomeSample)) {
                    writeInvalid("Biome id field returned a non-finite sample.");
                    return;
                }
                int biomeId = (int) Math.round(Math.max(0.0d, biomeSample));
                if (biomeId > paletteLast) {
                    writeInvalid("Biome id out of palette range.");
                    return;
                }
                String blockId = palette.blockIdAt(biomeId, "");
                if (blockId == null || blockId.isBlank()) {
                    writeInvalid("Biome Palette entry is empty.");
                    return;
                }

                int tileMaxX = resolvedFillTiles
                    ? TerrainGridDomain.safeTileEnd(x, resolvedStep, bounds.maxX)
                    : x;
                int tileMaxZ = resolvedFillTiles
                    ? TerrainGridDomain.safeTileEnd(z, resolvedStep, bounds.maxZ)
                    : z;

                int tx = x;
                tileX:
                while (true) {
                    int tz = z;
                    while (true) {
                        if (columnCount >= resolvedMaxColumns) {
                            hitLimit = true;
                            stoppedReason = "max_columns";
                            break tileX;
                        }
                        if (placements.size() >= resolvedMaxPlacements) {
                            hitLimit = true;
                            stoppedReason = "max_placements";
                            break tileX;
                        }
                        BlockPos pos = new BlockPos(tx, y, tz);
                        placements.add(new BlockPlacementData(pos, blockId));
                        surfaceBlocks.add(pos);
                        columnCount++;

                        Integer nextTz = TerrainGridDomain.safeNextAxis(tz, 1, tileMaxZ);
                        if (nextTz == null) {
                            break;
                        }
                        tz = nextTz;
                    }
                    Integer nextTx = TerrainGridDomain.safeNextAxis(tx, 1, tileMaxX);
                    if (nextTx == null) {
                        break;
                    }
                    tx = nextTx;
                }

                if (hitLimit) {
                    break;
                }
                Integer nextZ = TerrainGridDomain.safeNextAxis(z, resolvedStep, bounds.maxZ);
                if (nextZ == null) {
                    break;
                }
                z = nextZ;
            }
            if (hitLimit) {
                break;
            }
            Integer nextX = TerrainGridDomain.safeNextAxis(x, resolvedStep, bounds.maxX);
            if (nextX == null) {
                break;
            }
            x = nextX;
        }

        outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_SURFACE_BLOCKS_ID, surfaceBlocks);
        outputValues.put(OUTPUT_COLUMN_COUNT_ID, columnCount);
        outputValues.put(OUTPUT_PLACEMENT_COUNT_ID, placements.size());
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, !hitLimit);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_SURFACE_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_COLUMN_COUNT_ID, 0);
        outputValues.put(OUTPUT_PLACEMENT_COUNT_ID, 0);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
        outputValues.put(OUTPUT_COMPLETE_ID, false);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private int toColumnTopY(double sampledHeight, int minY, int maxY) {
        double normalized = TerrainNodeUtils.clampNormalizedHeight(sampledHeight);
        if (!Double.isFinite(normalized)) {
            return minY;
        }
        double t = (normalized + 1.0d) * 0.5d;
        double spanY = (double) maxY - (double) minY;
        int y = (int) Math.round(minY + t * spanY);
        return TerrainNodeUtils.clamp(y, minY, maxY);
    }

    private RegionBounds resolveBounds(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return new RegionBounds(
                TerrainNodeUtils.DEFAULT_MIN_X, TerrainNodeUtils.DEFAULT_MAX_X,
                TerrainNodeUtils.DEFAULT_MIN_Y, TerrainNodeUtils.DEFAULT_MAX_Y,
                TerrainNodeUtils.DEFAULT_MIN_Z, TerrainNodeUtils.DEFAULT_MAX_Z);
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return new RegionBounds(
                TerrainNodeUtils.DEFAULT_MIN_X, TerrainNodeUtils.DEFAULT_MAX_X,
                TerrainNodeUtils.DEFAULT_MIN_Y, TerrainNodeUtils.DEFAULT_MAX_Y,
                TerrainNodeUtils.DEFAULT_MIN_Z, TerrainNodeUtils.DEFAULT_MAX_Z);
        }
        return new RegionBounds(min.getX(), max.getX(), min.getY(), max.getY(), min.getZ(), max.getZ());
    }

    private record RegionBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }
}
