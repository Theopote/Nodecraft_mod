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
 * Vector Scalar Divide Node: 向量除以标量 (Vector / Scalar)
 */
@NodeInfo(
    id = "reference.vectors.vector_scalar_divide",
    displayName = "向量标量除法",
    description = "向量除以标量 (Vector / Scalar)",
    category = "reference.vectors"
)
public class VectorScalarDivideNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_SCALAR_ID = "input_scalar"; // Divisor

    // --- 输出端口 IDs ---
    private static final String OUTPUT_QUOTIENT_ID = "output_vector_quotient";

    // --- 构造函数 ---
    public VectorScalarDivideNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_scalar_divide");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector (Dividend)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_SCALAR_ID, "Scalar", "Scalar value (Divisor)", NodeDataType.ANY, this)); 

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_QUOTIENT_ID, "Scaled Vector", "Result V / s", NodeDataType.VECTOR, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Outputs the vector divided by the scalar.";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Vector Scale (/)";
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
            
            // 检查除零
             if (Math.abs(scalar) < 1e-10) {
                 // 除零：可以返回原向量、零向量或标记错误 (例如输出 null 或 NaN 向量？Vec3d 没有 NaN)
                 // 返回零向量可能是安全的默认行为
                 outputValues.put(OUTPUT_QUOTIENT_ID, Vec3d.ZERO); 
             } else {
                 // Vec3d 没有 divide 方法，需要用乘法实现
                 Vec3d quotient = vector.multiply(1.0 / scalar);
                 outputValues.put(OUTPUT_QUOTIENT_ID, quotient);
             }
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_QUOTIENT_ID, Vec3d.ZERO); // 或者 null
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---
} 
