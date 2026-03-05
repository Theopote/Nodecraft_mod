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
 * Torus (Blocks) 节点: 生成圆环体区域的坐标列表
 */
@NodeInfo(
    id = "spatial.generators.torus_blocks",
    displayName = "圆环生成器",
    description = "生成圆环（甜甜圈形）区域的坐标列表",
    category = "spatial.generators"
)
public class TorusBlocksNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_MAJOR_RADIUS_ID = "input_major_radius";
    private static final String INPUT_MINOR_RADIUS_ID = "input_minor_radius";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public TorusBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.torus_blocks");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "圆环中心点", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_MAJOR_RADIUS_ID, "Major Radius", "主半径（中心到管道中心的距离）", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MINOR_RADIUS_ID, "Minor Radius", "管道半径（管道截面半径）", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "组成圆环的方块列表", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "方块数量", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "生成圆环（甜甜圈形）区域的坐标列表";
    }

    @Override
    public String getDisplayName() {
        return "Torus (Blocks)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object majorRObj = inputValues.get(INPUT_MAJOR_RADIUS_ID);
        Object minorRObj = inputValues.get(INPUT_MINOR_RADIUS_ID);

        BlockPosList result = new BlockPosList();

        if (centerObj instanceof BlockPos &&
            majorRObj instanceof Number &&
            minorRObj instanceof Number) {

            BlockPos center = (BlockPos) centerObj;
            double R = Math.max(1, ((Number) majorRObj).doubleValue()); // 主半径
            double r = Math.max(1, ((Number) minorRObj).doubleValue()); // 管道半径

            int cx = center.getX();
            int cy = center.getY();
            int cz = center.getZ();

            // 扫描包围盒
            int bound = (int) Math.ceil(R + r);

            for (int dx = -bound; dx <= bound; dx++) {
                for (int dy = -(int) Math.ceil(r); dy <= (int) Math.ceil(r); dy++) {
                    for (int dz = -bound; dz <= bound; dz++) {
                        // 圆环方程:
                        // (sqrt(x^2 + z^2) - R)^2 + y^2 <= r^2
                        double distXZ = Math.sqrt(dx * dx + dz * dz);
                        double distFromTube = (distXZ - R) * (distXZ - R) + dy * dy;

                        if (distFromTube <= r * r) {
                            result.add(new BlockPos(cx + dx, cy + dy, cz + dz));
                        }
                    }
                }
            }
        }

        outputValues.put(OUTPUT_BLOCKS_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<>();
    }

    @Override
    public void setNodeState(Object state) {
        // 无额外状态
    }
}
