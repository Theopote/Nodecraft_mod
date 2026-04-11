package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * ArcCos Node: Computes the arc cosine of a value (result in radians).
 */
@NodeInfo(
    id = "math.trigonometry.acos",
    displayName = "反余弦函数 (ArcCos)",
    description = "计算输入值的反余弦值（结果以弧度为单位）",
    category = "math.trigonometry"
)
public class ArcCosNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_VALUE_ID = "input_value"; // Range [-1, 1]

    // --- 输出端口 IDs ---
    private static final String OUTPUT_ANGLE_ID = "output_angle_rad";
    
    private String description = "计算输入值的反余弦值（结果以弧度为单位）";

    // --- 构造函数 ---
    public ArcCosNode() {
        super(UUID.randomUUID(), "math.trigonometry.acos");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value [-1, 1]", NodeDataType.ANY, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle (rad)", "Result acos(Value)", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object val = inputValues.get(INPUT_VALUE_ID);

        // 检查输入是否为数字
        if (val instanceof Number) {
            double value = ((Number) val).doubleValue();
            
            // Math.acos 输入范围是 [-1, 1], 超出范围返回 NaN
            double result = Math.acos(value);
            outputValues.put(OUTPUT_ANGLE_ID, result); // result is NaN if value is outside [-1, 1]
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---
} 
