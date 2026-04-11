package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Tangent Node: Computes the tangent of an angle (in radians).
 */
@NodeInfo(
    id = "math.trigonometry.tan",
    displayName = "正切函数 (Tan)",
    description = "计算角度的正切值（输入为弧度）",
    category = "math.trigonometry"
)
public class TangentNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_ANGLE_ID = "input_angle_rad";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_TANGENT_ID = "output_tangent";

    // --- 构造函数 ---
    public TangentNode() {
        super(UUID.randomUUID(), "math.trigonometry.tan");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle (rad)", "Input angle in radians", NodeDataType.ANY, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_TANGENT_ID, "Tangent", "Result tan(Angle)", NodeDataType.DOUBLE, this));
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object val = inputValues.get(INPUT_ANGLE_ID);

        // 检查输入是否为数字
        if (val instanceof Number) {
            double angleRad = ((Number) val).doubleValue();
            
            // 虽然 Math.tan() 会处理接近 pi/2 + k*pi 的情况（返回大的正/负值），
            // 但理论上是无穷大。根据需要可以添加检查或保持 Math.tan() 的行为。
            double result = Math.tan(angleRad);
            outputValues.put(OUTPUT_TANGENT_ID, result);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_TANGENT_ID, Double.NaN);
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---

    @Override
    public String getDescription() {
        return "Outputs the tangent of the input angle (in radians).";
    }

    @Override
    public String getDisplayName() {
        return "Tangent (Tan)";
    }
} 
