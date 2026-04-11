package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Sine Node: Computes the sine of an angle (in radians).
 */
@NodeInfo(
    id = "math.trigonometry.sin",
    displayName = "正弦函数 (Sin)",
    description = "计算角度的正弦值（输入为弧度）",
    category = "math.trigonometry"
)
public class SineNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_ANGLE_ID = "input_angle_rad";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_SINE_ID = "output_sine";

    // --- 构造函数 ---
    public SineNode() {
        super(UUID.randomUUID(), "math.trigonometry.sin");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle (rad)", "Input angle in radians", NodeDataType.ANY, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_SINE_ID, "Sine", "Result sin(Angle)", NodeDataType.DOUBLE, this));
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object val = inputValues.get(INPUT_ANGLE_ID);

        // 检查输入是否为数字
        if (val instanceof Number) {
            double angleRad = ((Number) val).doubleValue();
            double result = Math.sin(angleRad);
            outputValues.put(OUTPUT_SINE_ID, result);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_SINE_ID, Double.NaN);
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---

    @Override
    public String getDescription() {
        return "Outputs the sine of the input angle (in radians).";
    }

    @Override
    public String getDisplayName() {
        return "Sine (Sin)";
    }
} 
