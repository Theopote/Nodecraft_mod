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
    displayName = "Arccosine (ArcCos)",
    description = "计算输入值的反余弦值（结果以弧度为单位）",
    category = "math.trigonometry",
    order = 6
)
public class ArcCosNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_VALUE_ID = "input_value"; // Range [-1, 1]

    // --- 输出端口 IDs ---
    private static final String OUTPUT_ANGLE_ID = "output_angle_rad";
    private static final String OUTPUT_VALID_ID = "output_valid";
    
    private String description = "计算输入值的反余弦值（结果以弧度为单位）";

    // --- 构造函数 ---
    public ArcCosNode() {
        super(UUID.randomUUID(), "math.trigonometry.acos");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value [-1, 1]", NodeDataType.ANY, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle (rad)", "Result acos(Value)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is in [-1, 1]", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object val = inputValues.get(INPUT_VALUE_ID);
        if (!(val instanceof Number number)) {
            outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < -1.0d || value > 1.0d) {
            outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_ANGLE_ID, Math.acos(value));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---
} 
