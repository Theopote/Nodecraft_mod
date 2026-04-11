package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Midpoint Node: 计算两个向量点之间的中点
 */
@NodeInfo(
    id = "reference.points.mid_point",
    displayName = "中点",
    description = "计算两个向量点之间的中点",
    category = "reference.points"
)
public class MidpointNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "计算两个向量点之间的中点";

    // --- 输入端口 IDs ---
    private static final String INPUT_A_ID = "input_point_a";
    private static final String INPUT_B_ID = "input_point_b";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_MIDPOINT_ID = "output_midpoint";

    // --- 构造函数 ---
    public MidpointNode() {
        super(UUID.randomUUID(), "reference.points.mid_point");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_A_ID, "Point A", "First point", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "Point B", "Second point", NodeDataType.VECTOR, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_MIDPOINT_ID, "Midpoint", "The midpoint between A and B", NodeDataType.VECTOR, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object valA = inputValues.get(INPUT_A_ID);
        Object valB = inputValues.get(INPUT_B_ID);

        // 检查输入是否为 Vec3d
        if (valA instanceof Vec3d && valB instanceof Vec3d) {
            Vec3d a = (Vec3d) valA;
            Vec3d b = (Vec3d) valB;
            
            // 计算中点
            Vec3d midpoint = new Vec3d(
                (a.x + b.x) / 2.0,
                (a.y + b.y) / 2.0,
                (a.z + b.z) / 2.0
            );
            
            // 设置输出值
            outputValues.put(OUTPUT_MIDPOINT_ID, midpoint);
        } else {
            // 如果输入无效，输出零向量
            outputValues.put(OUTPUT_MIDPOINT_ID, Vec3d.ZERO);
        }
    }
} 
