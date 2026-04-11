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
 * Deconstruct Vector Node: Outputs the X, Y, and Z components of a vector.
 */
@NodeInfo(
    id = "reference.vectors.deconstruct_vector",
    displayName = "解构向量",
    description = "输出向量的X、Y、Z分量",
    category = "reference.vectors"
)
public class DeconstructVectorNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_VECTOR_ID = "input_vector";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";

    // --- 构造函数 ---
    public DeconstructVectorNode() {
        super(UUID.randomUUID(), "reference.vectors.deconstruct_vector");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector", NodeDataType.VECTOR, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z component", NodeDataType.DOUBLE, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Outputs the X, Y, Z components of the input vector.";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Deconstruct Vector";
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object val = inputValues.get(INPUT_VECTOR_ID);

        // 检查输入是否为 Vec3d
        if (val instanceof Vec3d) {
            Vec3d vector = (Vec3d) val;
            
            // 设置输出值
            outputValues.put(OUTPUT_X_ID, vector.getX());
            outputValues.put(OUTPUT_Y_ID, vector.getY());
            outputValues.put(OUTPUT_Z_ID, vector.getZ());
        } else {
            // 如果输入无效
            outputValues.put(OUTPUT_X_ID, 0.0); // 或者 NaN
            outputValues.put(OUTPUT_Y_ID, 0.0);
            outputValues.put(OUTPUT_Z_ID, 0.0);
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---
} 
