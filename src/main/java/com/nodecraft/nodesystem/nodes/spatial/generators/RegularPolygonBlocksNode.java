package com.nodecraft.nodesystem.nodes.spatial.generators;

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
 * 正多边形生成器：在指定平面生成正多边形（实心或轮廓）的方块坐标列表。
 * 分类：spatial.generators
 */
@NodeInfo(
    id = "spatial.generators.regular_polygon_blocks",
    displayName = "正多边形",
    description = "生成正多边形区域的坐标列表",
    category = "spatial.generators"
)
public class RegularPolygonBlocksNode extends BaseNode {

    public enum Plane { XY, YZ, XZ }

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_SIDES_ID = "input_sides";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";

    private boolean filled = true;
    private Plane plane = Plane.XZ;

    public RegularPolygonBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.regular_polygon_blocks");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "中心", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "外接圆半径（格）", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SIDES_ID, "Sides", "边数（≥3）", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "多边形上的方块坐标", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() { return "生成正多边形区域的坐标列表"; }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList result = new BlockPosList();
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        Object sidesObj = inputValues.get(INPUT_SIDES_ID);

        if (!(centerObj instanceof BlockPos) || !(radiusObj instanceof Number) || !(sidesObj instanceof Number)) {
            outputValues.put(OUTPUT_BLOCKS_ID, result);
            return;
        }

        BlockPos center = (BlockPos) centerObj;
        double r = Math.max(1, ((Number) radiusObj).doubleValue());
        int n = Math.max(3, ((Number) sidesObj).intValue());

        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n - Math.PI / 2;
            xs[i] = r * Math.cos(a);
            ys[i] = r * Math.sin(a);
        }

        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        int rad = (int) Math.ceil(r) + 1;

        if (plane == Plane.XZ) {
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    boolean inside = pointInPolygon(dx, dz, xs, ys);
                    if (filled && inside) {
                        result.add(new BlockPos(cx + dx, cy, cz + dz));
                    } else if (!filled && onPolygonEdge(dx, dz, xs, ys)) {
                        result.add(new BlockPos(cx + dx, cy, cz + dz));
                    }
                }
            }
        } else if (plane == Plane.XY) {
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dy = -rad; dy <= rad; dy++) {
                    boolean inside = pointInPolygon(dx, dy, xs, ys);
                    if (filled && inside) {
                        result.add(new BlockPos(cx + dx, cy + dy, cz));
                    } else if (!filled && onPolygonEdge(dx, dy, xs, ys)) {
                        result.add(new BlockPos(cx + dx, cy + dy, cz));
                    }
                }
            }
        } else {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    boolean inside = pointInPolygon(dy, dz, xs, ys);
                    if (filled && inside) {
                        result.add(new BlockPos(cx, cy + dy, cz + dz));
                    } else if (!filled && onPolygonEdge(dy, dz, xs, ys)) {
                        result.add(new BlockPos(cx, cy + dy, cz + dz));
                    }
                }
            }
        }
        outputValues.put(OUTPUT_BLOCKS_ID, result);
    }

    public static boolean pointInPolygon(double px, double py, double[] xs, double[] ys) {
        int n = xs.length;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if (((ys[i] > py) != (ys[j] > py)) &&
                (px < (xs[j] - xs[i]) * (py - ys[i]) / (ys[j] - ys[i]) + xs[i])) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static boolean onPolygonEdge(double px, double py, double[] xs, double[] ys) {
        int n = xs.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double dist = segmentDist(px, py, xs[i], ys[i], xs[j], ys[j]);
            if (dist <= 0.6) return true;
        }
        return false;
    }

    public static double segmentDist(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-9) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / (len * len)));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
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
