package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    id = "pattern.surface_volume_distribution.scatter_surface_strip",
    displayName = "Scatter On Surface Strip",
    description = "Scatters points on a surface strip by random section interpolation with optional spacing",
    category = "pattern.surface_volume_distribution",
    order = 5
)
public class ScatterOnSurfaceStripNode extends BaseNode {

    @NodeProperty(displayName = "Count", category = "Scatter", order = 1)
    private int count = 128;

    @NodeProperty(displayName = "Seed", category = "Scatter", order = 2)
    private int seed = 12345;

    @NodeProperty(displayName = "Min Spacing", category = "Scatter", order = 3)
    private double minSpacing = 0.0d;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_MIN_SPACING_ID = "input_min_spacing";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ScatterOnSurfaceStripNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.scatter_surface_strip");
        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to scatter points on", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Optional scatter count override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional random seed override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_SPACING_ID, "Min Spacing", "Minimum Euclidean spacing between accepted points", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Scattered points on the strip surface", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Scattered points snapped to block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated point count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid strip was sampled", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Scatters points on a surface strip by random section interpolation with optional spacing";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object stripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (!(stripObj instanceof SurfaceStripData strip)) {
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        List<List<Vector3d>> sections = strip.getSections();
        if (sections.size() < 2 || sections.get(0).size() < 2) {
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        int resolvedCount = GenerationLimits.clampPositiveCount(inputValues.get(INPUT_COUNT_ID) instanceof Number n ? n.intValue() : count);
        int resolvedSeed = inputValues.get(INPUT_SEED_ID) instanceof Number n ? n.intValue() : seed;
        double spacing = Math.max(0.0d, inputValues.get(INPUT_MIN_SPACING_ID) instanceof Number n ? n.doubleValue() : minSpacing);
        double spacingSq = spacing * spacing;

        Random random = new Random(resolvedSeed);
        int sectionCount = sections.size();
        int pointsPerSection = sections.get(0).size();

        List<Vector3d> points = new ArrayList<>(resolvedCount);
        int maxAttempts = Math.max(resolvedCount * 24, 128);
        for (int attempt = 0; attempt < maxAttempts && points.size() < resolvedCount; attempt++) {
            int s = random.nextInt(sectionCount - 1);
            int i = random.nextInt(pointsPerSection);
            int nextI = (i + 1) % pointsPerSection;
            double u = random.nextDouble();
            double v = random.nextDouble();
            if (u + v > 1.0d) {
                u = 1.0d - u;
                v = 1.0d - v;
            }

            Vector3d a = sections.get(s).get(i);
            Vector3d b = sections.get(s).get(nextI);
            Vector3d c = sections.get(s + 1).get(i);

            Vector3d p = new Vector3d(a)
                .add(new Vector3d(b).sub(a).mul(u))
                .add(new Vector3d(c).sub(a).mul(v));

            if (spacingSq > 0.0d && !isFarEnough(p, points, spacingSq)) {
                continue;
            }
            points.add(p);
        }

        Collections.shuffle(points, random);
        BlockPosList blocks = new BlockPosList();
        for (Vector3d p : points) {
            blocks.add(new BlockPos((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z)));
        }

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, !points.isEmpty());
    }

    private boolean isFarEnough(Vector3d candidate, List<Vector3d> existing, double minSpacingSq) {
        for (Vector3d p : existing) {
            if (candidate.distanceSquared(p) < minSpacingSq) {
                return false;
            }
        }
        return true;
    }
}

