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
 * 半圆生成器：在指定平面生成半圆（圆心、半径、方向）的方块坐标列表。
 * 分类：spatial.generators
 */
@NodeInfo(
    id = "spatial.generators.semicircle_blocks",
    displayName = "半圆",
    description = "生成半圆弧上的方块坐标列表",
    category = "spatial.generators"
)
public class SemicircleBlocksNode extends BaseNode {

    /** 半圆开口方向 */
    public enum Half { RIGHT, DOWN, LEFT, UP }

    public enum Plane { XY, YZ, XZ }

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";

    private Half half = Half.RIGHT;
    private Plane plane = Plane.XZ;

    public SemicircleBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.semicircle_blocks");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "圆心", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "半径（格）", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "半圆上的方块坐标", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() { return "生成半圆弧上的方块坐标列表"; }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList result = new BlockPosList();
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);

        if (!(centerObj instanceof BlockPos) || !(radiusObj instanceof Number)) {
            outputValues.put(OUTPUT_BLOCKS_ID, result);
            return;
        }

        BlockPos center = (BlockPos) centerObj;
        double r = Math.max(0.5, ((Number) radiusObj).doubleValue());

        double startDeg;
        double endDeg;
        switch (half) {
            case RIGHT: startDeg = -90; endDeg = 90; break;  // 开口向右
            case DOWN:  startDeg = 0;   endDeg = 180; break;
            case LEFT:  startDeg = 90;  endDeg = 270; break;
            case UP:    startDeg = 180; endDeg = 360; break;
            default:   startDeg = -90; endDeg = 90; break;
        }

        double startRad = Math.toRadians(startDeg);
        double endRad = Math.toRadians(endDeg);
        int steps = Math.max(4, (int) (r * Math.PI));
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

    public Half getHalf() { return half; }
    public void setHalf(Half v) { if (half != v) { half = v; markDirty(); } }
    public Plane getPlane() { return plane; }
    public void setPlane(Plane v) { if (plane != v) { plane = v; markDirty(); } }

    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("half", half.name());
        m.put("plane", plane.name());
        return m;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.get("half") instanceof String) try { setHalf(Half.valueOf((String) m.get("half"))); } catch (Exception ignored) {}
            if (m.get("plane") instanceof String) try { setPlane(Plane.valueOf((String) m.get("plane"))); } catch (Exception ignored) {}
        }
    }
}
