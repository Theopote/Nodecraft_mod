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
 * Vector Subtraction Node: Subtracts vector B from vector A (A - B).
 */
@NodeInfo(
    id = "reference.vectors.vector_subtraction",
    displayName = "向量减法 (-)",
    description = "计算两个向量的差，输出A - B",
    category = "reference.vectors"
)
public class VectorSubtractionNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_A_ID = "input_vector_a"; // Minuend
    private static final String INPUT_B_ID = "input_vector_b"; // Subtrahend

    // --- 输出端口 IDs ---
    private static final String OUTPUT_DIFFERENCE_ID = "output_vector_difference";

    // --- 构造函数 ---
    public VectorSubtractionNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_subtraction");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_A_ID, "Vector A", "Minuend vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "Vector B", "Subtrahend vector", NodeDataType.VECTOR, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_DIFFERENCE_ID, "Difference Vector", "Result A - B", NodeDataType.VECTOR, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Outputs the vector difference A - B.";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Vector Subtraction (-)";
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
            
            Vec3d difference = a.subtract(b);
            
            // 设置输出值
            outputValues.put(OUTPUT_DIFFERENCE_ID, difference);
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_DIFFERENCE_ID, Vec3d.ZERO); // 或者 null
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---
} 
