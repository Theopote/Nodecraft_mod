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
 * Distance Node: 计算两个点之间的距离
 */
@NodeInfo(
    id = "reference.points.distance_between_points",
    displayName = "距离",
    description = "计算两个点之间的距离",
    category = "reference.points"
)
public class DistanceNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_A_ID = "input_point_a";
    private static final String INPUT_B_ID = "input_point_b";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_DISTANCE_ID = "output_distance";

    // --- 构造函数 ---
    public DistanceNode() {
        super(UUID.randomUUID(), "reference.points.distance_between_points");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_A_ID, "Point A", "First point", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "Point B", "Second point", NodeDataType.VECTOR, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Distance between A and B", NodeDataType.DOUBLE, this));
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
            
            double distance = a.distanceTo(b);
            
            // 设置输出值
            outputValues.put(OUTPUT_DISTANCE_ID, distance);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0); // 或者 NaN
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---

    @Override
    public String getDescription() {
        return "Outputs the distance between point A and point B.";
    }

    @Override
    public String getDisplayName() {
        return "Distance";
    }
} 
