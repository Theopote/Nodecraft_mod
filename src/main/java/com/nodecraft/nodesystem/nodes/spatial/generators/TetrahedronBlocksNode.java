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
 * Regular Tetrahedron (Blocks) 节点: 生成正四面体区域的坐标列表
 * 正四面体以中心点为基准，通过edge长度定义大小。
 * 使用半空间交集法来判断点是否在正四面体内部。
 */
@NodeInfo(
    id = "spatial.generators.tetrahedron_blocks",
    displayName = "正四面体生成器",
    description = "生成正四面体区域的坐标列表",
    category = "spatial.generators"
)
public class TetrahedronBlocksNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_SIZE_ID = "input_size";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public TetrahedronBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.tetrahedron_blocks");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "正四面体中心点", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_SIZE_ID, "Edge Length", "棱长", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "组成正四面体的方块列表", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "方块数量", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "生成正四面体区域的坐标列表";
    }

    @Override
    public String getDisplayName() {
        return "Tetrahedron (Blocks)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object sizeObj = inputValues.get(INPUT_SIZE_ID);

        BlockPosList result = new BlockPosList();

        if (centerObj instanceof BlockPos && sizeObj instanceof Number) {
            BlockPos center = (BlockPos) centerObj;
            int edgeLength = Math.max(1, ((Number) sizeObj).intValue());

            int cx = center.getX();
            int cy = center.getY();
            int cz = center.getZ();

            // 正四面体的4个顶点 (以中心为原点，外接球半径 = edge * sqrt(6)/4)
            double circumR = edgeLength * Math.sqrt(6.0) / 4.0;

            // 正四面体4个顶点坐标（标准朝向）
            // V0 = (0, circumR, 0)                    -- 顶部
            // V1 = (0, -circumR/3, 2*circumR*sqrt(2)/3) -- 前方
            // V2 = (-circumR*sqrt(6)/3, -circumR/3, -circumR*sqrt(2)/3) -- 左后
            // V3 = (circumR*sqrt(6)/3, -circumR/3, -circumR*sqrt(2)/3)  -- 右后
            double h = circumR;
            double hBottom = circumR / 3.0;
            double frontZ = 2.0 * circumR * Math.sqrt(2.0) / 3.0;
            double backZ = -circumR * Math.sqrt(2.0) / 3.0;
            double sideX = circumR * Math.sqrt(6.0) / 3.0;

            double[][] vertices = {
                {0, h, 0},                    // V0: 上
                {0, -hBottom, frontZ},         // V1: 前
                {-sideX, -hBottom, backZ},     // V2: 左后
                {sideX, -hBottom, backZ}       // V3: 右后
            };

            // 计算4个面的法线和d值（面朝内）
            // 面i由除顶点i以外的3个顶点构成
            int[][] faces = {{1, 2, 3}, {0, 3, 2}, {0, 1, 3}, {0, 2, 1}};

            double[][] normals = new double[4][3];
            double[] dValues = new double[4];

            for (int f = 0; f < 4; f++) {
                double[] a = vertices[faces[f][0]];
                double[] b = vertices[faces[f][1]];
                double[] c = vertices[faces[f][2]];

                // 边向量
                double[] ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
                double[] ac = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};

                // 法线 = ab × ac
                normals[f][0] = ab[1] * ac[2] - ab[2] * ac[1];
                normals[f][1] = ab[2] * ac[0] - ab[0] * ac[2];
                normals[f][2] = ab[0] * ac[1] - ab[1] * ac[0];

                // d = normal · a
                dValues[f] = normals[f][0] * a[0] + normals[f][1] * a[1] + normals[f][2] * a[2];

                // 确保法线朝内（中心在正侧）
                // 中心在原点(0,0,0)，所以检查 normal · (0,0,0) - d 的符号
                if (-dValues[f] < 0) {
                    normals[f][0] = -normals[f][0];
                    normals[f][1] = -normals[f][1];
                    normals[f][2] = -normals[f][2];
                    dValues[f] = -dValues[f];
                }
            }

            // 扫描包围盒
            int bound = (int) Math.ceil(circumR) + 1;

            for (int dx = -bound; dx <= bound; dx++) {
                for (int dy = -bound; dy <= bound; dy++) {
                    for (int dz = -bound; dz <= bound; dz++) {
                        // 检查点是否在所有4个面的内侧
                        boolean inside = true;
                        for (int f = 0; f < 4; f++) {
                            double dot = normals[f][0] * dx + normals[f][1] * dy + normals[f][2] * dz;
                            if (dot > dValues[f] + 0.5) { // +0.5 补偿方块离散化
                                inside = false;
                                break;
                            }
                        }
                        if (inside) {
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
