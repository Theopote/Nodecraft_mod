package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.TorusBlockGenerator;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.UUID;

/**
 * Generates a torus block volume around a center point.
 *
 * If a plane is provided, the torus is oriented so its axis matches the
 * plane normal. When no center is provided, the node falls back to the
 * plane origin.
 */
@NodeInfo(
    id = "spatial.generators.torus_blocks",
    displayName = "Torus (Blocks)",
    description = "Generates a torus block volume with optional plane-based orientation",
    category = "spatial.generators"
)
public class TorusBlocksNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_MAJOR_RADIUS_ID = "input_major_radius";
    private static final String INPUT_MINOR_RADIUS_ID = "input_minor_radius";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_TORUS_GEOMETRY_ID = "output_torus_geometry";

    public TorusBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.torus_blocks");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Center point of the torus", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional plane used to orient the torus axis", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_MAJOR_RADIUS_ID, "Major Radius", "Distance from center to tube center", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MINOR_RADIUS_ID, "Minor Radius", "Tube radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Blocks composing the torus", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Resolved torus geometry", NodeDataType.TORUS_GEOMETRY, this));
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
        TorusGeometryData geometry = null;

        if (majorRObj instanceof Number majorRadiusObj &&
            minorRObj instanceof Number minorRadiusObj) {

            BlockPos center = resolveCenter(centerObj, planeObj);
            if (center == null) {
                outputValues.put(OUTPUT_BLOCKS_ID, result);
                outputValues.put(OUTPUT_COUNT_ID, 0);
                return;
            }

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

            geometry = new TorusGeometryData(
                new Vector3d(center.getX(), center.getY(), center.getZ()),
                axis,
                majorRadius,
                minorRadius
            );
            TorusBlockGenerator.populateTorus(result, TorusBlockGenerator.createBoundingRegion(geometry), geometry, true);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_TORUS_GEOMETRY_ID, geometry);
    }

    private BlockPos resolveCenter(Object centerObj, Object planeObj) {
        if (centerObj instanceof BlockPos center) {
            return center.toImmutable();
        }

        if (planeObj instanceof PlaneData planeData) {
            Vector3d point = planeData.getPoint();
            return BlockPos.ofFloored(point.x, point.y, point.z);
        }

        return null;
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
