package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Cosine Node: Computes the cosine of an angle (in radians).
 */
@NodeInfo(
    id = "math.trigonometry.cos",
    displayName = "余弦函数 (Cos)",
    description = "计算角度的余弦值（输入为弧度）",
    category = "math.trigonometry"
)
public class CosineNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_ANGLE_ID = "input_angle_rad";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_COSINE_ID = "output_cosine";

    // --- 构造函数 ---
    public CosineNode() {
        super(UUID.randomUUID(), "math.trigonometry.cos");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle (rad)", "Input angle in radians", NodeDataType.ANY, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_COSINE_ID, "Cosine", "Result cos(Angle)", NodeDataType.DOUBLE, this));
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object val = inputValues.get(INPUT_ANGLE_ID);

        // 检查输入是否为数字
        if (val instanceof Number) {
            double angleRad = ((Number) val).doubleValue();
            double result = Math.cos(angleRad);
            outputValues.put(OUTPUT_COSINE_ID, result);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_COSINE_ID, Double.NaN);
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---

    @Override
    public String getDescription() {
        return "Outputs the cosine of the input angle (in radians).";
    }

    @Override
    public String getDisplayName() {
        return "Cosine (Cos)";
    }
} 
