package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    id = "world.terrain.plate_partition_field",
    displayName = "Plate Partition Field",
    description = "Generates pseudo tectonic plate ids and boundary intensity via Voronoi-style partitioning.",
    category = "world.terrain",
    order = 2
)
public class PlatePartitionFieldNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_PLATE_COUNT_ID = "input_plate_count";

    private static final String OUTPUT_PLATE_ID_FIELD_ID = "output_plate_id_field";
    private static final String OUTPUT_BOUNDARY_FIELD_ID = "output_boundary_field";

    @NodeProperty(displayName = "Seed", category = "Partition", order = 1)
    private int seed = 2026;

    @NodeProperty(displayName = "Plate Count", category = "Partition", order = 2)
    private int plateCount = 10;

    public PlatePartitionFieldNode() {
        super(UUID.randomUUID(), "world.terrain.plate_partition_field");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region that bounds plate seeds", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic seed", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLATE_COUNT_ID, "Plate Count", "Number of pseudo plates", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PLATE_ID_FIELD_ID, "Plate Id Field", "Nearest-seed plate id as scalar", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_FIELD_ID, "Boundary Field", "Boundary intensity in [0,1]", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        RegionData region = inputValues.get(INPUT_REGION_ID) instanceof RegionData value ? value : null;
        int resolvedSeed = getInputInt(INPUT_SEED_ID, seed);
        int resolvedPlateCount = Math.max(2, getInputInt(INPUT_PLATE_COUNT_ID, plateCount));

        Bounds bounds = Bounds.fromRegion(region);
        List<PlateSeed> seeds = generateSeeds(bounds, resolvedPlateCount, resolvedSeed);

        ScalarFieldData plateIdField = point -> {
            NearestPair pair = nearestPair(point.x, point.z, seeds);
            return pair.nearestIndex;
        };

        ScalarFieldData boundaryField = point -> {
            NearestPair pair = nearestPair(point.x, point.z, seeds);
            double gap = Math.max(1.0e-9d, pair.secondDistance - pair.nearestDistance);
            double normalizedGap = gap / Math.max(1.0d, bounds.diagonal);
            return clamp01(1.0d - normalizedGap * 24.0d);
        };

        outputValues.put(OUTPUT_PLATE_ID_FIELD_ID, plateIdField);
        outputValues.put(OUTPUT_BOUNDARY_FIELD_ID, boundaryField);
    }

    private List<PlateSeed> generateSeeds(Bounds bounds, int count, int randomSeed) {
        Random random = new Random(randomSeed);
        List<PlateSeed> seeds = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double x = lerp(bounds.minX, bounds.maxX, random.nextDouble());
            double z = lerp(bounds.minZ, bounds.maxZ, random.nextDouble());
            seeds.add(new PlateSeed(x, z, i));
        }

        return seeds;
    }

    private NearestPair nearestPair(double x, double z, List<PlateSeed> seeds) {
        double nearest = Double.POSITIVE_INFINITY;
        double second = Double.POSITIVE_INFINITY;
        int nearestIndex = 0;

        for (PlateSeed seed : seeds) {
            double dx = x - seed.x;
            double dz = z - seed.z;
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance < nearest) {
                second = nearest;
                nearest = distance;
                nearestIndex = seed.index;
            } else if (distance < second) {
                second = distance;
            }
        }

        return new NearestPair(nearest, second, nearestIndex);
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private record PlateSeed(double x, double z, int index) {
    }

    private record NearestPair(double nearestDistance, double secondDistance, int nearestIndex) {
    }

    private record Bounds(double minX, double minZ, double maxX, double maxZ, double diagonal) {

        private static Bounds fromRegion(@Nullable RegionData region) {
            if (region == null || !region.isComplete()) {
                return new Bounds(-4096.0d, -4096.0d, 4096.0d, 4096.0d, Math.sqrt(4096.0d * 4096.0d * 8.0d));
            }

            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min == null || max == null) {
                return new Bounds(-4096.0d, -4096.0d, 4096.0d, 4096.0d, Math.sqrt(4096.0d * 4096.0d * 8.0d));
            }

            double minX = min.getX();
            double minZ = min.getZ();
            double maxX = max.getX();
            double maxZ = max.getZ();
            double dx = Math.max(1.0d, maxX - minX);
            double dz = Math.max(1.0d, maxZ - minZ);
            double diagonal = Math.sqrt(dx * dx + dz * dz);
            return new Bounds(minX, minZ, maxX, maxZ, diagonal);
        }
    }
}
