package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import java.util.UUID;

/**
 * Mirror Coordinates 节点: 镜像坐标列表
 */
@NodeInfo(
    id = "transform.basic_transforms.mirror_points",
    displayName = "坐标镜像",
    description = "沿指定平面镜像坐标列表",
    category = "transform.basic_transforms"
)
public class MirrorCoordinatesNode extends BaseNode {

    // --- 节点属性 ---
    public enum MirrorPlane {
        XY, YZ, XZ, CUSTOM
    }
    
    private MirrorPlane mirrorPlane = MirrorPlane.XZ; // 默认使用XZ平面(地平面)

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_NORMAL_ID = "input_normal";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";

    // --- 构造函数 ---
    public MirrorCoordinatesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.mirror_points");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "The coordinates to mirror", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", 
                "Mirror plane (optional)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", 
                "Point on mirror plane", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", 
                "Normal vector of mirror plane", NodeDataType.VECTOR, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", 
                "Mirrored coordinates", NodeDataType.BLOCK_LIST, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Mirrors a list of coordinates across a plane";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Mirror Coordinates";
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object pointObj = inputValues.get(INPUT_POINT_ID);
        Object normalObj = inputValues.get(INPUT_NORMAL_ID);
        
        // 默认空的坐标列表
        BlockPosList result = new BlockPosList();
        
        // 检查坐标列表输入是否合法
        if (!(coordinatesObj instanceof BlockPosList)) {
            outputValues.put(OUTPUT_COORDINATES_ID, result);
            return;
        }
        
        BlockPosList coordinates = (BlockPosList) coordinatesObj;
        
        // 确定镜像平面
        PlaneData mirrorPlaneData = null;
        
        // 首选：如果提供了PlaneData对象，直接使用
        if (planeObj instanceof PlaneData) {
            mirrorPlaneData = (PlaneData) planeObj;
        } 
        // 次选：如果提供了点和法线，创建平面
        else if (pointObj instanceof BlockPos && normalObj instanceof Vector3d) {
            BlockPos point = (BlockPos) pointObj;
            Vector3d normal = ((Vector3d) normalObj).normalize(); // 标准化法线向量
            Vector3d pointVec = new Vector3d(point.getX(), point.getY(), point.getZ());
            mirrorPlaneData = new PlaneData(pointVec, normal);
        } 
        // 最后：根据预设的mirrorPlane创建一个默认平面
        else {
            // 创建一个默认的镜像平面，基于枚举值
            Vector3d origin = new Vector3d(0, 0, 0);
            Vector3d normal;
            
            switch (mirrorPlane) {
                case XY:
                    normal = new Vector3d(0, 0, 1); // Z轴为法线
                    break;
                case YZ:
                    normal = new Vector3d(1, 0, 0); // X轴为法线
                    break;
                case XZ:
                default:
                    normal = new Vector3d(0, 1, 0); // Y轴为法线
                    break;
            }
            
            mirrorPlaneData = new PlaneData(origin, normal);
        }
        
        // 应用镜像变换
        for (BlockPos pos : coordinates) {
            // 转换为双精度向量
            Vector3d posVec = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
            
            // 在平面上进行镜像投影
            Vector3d mirroredVec = mirrorPoint(posVec, mirrorPlaneData);
            
            // 转换回BlockPos（四舍五入到最接近的整数）
            BlockPos mirroredPos = new BlockPos(
                (int) Math.round(mirroredVec.x),
                (int) Math.round(mirroredVec.y),
                (int) Math.round(mirroredVec.z)
            );
            
            // 添加到结果列表
            result.add(mirroredPos);
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_COORDINATES_ID, result);
    }
    
    /**
     * 在平面上镜像一个点
     * @param point 要镜像的点
     * @param plane 镜像平面
     * @return 镜像后的点
     */
    private Vector3d mirrorPoint(Vector3d point, PlaneData plane) {
        // 获取平面法线
        Vector3d normal = plane.getNormal();
        
        // 获取平面上的一个点
        Vector3d planePoint = plane.getPoint();
        
        // 计算点到平面的有符号距离
        double distance = plane.signedDistanceTo(point);
        
        // 沿法线方向平移两倍的距离
        Vector3d displacement = new Vector3d(normal).mul(2 * distance);
        
        // 从原点减去位移得到镜像点
        Vector3d result = new Vector3d(point).sub(displacement);
        
        return result;
    }
    
    // --- Getters/Setters for Properties ---
    
    public MirrorPlane getMirrorPlane() {
        return mirrorPlane;
    }
    
    public void setMirrorPlane(MirrorPlane plane) {
        this.mirrorPlane = plane;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("mirrorPlane", mirrorPlane.name());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("mirrorPlane")) {
                Object planeObj = stateMap.get("mirrorPlane");
                if (planeObj instanceof String) {
                    try {
                        setMirrorPlane(MirrorPlane.valueOf((String) planeObj));
                    } catch (IllegalArgumentException e) {
                        // 忽略无效的枚举值
                    }
                }
            }
        }
    }
} 
