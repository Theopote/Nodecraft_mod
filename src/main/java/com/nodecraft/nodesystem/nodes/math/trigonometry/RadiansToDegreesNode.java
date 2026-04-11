package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * RadiansToDegrees Node: Converts an angle from radians to degrees.
 */
@NodeInfo(
    id = "math.trigonometry.rad_to_deg",
    displayName = "弧度转角度",
    description = "将角度从弧度转换为度数",
    category = "math.trigonometry"
)
public class RadiansToDegreesNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_RADIANS_ID = "input_radians";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_DEGREES_ID = "output_degrees";

    // --- 构造函数 ---
    public RadiansToDegreesNode() {
        super(UUID.randomUUID(), "math.trigonometry.rad_to_deg");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_RADIANS_ID, "Radians", "Angle in radians", NodeDataType.ANY, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_DEGREES_ID, "Degrees", "Angle in degrees", NodeDataType.DOUBLE, this));
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object val = inputValues.get(INPUT_RADIANS_ID);

        // 检查输入是否为数字
        if (val instanceof Number) {
            double radians = ((Number) val).doubleValue();
            double degrees = Math.toDegrees(radians);
            outputValues.put(OUTPUT_DEGREES_ID, degrees);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_DEGREES_ID, Double.NaN);
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---

    @Override
    public String getDescription() {
        return "Converts an angle from radians to degrees.";
    }

    @Override
    public String getDisplayName() {
        return "Radians to Degrees";
    }
} 
