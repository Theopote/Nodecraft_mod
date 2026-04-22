package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    id = "pattern.surface_volume_distribution.sample_geometry_surface",
    displayName = "Sample Geometry Surface",
    description = "Samples points from voxelized geometry surfaces using density or explicit count",
    category = "pattern.surface_volume_distribution",
    order = 2
)
public class SampleGeometrySurfaceNode extends BaseNode {

    @NodeProperty(displayName = "Density", category = "Sampling", order = 1)
    private double density = 0.2d;

    @NodeProperty(displayName = "Default Count", category = "Sampling", order = 2)
    private int defaultCount = 128;

    @NodeProperty(displayName = "Seed", category = "Sampling", order = 3)
    private int seed = 12345;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_DENSITY_ID = "input_density";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SampleGeometrySurfaceNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.sample_geometry_surface");
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_DENSITY_ID, "Density", "Sampling density in [0,1], applied to voxelized surface block count", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Optional explicit sample count override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional random seed override", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled surface points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Sampled surface blocks", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated sample count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid geometry surface could be sampled", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Samples points from voxelized geometry surfaces using density or explicit count";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList surfaceBlocks = GeometryVoxelizer.resolveBlocks(
            null,
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            false
        );
        if (surfaceBlocks.isEmpty()) {
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double resolvedDensity = inputValues.get(INPUT_DENSITY_ID) instanceof Number n
            ? n.doubleValue()
            : density;
        resolvedDensity = Math.max(0.0d, Math.min(1.0d, resolvedDensity));
        int resolvedCount = inputValues.get(INPUT_COUNT_ID) instanceof Number n
            ? n.intValue()
            : Math.max(1, (int) Math.ceil(surfaceBlocks.size() * resolvedDensity));
        if (resolvedCount <= 0) {
            resolvedCount = Math.min(defaultCount, Math.max(1, surfaceBlocks.size()));
        }
        resolvedCount = Math.min(resolvedCount, surfaceBlocks.size());
        int resolvedSeed = inputValues.get(INPUT_SEED_ID) instanceof Number n ? n.intValue() : seed;

        List<BlockPos> pool = new ArrayList<>(surfaceBlocks.getPositions());
        Collections.shuffle(pool, new Random(resolvedSeed));
        List<BlockPos> selected = pool.subList(0, resolvedCount);

        BlockPosList blockSamples = new BlockPosList();
        List<Vector3d> pointSamples = new ArrayList<>(resolvedCount);
        for (BlockPos pos : selected) {
            blockSamples.add(pos);
            pointSamples.add(new Vector3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d));
        }

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(pointSamples));
        outputValues.put(OUTPUT_BLOCKS_ID, blockSamples);
        outputValues.put(OUTPUT_COUNT_ID, pointSamples.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

