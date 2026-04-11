package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.UUID;

/**
 * Rotate Coordinates 节点: 旋转坐标列表
 */
@NodeInfo(
    id = "transform.basic_transforms.rotate_points",
    displayName = "坐标旋转",
    description = "绕指定轴和中心点旋转坐标列表",
    category = "transform.basic_transforms"
)
public class RotateCoordinatesNode extends BaseNode {

    // --- 节点属性 ---
    public enum RotationAxis {
        X_AXIS, Y_AXIS, Z_AXIS, CUSTOM
    }
    
    private RotationAxis rotationAxis = RotationAxis.Y_AXIS; // 默认绕Y轴旋转

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_ANGLE_ID = "input_angle";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";

    // --- 构造函数 ---
    public RotateCoordinatesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.rotate_points");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "The coordinates to rotate", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", 
                "Rotation center point", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", 
                "Rotation axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", 
                "Rotation angle in degrees", NodeDataType.DOUBLE, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", 
                "Rotated coordinates", NodeDataType.BLOCK_LIST, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Rotates a list of coordinates around a point and axis";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Rotate Coordinates";
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object axisObj = inputValues.get(INPUT_AXIS_ID);
        Object angleObj = inputValues.get(INPUT_ANGLE_ID);
        
        // 默认空的坐标列表
        BlockPosList result = new BlockPosList();
        
        // 检查输入是否合法
        if (coordinatesObj instanceof BlockPosList && 
            centerObj instanceof BlockPos && 
            axisObj instanceof Vector3d && 
            angleObj instanceof Number) {
            
            BlockPosList coordinates = (BlockPosList) coordinatesObj;
            BlockPos centerPos = (BlockPos) centerObj;
            Vector3d axis = ((Vector3d) axisObj).normalize(); // 标准化轴向量
            double angleDegrees = ((Number) angleObj).doubleValue();
            double angleRadians = Math.toRadians(angleDegrees);
            
            // 创建中心点向量
            Vector3d center = new Vector3d(centerPos.getX(), centerPos.getY(), centerPos.getZ());
            
            // 创建变换矩阵
            Matrix4d rotationMatrix = new Matrix4d();
            
            // 创建旋转四元数
            Quaterniond rotation = new Quaterniond(new AxisAngle4d(angleRadians, axis.x, axis.y, axis.z));
            
            // 构建变换矩阵：平移到原点 -> 旋转 -> 平移回去
            rotationMatrix.translate(-center.x, -center.y, -center.z)  // 平移到原点
                          .rotate(rotation)                           // 应用旋转
                          .translate(center.x, center.y, center.z);   // 平移回原位置
            
            // 将每个坐标应用旋转变换
            for (BlockPos pos : coordinates) {
                // 转换为双精度向量进行变换
                Vector3d posVec = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
                
                // 应用变换
                Vector3d transformedVec = posVec.mulPosition(rotationMatrix);
                
                // 转换回BlockPos（四舍五入到最接近的整数）
                BlockPos rotatedPos = new BlockPos(
                    (int) Math.round(transformedVec.x),
                    (int) Math.round(transformedVec.y),
                    (int) Math.round(transformedVec.z)
                );
                
                // 添加到结果列表
                result.add(rotatedPos);
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_COORDINATES_ID, result);
    }
    
    // --- Getters/Setters for Properties ---
    
    public RotationAxis getRotationAxis() {
        return rotationAxis;
    }
    
    public void setRotationAxis(RotationAxis axis) {
        this.rotationAxis = axis;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("rotationAxis", rotationAxis.name());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("rotationAxis")) {
                Object axisObj = stateMap.get("rotationAxis");
                if (axisObj instanceof String) {
                    try {
                        setRotationAxis(RotationAxis.valueOf((String) axisObj));
                    } catch (IllegalArgumentException e) {
                        // 忽略无效的枚举值
                    }
                }
            }
        }
    }
} 
