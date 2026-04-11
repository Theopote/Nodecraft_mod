package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * ArcTan Node: Computes the arc tangent of a value (result in radians).
 */
@NodeInfo(
    id = "math.trigonometry.atan",
    displayName = "反正切函数 (ArcTan)",
    description = "计算输入值的反正切值（结果以弧度为单位）",
    category = "math.trigonometry"
)
public class ArcTanNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_VALUE_ID = "input_value";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_ANGLE_ID = "output_angle_rad";

    // --- 构造函数 ---
    public ArcTanNode() {
        super(UUID.randomUUID(), "math.trigonometry.atan");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value", NodeDataType.ANY, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle (rad)", "Result atan(Value)", NodeDataType.DOUBLE, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Outputs the arc tangent of the input value (in radians).";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Arc Tangent (Atan)";
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object val = inputValues.get(INPUT_VALUE_ID);

        // 检查输入是否为数字
        if (val instanceof Number) {
            double value = ((Number) val).doubleValue();
            double result = Math.atan(value);
            outputValues.put(OUTPUT_ANGLE_ID, result);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---
} 
