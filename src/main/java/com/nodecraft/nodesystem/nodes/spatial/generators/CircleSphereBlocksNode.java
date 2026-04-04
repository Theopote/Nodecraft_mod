package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.SphereBlockGenerator;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates circle or sphere blocks. When 3D mode is enabled, the node also
 * exposes sphere geometry for the shared geometry pipeline.
 */
@NodeInfo(
    id = "spatial.generators.circle_sphere_blocks",
    displayName = "Circle / Sphere (Blocks)",
    description = "Generates a circle or sphere of blocks",
    category = "spatial.generators"
)
public class CircleSphereBlocksNode extends BaseNode {

    @NodeProperty(displayName = "3D Sphere", category = "Shape", order = 1)
    private boolean is3D = false;

    @NodeProperty(displayName = "Fill Shape", category = "Shape", order = 2)
    private boolean fillShape = true;

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_SPHERE_GEOMETRY_ID = "output_sphere_geometry";

    public CircleSphereBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.circle_sphere_blocks");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Center point of the circle or sphere", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Radius of the circle or sphere", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Generated blocks", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Resolved sphere geometry when 3D is enabled", NodeDataType.SPHERE, this));
    }

    @Override
    public String getDescription() {
        return "Generates a circle or sphere of blocks";
    }

    @Override
    public String getDisplayName() {
        return "Circle / Sphere (Blocks)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList result = new BlockPosList();
        SphereData sphereGeometry = null;

        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        if (centerObj instanceof BlockPos center && radiusObj instanceof Number radiusNumber) {
            double radius = Math.max(1.0d, radiusNumber.doubleValue());
            if (is3D) {
                sphereGeometry = new SphereData(
                    new Vector3d(center.getX(), center.getY(), center.getZ()),
                    radius
                );
                SphereBlockGenerator.populateSphere(
                    result,
                    SphereBlockGenerator.createBoundingRegion(sphereGeometry),
                    sphereGeometry,
                    fillShape
                );
            } else {
                generateCircle(center, radius, result);
            }
        }

        outputValues.put(OUTPUT_BLOCKS_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        outputValues.put(OUTPUT_GEOMETRY_ID, sphereGeometry);
        outputValues.put(OUTPUT_SPHERE_GEOMETRY_ID, sphereGeometry);
    }

    private void generateCircle(BlockPos center, double radius, BlockPosList result) {
        int radiusCeil = (int) Math.ceil(radius);
        for (int dx = -radiusCeil; dx <= radiusCeil; dx++) {
            for (int dz = -radiusCeil; dz <= radiusCeil; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius) {
                    continue;
                }
                if (!fillShape && distance < Math.max(0.0d, radius - 1.0d)) {
                    continue;
                }
                result.add(new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz));
            }
        }
    }

    public boolean isIs3D() {
        return is3D;
    }

    public void setIs3D(boolean is3D) {
        if (this.is3D != is3D) {
            this.is3D = is3D;
            markDirty();
        }
    }

    public boolean isFillShape() {
        return fillShape;
    }

    public void setFillShape(boolean fillShape) {
        if (this.fillShape != fillShape) {
            this.fillShape = fillShape;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("is3D", is3D);
        state.put("fillShape", fillShape);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("is3D") instanceof Boolean is3DValue) {
            setIs3D(is3DValue);
        }
        if (map.get("fillShape") instanceof Boolean fillShapeValue) {
            setFillShape(fillShapeValue);
        }
    }
}
