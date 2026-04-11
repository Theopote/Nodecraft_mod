package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
// 使用 JOML 的 Quaternionf 和 Vector3f 进行旋转计算
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.AxisAngle4f;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Rotate Vector Node: Rotates a vector around an axis by a given angle.
 */
@NodeInfo(
    id = "transform.orientation.rotate_vector",
    displayName = "旋转向量",
    description = "绕指定轴旋转向量",
    category = "transform.orientation"
)
public class RotateVectorNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_ANGLE_ID = "input_angle_rad"; // Angle in radians

    // --- 输出端口 IDs ---
    private static final String OUTPUT_ROTATED_VECTOR_ID = "output_rotated_vector";

    // --- 构造函数 ---
    public RotateVectorNode() {
        super(UUID.randomUUID(), "transform.orientation.rotate_vector");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Vector to rotate", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Axis of rotation (will be normalized)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle (rad)", "Angle of rotation in radians", NodeDataType.ANY, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_ROTATED_VECTOR_ID, "Rotated Vector", "Resulting rotated vector", NodeDataType.VECTOR, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Rotates a vector around an axis by an angle (radians).";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Rotate Vector";
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object valVector = inputValues.get(INPUT_VECTOR_ID);
        Object valAxis = inputValues.get(INPUT_AXIS_ID);
        Object valAngle = inputValues.get(INPUT_ANGLE_ID);

        // 检查输入类型
        if (valVector instanceof Vec3d && valAxis instanceof Vec3d && valAngle instanceof Number) {
            Vec3d vector = (Vec3d) valVector;
            Vec3d axis = (Vec3d) valAxis;
            double angleRad = ((Number) valAngle).doubleValue();
            
            // 检查轴向量是否为零向量
            if (axis.lengthSquared() < 1e-10) {
                 // 如果轴是零向量，旋转无效，直接输出原向量
                 outputValues.put(OUTPUT_ROTATED_VECTOR_ID, vector);
                 return;
            }
            
            // 归一化轴向量
            Vec3d normalizedAxis = axis.normalize();
            
            // 使用 JOML 进行旋转
            // 1. 将 Vec3d 转换为 Vector3f
            Vector3f vecToRotate = new Vector3f((float)vector.x, (float)vector.y, (float)vector.z);
            Vector3f rotationAxis = new Vector3f((float)normalizedAxis.x, (float)normalizedAxis.y, (float)normalizedAxis.z);
            
            // 2. 创建旋转四元数
            // Quaternionf rotation = new Quaternionf().rotateAxis((float)angleRad, rotationAxis);
            // 或者使用 AxisAngle4f
            AxisAngle4f axisAngle = new AxisAngle4f((float)angleRad, rotationAxis);
            Quaternionf rotation = new Quaternionf(axisAngle);
            
            // 3. 应用旋转
            Vector3f rotatedVec = rotation.transform(vecToRotate);
            
            // 4. 将结果转回 Vec3d
            Vec3d result = new Vec3d(rotatedVec.x, rotatedVec.y, rotatedVec.z);
            
            // 设置输出值
            outputValues.put(OUTPUT_ROTATED_VECTOR_ID, result);
            
        } else {
            // 如果输入无效，输出零向量或输入向量
            outputValues.put(OUTPUT_ROTATED_VECTOR_ID, valVector instanceof Vec3d ? (Vec3d)valVector : Vec3d.ZERO);
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---
} 
