package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a torus block volume around a center point.
 *
 * If a plane is provided, the torus is oriented so its axis matches the
 * plane normal. Otherwise it defaults to the world Y axis.
 */
@NodeInfo(
    id = "spatial.generators.torus_blocks",
    displayName = "圆环生成器",
    description = "生成圆环体区域的坐标列表，可选平面输入用于控制朝向",
    category = "spatial.generators"
)
public class TorusBlocksNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_MAJOR_RADIUS_ID = "input_major_radius";
    private static final String INPUT_MINOR_RADIUS_ID = "input_minor_radius";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public TorusBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.torus_blocks");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Center point of the torus", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional plane used to orient the torus axis", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_MAJOR_RADIUS_ID, "Major Radius", "Distance from center to tube center", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MINOR_RADIUS_ID, "Minor Radius", "Tube radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Blocks composing the torus", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Generates a torus block volume with optional plane-based orientation";
    }

    @Override
    public String getDisplayName() {
        return "Torus (Blocks)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object majorRObj = inputValues.get(INPUT_MAJOR_RADIUS_ID);
        Object minorRObj = inputValues.get(INPUT_MINOR_RADIUS_ID);

        BlockPosList result = new BlockPosList();

        if (centerObj instanceof BlockPos center &&
            majorRObj instanceof Number majorRadiusObj &&
            minorRObj instanceof Number minorRadiusObj) {

            double majorRadius = Math.max(1.0d, majorRadiusObj.doubleValue());
            double minorRadius = Math.max(1.0d, minorRadiusObj.doubleValue());

            Vector3d axis = new Vector3d(0.0d, 1.0d, 0.0d);
            if (planeObj instanceof PlaneData planeData) {
                axis = planeData.getNormal();
                if (axis.lengthSquared() < 1e-9) {
                    axis.set(0.0d, 1.0d, 0.0d);
                } else {
                    axis.normalize();
                }
            }

            Vector3d tangent = buildOrthogonalBasisVector(axis);
            Vector3d bitangent = new Vector3d(axis).cross(tangent).normalize();

            int bound = (int) Math.ceil(majorRadius + minorRadius);
            int cx = center.getX();
            int cy = center.getY();
            int cz = center.getZ();
            double minorRadiusSquared = minorRadius * minorRadius;

            for (int dx = -bound; dx <= bound; dx++) {
                for (int dy = -bound; dy <= bound; dy++) {
                    for (int dz = -bound; dz <= bound; dz++) {
                        Vector3d relative = new Vector3d(dx, dy, dz);

                        double localX = relative.dot(tangent);
                        double localY = relative.dot(bitangent);
                        double localZ = relative.dot(axis);

                        double radialDistance = Math.sqrt(localX * localX + localY * localY);
                        double torusEquation = (radialDistance - majorRadius) * (radialDistance - majorRadius)
                            + localZ * localZ;

                        if (torusEquation <= minorRadiusSquared) {
                            result.add(new BlockPos(cx + dx, cy + dy, cz + dz));
                        }
                    }
                }
            }
        }

        outputValues.put(OUTPUT_BLOCKS_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
    }

    private Vector3d buildOrthogonalBasisVector(Vector3d axis) {
        Vector3d reference = Math.abs(axis.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);

        Vector3d tangent = reference.cross(axis, new Vector3d());
        if (tangent.lengthSquared() < 1e-9) {
            tangent = new Vector3d(0.0d, 0.0d, 1.0d);
        }
        return tangent.normalize();
    }

    @Override
    public Object getNodeState() {
        return new HashMap<String, Object>();
    }

    @Override
    public void setNodeState(Object state) {
        // no custom state
    }
}
