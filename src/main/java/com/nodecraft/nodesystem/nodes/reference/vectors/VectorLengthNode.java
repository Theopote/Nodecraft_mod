package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;
import com.nodecraft.nodesystem.api.NodeInfo;

/**
 * Vector Length Node: Computes the length (magnitude) of a vector.
 */
@NodeInfo(
    id = "reference.vectors.vector_length",
    displayName = "向量长度",
    description = "计算向量的长度（模长）",
    category = "reference.vectors"
)
public class VectorLengthNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_VECTOR_ID = "input_vector";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_LENGTH_ID = "output_length";

    // --- 构造函数 ---
    public VectorLengthNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_length");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector", NodeDataType.VECTOR, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Length of the vector", NodeDataType.DOUBLE, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Outputs the length (magnitude) of the input vector.";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Vector Length";
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object val = inputValues.get(INPUT_VECTOR_ID);

        // 检查输入是否为 Vec3d
        if (val instanceof Vec3d) {
            Vec3d vector = (Vec3d) val;
            
            double length = vector.length();
            
            // 设置输出值
            outputValues.put(OUTPUT_LENGTH_ID, length);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_LENGTH_ID, 0.0); // 长度为 0 或 NaN
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---
} 
