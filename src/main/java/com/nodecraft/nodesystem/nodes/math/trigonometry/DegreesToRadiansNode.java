package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * DegreesToRadians Node: Converts an angle from degrees to radians.
 */
@NodeInfo(
    id = "math.trigonometry.deg_to_rad",
    displayName = "角度转弧度",
    description = "将角度从度数转换为弧度",
    category = "math.trigonometry"
)
public class DegreesToRadiansNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_DEGREES_ID = "input_degrees";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_RADIANS_ID = "output_radians";

    // --- 构造函数 ---
    public DegreesToRadiansNode() {
        super(UUID.randomUUID(), "math.trigonometry.deg_to_rad");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_DEGREES_ID, "Degrees", "Angle in degrees", NodeDataType.ANY, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_RADIANS_ID, "Radians", "Angle in radians", NodeDataType.DOUBLE, this));
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object val = inputValues.get(INPUT_DEGREES_ID);

        // 检查输入是否为数字
        if (val instanceof Number) {
            double degrees = ((Number) val).doubleValue();
            double radians = Math.toRadians(degrees);
            outputValues.put(OUTPUT_RADIANS_ID, radians);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_RADIANS_ID, Double.NaN);
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---

    @Override
    public String getDescription() {
        return "Converts an angle from degrees to radians.";
    }

    @Override
    public String getDisplayName() {
        return "Degrees to Radians";
    }
} 
