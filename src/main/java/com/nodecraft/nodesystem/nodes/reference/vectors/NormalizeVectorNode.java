package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Normalize Vector Node: Normalizes a vector to unit length.
 */
@NodeInfo(
    id = "reference.vectors.normalize_vector",
    displayName = "向量归一化",
    description = "将向量归一化为单位长度",
    category = "reference.vectors"
)
public class NormalizeVectorNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_VECTOR_ID = "input_vector";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_NORMALIZED_ID = "output_normalized_vector";

    // --- 构造函数 ---
    public NormalizeVectorNode() {
        super(UUID.randomUUID(), "reference.vectors.normalize_vector");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector", NodeDataType.VECTOR, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_NORMALIZED_ID, "Normalized", "Normalized vector", NodeDataType.VECTOR, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Outputs the normalized (unit length) version of the input vector.";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Normalize Vector";
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object val = inputValues.get(INPUT_VECTOR_ID);

        // 检查输入是否为 Vec3d
        if (val instanceof Vec3d) {
            Vec3d vector = (Vec3d) val;
            
            // Vec3d.normalize() 处理零向量（返回零向量）
            Vec3d normalized = vector.normalize();
            
            // 设置输出值
            outputValues.put(OUTPUT_NORMALIZED_ID, normalized);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_NORMALIZED_ID, Vec3d.ZERO); // 或者 null
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---
} 
