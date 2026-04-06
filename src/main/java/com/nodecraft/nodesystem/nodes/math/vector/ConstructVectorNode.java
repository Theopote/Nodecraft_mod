package com.nodecraft.nodesystem.nodes.math.vector;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import java.util.UUID;

/**
 * Construct Vector Node: Creates a vector from X, Y, and Z components.
 */
@NodeInfo(
    id = "math.vector.construct",
    displayName = "构造向量",
    description = "从X、Y、Z分量创建向量",
    category = "math.vector"
)
public class ConstructVectorNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_VECTOR_ID = "output_vector";

    // --- 构造函数 ---
    public ConstructVectorNode() {
        super(UUID.randomUUID(), "math.vector.construct");
        
        // 创建并添加输入端口 (允许 ANY 数字类型，默认值为 0)
        addInputPort(new BasePort(INPUT_X_ID, "X", "X component", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Y component", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Z component", NodeDataType.ANY, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector", "Resulting vector", NodeDataType.VECTOR, this));
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值，如果输入无效或未连接，则默认为 0.0
        double x = getValueAsDouble(inputValues.get(INPUT_X_ID), 0.0);
        double y = getValueAsDouble(inputValues.get(INPUT_Y_ID), 0.0);
        double z = getValueAsDouble(inputValues.get(INPUT_Z_ID), 0.0);
        
        // 创建 Vec3d
        Vector3d result = new Vector3d(x, y, z);
        
        // 设置输出值
        outputValues.put(OUTPUT_VECTOR_ID, result);
    }
    
    /**
     * Helper method to safely convert an input object to double.
     * @param value The input object.
     * @param defaultValue The value to return if conversion fails.
     * @return The double value or the default value.
     */
    private double getValueAsDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---

    @Override
    public String getDescription() {
        return "Creates a vector from X, Y, Z components.";
    }

    @Override
    public String getDisplayName() {
        return "Construct Vector";
    }
} 
