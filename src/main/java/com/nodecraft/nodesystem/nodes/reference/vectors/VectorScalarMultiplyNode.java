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
 * Vector Scalar Multiply Node: 向量与标量相乘 (Vector * Scalar)
 */
@NodeInfo(
    id = "reference.vectors.vector_scalar_multiply",
    displayName = "向量标量乘法",
    description = "向量与标量相乘 (Vector * Scalar)",
    category = "reference.vectors"
)
public class VectorScalarMultiplyNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_SCALAR_ID = "input_scalar";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_PRODUCT_ID = "output_vector_product";

    // --- 构造函数 ---
    public VectorScalarMultiplyNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_scalar_multiply");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_SCALAR_ID, "Scalar", "Scalar value", NodeDataType.ANY, this)); // Allow any number type

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_PRODUCT_ID, "Scaled Vector", "Result V * s", NodeDataType.VECTOR, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Outputs the vector multiplied by the scalar.";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Vector Scale (*)";
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object valVector = inputValues.get(INPUT_VECTOR_ID);
        Object valScalar = inputValues.get(INPUT_SCALAR_ID);

        // 检查输入类型
        if (valVector instanceof Vec3d && valScalar instanceof Number) {
            Vec3d vector = (Vec3d) valVector;
            double scalar = ((Number) valScalar).doubleValue();
            
            Vec3d product = vector.multiply(scalar);
            
            // 设置输出值
            outputValues.put(OUTPUT_PRODUCT_ID, product);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_PRODUCT_ID, Vec3d.ZERO); // 或者 null
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---
} 
