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
 * 圆弧生成器：在指定平面生成圆弧（圆心、半径、起止角度）的方块坐标列表。
 * 角度为度，0 为 X 正轴方向，逆时针为正。
 * 分类：spatial.generators
 */
@NodeInfo(
    id = "spatial.generators.arc_blocks",
    displayName = "圆弧",
    description = "生成圆弧上的方块坐标列表",
    category = "spatial.generators"
)
public class ArcBlocksNode extends BaseNode {

    public enum Plane { XY, YZ, XZ }

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_START_ANGLE_ID = "input_start_angle";
    private static final String INPUT_END_ANGLE_ID = "input_end_angle";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";

    private Plane plane = Plane.XZ;

    public ArcBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.arc_blocks");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "圆心", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "半径（格）", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "起始角（度，0=右）", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_ANGLE_ID, "End Angle", "结束角（度）", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "圆弧上的方块坐标", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() { return "生成圆弧上的方块坐标列表"; }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList result = new BlockPosList();
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        Object startObj = inputValues.get(INPUT_START_ANGLE_ID);
        Object endObj = inputValues.get(INPUT_END_ANGLE_ID);

        if (!(centerObj instanceof BlockPos) || !(radiusObj instanceof Number)) {
            outputValues.put(OUTPUT_BLOCKS_ID, result);
            return;
        }

        BlockPos center = (BlockPos) centerObj;
        double r = Math.max(0.5, ((Number) radiusObj).doubleValue());
        double startDeg = startObj instanceof Number ? ((Number) startObj).doubleValue() : 0;
        double endDeg = endObj instanceof Number ? ((Number) endObj).doubleValue() : 90;

        double startRad = Math.toRadians(startDeg);
        double endRad = Math.toRadians(endDeg);
        if (endRad <= startRad) endRad += 2 * Math.PI;
        int steps = Math.max(4, (int) (r * Math.abs(endRad - startRad) * 2));
        double step = (endRad - startRad) / steps;

        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        java.util.Set<String> seen = new java.util.HashSet<>();

        for (int i = 0; i <= steps; i++) {
            double a = startRad + i * step;
            int ix = (int) Math.round(r * Math.cos(a));
            int iz = (int) Math.round(r * Math.sin(a));
            String key = ix + "," + iz;
            if (seen.add(key)) {
                if (plane == Plane.XZ) {
                    result.add(new BlockPos(cx + ix, cy, cz + iz));
                } else if (plane == Plane.XY) {
                    result.add(new BlockPos(cx + ix, cy + iz, cz));
                } else {
                    result.add(new BlockPos(cx, cy + ix, cz + iz));
                }
            }
        }
        outputValues.put(OUTPUT_BLOCKS_ID, result);
    }

    public Plane getPlane() { return plane; }
    public void setPlane(Plane v) { if (plane != v) { plane = v; markDirty(); } }

    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("plane", plane.name());
        return m;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map && ((java.util.Map<?, ?>) state).get("plane") instanceof String) {
            try { setPlane(Plane.valueOf((String) ((java.util.Map<?, ?>) state).get("plane"))); } catch (Exception ignored) {}
        }
    }
}
