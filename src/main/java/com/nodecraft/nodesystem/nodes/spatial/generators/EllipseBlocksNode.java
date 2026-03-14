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
 * 椭圆生成器：在指定平面生成 2D 椭圆（实心或轮廓）的方块坐标列表。
 * 分类：spatial.generators
 */
@NodeInfo(
    id = "spatial.generators.ellipse_blocks",
    displayName = "椭圆",
    description = "生成二维椭圆区域的坐标列表",
    category = "spatial.generators"
)
public class EllipseBlocksNode extends BaseNode {

    public enum Plane { XY, YZ, XZ }

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_A_ID = "input_radius_a";
    private static final String INPUT_RADIUS_B_ID = "input_radius_b";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";

    private boolean filled = true;
    private Plane plane = Plane.XZ;

    public EllipseBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.ellipse_blocks");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "椭圆中心", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_RADIUS_A_ID, "Radius A", "半长轴（格）", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_B_ID, "Radius B", "半短轴（格）", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "椭圆上的方块坐标", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() { return "生成二维椭圆区域的坐标列表"; }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList result = new BlockPosList();
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object raObj = inputValues.get(INPUT_RADIUS_A_ID);
        Object rbObj = inputValues.get(INPUT_RADIUS_B_ID);

        if (!(centerObj instanceof BlockPos) || !(raObj instanceof Number) || !(rbObj instanceof Number)) {
            outputValues.put(OUTPUT_BLOCKS_ID, result);
            return;
        }

        BlockPos center = (BlockPos) centerObj;
        double ra = Math.max(0.5, ((Number) raObj).doubleValue());
        double rb = Math.max(0.5, ((Number) rbObj).doubleValue());
        int ia = (int) Math.ceil(ra);
        int ib = (int) Math.ceil(rb);
        double ra2 = ra * ra;
        double rb2 = rb * rb;

        int cx = center.getX(), cy = center.getY(), cz = center.getZ();

        if (plane == Plane.XZ) {
            for (int dx = -ia; dx <= ia; dx++) {
                for (int dz = -ib; dz <= ib; dz++) {
                    double n = (double) (dx * dx) / ra2 + (double) (dz * dz) / rb2;
                    if (filled ? n <= 1.0 : (n >= 0.90 && n <= 1.10)) {
                        result.add(new BlockPos(cx + dx, cy, cz + dz));
                    }
                }
            }
        } else if (plane == Plane.XY) {
            for (int dx = -ia; dx <= ia; dx++) {
                for (int dy = -ib; dy <= ib; dy++) {
                    double n = (double) (dx * dx) / ra2 + (double) (dy * dy) / rb2;
                    if (filled ? n <= 1.0 : (n >= 0.90 && n <= 1.10)) {
                        result.add(new BlockPos(cx + dx, cy + dy, cz));
                    }
                }
            }
        } else {
            for (int dy = -ia; dy <= ia; dy++) {
                for (int dz = -ib; dz <= ib; dz++) {
                    double n = (double) (dy * dy) / ra2 + (double) (dz * dz) / rb2;
                    if (filled ? n <= 1.0 : (n >= 0.90 && n <= 1.10)) {
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
