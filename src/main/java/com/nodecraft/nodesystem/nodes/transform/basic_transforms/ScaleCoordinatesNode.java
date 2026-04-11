package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import java.util.UUID;

/**
 * Scale Coordinates 节点: 缩放坐标列表
 */
@NodeInfo(
    id = "transform.basic_transforms.scale_points",
    displayName = "坐标缩放",
    description = "以指定中心点为基准缩放坐标列表",
    category = "transform.basic_transforms"
)
public class ScaleCoordinatesNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_SCALE_FACTOR_ID = "input_scale_factor";
    private static final String INPUT_SCALE_VECTOR_ID = "input_scale_vector";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";

    // --- 节点属性 ---
    private boolean useUniformScaling = true; // 默认使用统一缩放

    // --- 构造函数 ---
    public ScaleCoordinatesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.scale_points");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "The coordinates to scale", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", 
                "Scaling center point", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_SCALE_FACTOR_ID, "Scale Factor", 
                "Uniform scaling factor", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SCALE_VECTOR_ID, "Scale Vector", 
                "Non-uniform scaling vector (XYZ)", NodeDataType.VECTOR, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", 
                "Scaled coordinates", NodeDataType.BLOCK_LIST, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Scales a list of coordinates relative to a center point";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Scale Coordinates";
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object scaleFactorObj = inputValues.get(INPUT_SCALE_FACTOR_ID);
        Object scaleVectorObj = inputValues.get(INPUT_SCALE_VECTOR_ID);
        
        // 默认空的坐标列表
        BlockPosList result = new BlockPosList();
        
        // 检查基本输入是否合法
        if (coordinatesObj instanceof BlockPosList && centerObj instanceof BlockPos) {
            BlockPosList coordinates = (BlockPosList) coordinatesObj;
            BlockPos centerPos = (BlockPos) centerObj;
            
            // 创建中心点向量
            Vector3d center = new Vector3d(centerPos.getX(), centerPos.getY(), centerPos.getZ());
            
            // 判断使用统一缩放还是非统一缩放
            Vector3d scale;
            if (useUniformScaling && scaleFactorObj instanceof Number) {
                // 统一缩放 - 使用单一缩放因子
                double factor = ((Number) scaleFactorObj).doubleValue();
                scale = new Vector3d(factor, factor, factor);
            } else if (scaleVectorObj instanceof Vector3d) {
                // 非统一缩放 - 使用缩放向量
                scale = (Vector3d) scaleVectorObj;
            } else {
                // 未提供有效的缩放因子，使用默认值1（不缩放）
                scale = new Vector3d(1, 1, 1);
            }
            
            // 应用缩放变换到每个坐标
            for (BlockPos pos : coordinates) {
                // 转换为双精度向量
                Vector3d posVec = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
                
                // 平移到原点
                posVec.sub(center);
                
                // 应用缩放
                posVec.mul(scale);
                
                // 平移回原位置
                posVec.add(center);
                
                // 转换回BlockPos（四舍五入到最接近的整数）
                BlockPos scaledPos = new BlockPos(
                    (int) Math.round(posVec.x),
                    (int) Math.round(posVec.y),
                    (int) Math.round(posVec.z)
                );
                
                // 添加到结果列表
                result.add(scaledPos);
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_COORDINATES_ID, result);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isUseUniformScaling() {
        return useUniformScaling;
    }
    
    public void setUseUniformScaling(boolean useUniformScaling) {
        this.useUniformScaling = useUniformScaling;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("useUniformScaling", useUniformScaling);
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("useUniformScaling")) {
                Object useUniformObj = stateMap.get("useUniformScaling");
                if (useUniformObj instanceof Boolean) {
                    setUseUniformScaling((Boolean) useUniformObj);
                }
            }
        }
    }
} 
