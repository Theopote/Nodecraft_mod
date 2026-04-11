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
 * Cross Product Node: Computes the cross product of two vectors (A x B).
 */
@NodeInfo(
    id = "reference.vectors.cross_product",
    displayName = "叉积",
    description = "计算两个向量的叉积（A × B）",
    category = "reference.vectors"
)
public class CrossProductNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_A_ID = "input_vector_a";
    private static final String INPUT_B_ID = "input_vector_b";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_CROSS_PRODUCT_ID = "output_cross_product";

    // --- 构造函数 ---
    public CrossProductNode() {
        super(UUID.randomUUID(), "reference.vectors.cross_product");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_A_ID, "Vector A", "First vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "Vector B", "Second vector", NodeDataType.VECTOR, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_CROSS_PRODUCT_ID, "Cross Product", "Result A x B", NodeDataType.VECTOR, this));
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object valA = inputValues.get(INPUT_A_ID);
        Object valB = inputValues.get(INPUT_B_ID);

        // 检查输入是否为 Vec3d
        if (valA instanceof Vec3d && valB instanceof Vec3d) {
            Vec3d a = (Vec3d) valA;
            Vec3d b = (Vec3d) valB;
            
            Vec3d crossProduct = a.crossProduct(b);
            
            // 设置输出值
            outputValues.put(OUTPUT_CROSS_PRODUCT_ID, crossProduct);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_CROSS_PRODUCT_ID, Vec3d.ZERO); // 或者 null
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---

    @Override
    public String getDescription() {
        return "Outputs the cross product of vectors A and B.";
    }

    @Override
    public String getDisplayName() {
        return "Cross Product (x)";
    }
} 
