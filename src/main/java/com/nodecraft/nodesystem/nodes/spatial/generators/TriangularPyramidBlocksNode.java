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
 * Triangular Pyramid (Blocks) 节点: 生成一般三棱锥（非正四面体）区域的坐标列表
 * 用户可以自定义底面三角形尺寸和高度
 */
@NodeInfo(
    id = "spatial.generators.triangular_pyramid_blocks",
    displayName = "三棱锥生成器",
    description = "生成三棱锥区域的坐标列表，可自定义底边和高度",
    category = "spatial.generators"
)
public class TriangularPyramidBlocksNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_BASE_SIZE_ID = "input_base_size";
    private static final String INPUT_HEIGHT_ID = "input_height";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public TriangularPyramidBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.triangular_pyramid_blocks");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Base Center", "底面中心点", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_BASE_SIZE_ID, "Base Size", "底面等边三角形边长", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "棱锥高度", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "组成三棱锥的方块列表", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "方块数量", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "生成三棱锥区域的坐标列表";
    }

    @Override
    public String getDisplayName() {
        return "Triangular Pyramid (Blocks)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object baseSizeObj = inputValues.get(INPUT_BASE_SIZE_ID);
        Object heightObj = inputValues.get(INPUT_HEIGHT_ID);

        BlockPosList result = new BlockPosList();

        if (centerObj instanceof BlockPos &&
            baseSizeObj instanceof Number &&
            heightObj instanceof Number) {

            BlockPos center = (BlockPos) centerObj;
            int baseSize = Math.max(1, ((Number) baseSizeObj).intValue());
            int height = Math.max(1, ((Number) heightObj).intValue());

            int cx = center.getX();
            int cy = center.getY();
            int cz = center.getZ();

            // 底面为等边三角形，中心在(cx, cy, cz)
            // 等边三角形3个顶点（在XZ平面上）
            double circumR = baseSize / Math.sqrt(3.0); // 外接圆半径

            double[][] baseVertices = {
                {0, circumR},                                          // 前方
                {-circumR * Math.sqrt(3.0) / 2.0, -circumR / 2.0},    // 左后
                {circumR * Math.sqrt(3.0) / 2.0, -circumR / 2.0}      // 右后
            };

            // 尖顶在 (cx, cy + height, cz)
            // 逐层生成，每层的三角形按比例缩小
            for (int dy = 0; dy < height; dy++) {
                double ratio = 1.0 - (double) dy / height;

                // 当前层的三角形顶点
                double[][] layerVerts = new double[3][2];
                for (int i = 0; i < 3; i++) {
                    layerVerts[i][0] = baseVertices[i][0] * ratio;
                    layerVerts[i][1] = baseVertices[i][1] * ratio;
                }

                // 扫描当前层的包围盒
                int bound = (int) Math.ceil(circumR * ratio) + 1;

                for (int dx = -bound; dx <= bound; dx++) {
                    for (int dz = -bound; dz <= bound; dz++) {
                        if (isInsideTriangle(dx, dz, layerVerts)) {
                            result.add(new BlockPos(cx + dx, cy + dy, cz + dz));
                        }
                    }
                }
            }
        }

        outputValues.put(OUTPUT_BLOCKS_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
    }

    /**
     * 使用重心坐标法判断点是否在三角形内部
     */
    private boolean isInsideTriangle(double px, double pz, double[][] verts) {
        double x1 = verts[0][0], z1 = verts[0][1];
        double x2 = verts[1][0], z2 = verts[1][1];
        double x3 = verts[2][0], z3 = verts[2][1];

        double denom = (z2 - z3) * (x1 - x3) + (x3 - x2) * (z1 - z3);
        if (Math.abs(denom) < 1e-10) return false;

        double a = ((z2 - z3) * (px - x3) + (x3 - x2) * (pz - z3)) / denom;
        double b = ((z3 - z1) * (px - x3) + (x1 - x3) * (pz - z3)) / denom;
        double c = 1.0 - a - b;

        // 使用 +0.5 的容差来补偿方块离散化
        double tolerance = 0.5 / Math.max(1, Math.max(Math.abs(x1 - x2), Math.abs(x1 - x3)));
        return a >= -tolerance && b >= -tolerance && c >= -tolerance;
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
