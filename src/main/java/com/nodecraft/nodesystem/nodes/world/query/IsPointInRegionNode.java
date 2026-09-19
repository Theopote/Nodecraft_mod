package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.query.is_point_in_region",
    displayName = "Point In Region",
    description = "Tests whether the center of a block position lies inside a region.",
    category = "world.query",
    order = 4
)
public class IsPointInRegionNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_REGION_ID = "input_region";

    private static final String OUTPUT_IS_INSIDE_ID = "output_is_inside";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public IsPointInRegionNode() {
        super(UUID.randomUUID(), "world.query.is_point_in_region");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Block position whose center is tested", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to test against", NodeDataType.REGION, this));

        addOutputPort(new BasePort(OUTPUT_IS_INSIDE_ID, "Is Inside", "Whether the block center is inside the region", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Distance to the region boundary; negative when inside", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether point and region inputs were valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Tests whether the center of a block position lies inside a region.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointObj = inputValues.get(INPUT_POINT_ID);
        Object regionObj = inputValues.get(INPUT_REGION_ID);

        boolean isInside = false;
        double distance = -1.0d;
        boolean valid = false;

        if (pointObj instanceof BlockPos point && regionObj instanceof RegionData region && region.isComplete()) {
            Box box = region.toBox();
            if (box != null) {
                double pointX = point.getX() + 0.5d;
                double pointY = point.getY() + 0.5d;
                double pointZ = point.getZ() + 0.5d;

                isInside = box.contains(pointX, pointY, pointZ);
                distance = calculateDistanceToBox(pointX, pointY, pointZ, box);
                valid = true;
            }
        }

        outputValues.put(OUTPUT_IS_INSIDE_ID, isInside);
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private double calculateDistanceToBox(double x, double y, double z, Box box) {
        double dx = Math.max(box.minX - x, Math.max(0.0d, x - box.maxX));
        double dy = Math.max(box.minY - y, Math.max(0.0d, y - box.maxY));
        double dz = Math.max(box.minZ - z, Math.max(0.0d, z - box.maxZ));

        if (dx == 0.0d && dy == 0.0d && dz == 0.0d) {
            double dxMin = x - box.minX;
            double dxMax = box.maxX - x;
            double dyMin = y - box.minY;
            double dyMax = box.maxY - y;
            double dzMin = z - box.minZ;
            double dzMax = box.maxZ - z;
            double minDistance = Math.min(
                Math.min(dxMin, dxMax),
                Math.min(Math.min(dyMin, dyMax), Math.min(dzMin, dzMax))
            );
            return -minDistance;
        }

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
