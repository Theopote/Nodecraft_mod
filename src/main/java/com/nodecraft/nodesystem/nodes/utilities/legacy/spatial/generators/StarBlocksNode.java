package com.nodecraft.nodesystem.nodes.utilities.legacy.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Generates a 2D star polygon (filled or outline) on a selected plane.
 */
@NodeInfo(
    id = "spatial.generators.star_blocks",
    displayName = "Star Generator",
    description = "Generates a star-shaped block pattern with configurable points and radii.",
    category = "utilities.legacy.spatial.generators"
)
public class StarBlocksNode extends BaseNode {

    public enum Plane { XY, YZ, XZ }

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_OUTER_RADIUS_ID = "input_outer_radius";
    private static final String INPUT_INNER_RADIUS_ID = "input_inner_radius";
    private static final String INPUT_POINTS_ID = "input_points";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";

    private boolean filled = true;
    private Plane plane = Plane.XZ;

    public StarBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.star_blocks");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Star center point", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_OUTER_RADIUS_ID, "Outer Radius", "Radius of star tips", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_INNER_RADIUS_ID, "Inner Radius", "Radius of inner vertices", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Number of star points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Generated star blocks", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() { return "Generates a star-shaped block pattern with configurable points and radii."; }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList result = new BlockPosList();
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object roObj = inputValues.get(INPUT_OUTER_RADIUS_ID);
        Object riObj = inputValues.get(INPUT_INNER_RADIUS_ID);
        Object ptsObj = inputValues.get(INPUT_POINTS_ID);

        if (!(centerObj instanceof BlockPos) || !(roObj instanceof Number) || !(riObj instanceof Number) || !(ptsObj instanceof Number)) {
            outputValues.put(OUTPUT_BLOCKS_ID, result);
            return;
        }

        BlockPos center = (BlockPos) centerObj;
        double outer = Math.max(1, ((Number) roObj).doubleValue());
        double inner = Math.max(0, Math.min(outer - 0.5, ((Number) riObj).doubleValue()));
        int points = Math.max(3, ((Number) ptsObj).intValue());

        int n = points * 2;
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n - Math.PI / 2;
            double r = (i % 2 == 0) ? outer : inner;
            xs[i] = r * Math.cos(a);
            ys[i] = r * Math.sin(a);
        }

        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        int rad = (int) Math.ceil(outer) + 1;

        if (plane == Plane.XZ) {
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    boolean inside = RegularPolygonBlocksNode.pointInPolygon(dx, dz, xs, ys);
                    if (filled && inside) {
                        result.add(new BlockPos(cx + dx, cy, cz + dz));
                    } else if (!filled && RegularPolygonBlocksNode.onPolygonEdge(dx, dz, xs, ys)) {
                        result.add(new BlockPos(cx + dx, cy, cz + dz));
                    }
                }
            }
        } else if (plane == Plane.XY) {
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dy = -rad; dy <= rad; dy++) {
                    boolean inside = RegularPolygonBlocksNode.pointInPolygon(dx, dy, xs, ys);
                    if (filled && inside) {
                        result.add(new BlockPos(cx + dx, cy + dy, cz));
                    } else if (!filled && RegularPolygonBlocksNode.onPolygonEdge(dx, dy, xs, ys)) {
                        result.add(new BlockPos(cx + dx, cy + dy, cz));
                    }
                }
            }
        } else {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    boolean inside = RegularPolygonBlocksNode.pointInPolygon(dy, dz, xs, ys);
                    if (filled && inside) {
                        result.add(new BlockPos(cx, cy + dy, cz + dz));
                    } else if (!filled && RegularPolygonBlocksNode.onPolygonEdge(dy, dz, xs, ys)) {
                        result.add(new BlockPos(cx, cy + dy, cz + dz));
                    }
                }
            }
        }
        outputValues.put(OUTPUT_BLOCKS_ID, result);
    }

    public boolean isFilled() { return filled; }
    public void setFilled(boolean v) { if (filled != v) { filled = v; markDirty(); } }
    public Plane getPlane() { return plane; }
    public void setPlane(Plane v) { if (plane != v) { plane = v; markDirty(); } }

    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("filled", filled);
        m.put("plane", plane.name());
        return m;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (Boolean.TRUE.equals(m.get("filled")) || Boolean.FALSE.equals(m.get("filled"))) setFilled((Boolean) m.get("filled"));
            if (m.get("plane") instanceof String) try { setPlane(Plane.valueOf((String) m.get("plane"))); } catch (Exception ignored) {}
        }
    }
}
