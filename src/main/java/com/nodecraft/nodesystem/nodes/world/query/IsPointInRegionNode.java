package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Is Point in Region 节点: 检查点是否在区域内
 */
@NodeInfo(
    id = "world.query.is_point_in_region",
    displayName = "点在区域内",
    description = "检查点是否在指定区域内",
    category = "world.query",
    order = 4
)
public class IsPointInRegionNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "检查点是否在区域内";

    // --- 输入端口 IDs ---
    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_REGION_ID = "input_region";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_IS_INSIDE_ID = "output_is_inside";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";

    // --- 构造函数 ---
    public IsPointInRegionNode() {
        super(UUID.randomUUID(), "world.query.is_point_in_region");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", 
                "要检查的点", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", 
                "检查区域", NodeDataType.REGION, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_IS_INSIDE_ID, "Is Inside", 
                "点是否在区域内", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", 
                "点到区域边界的距离（负值表示在区域内）", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object pointObj = inputValues.get(INPUT_POINT_ID);
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        
        // 默认输出值
        boolean isInside = false;
        double distance = Double.POSITIVE_INFINITY;
        
        // 检查输入是否合法
        if (pointObj instanceof BlockPos && regionObj instanceof RegionData) {
            BlockPos point = (BlockPos) pointObj;
            RegionData region = (RegionData) regionObj;
            
            // 确保区域完整
            if (region.isComplete()) {
                // 获取区域的包围盒
                Box box = region.toBox();
                
                // 将方块坐标转换为中心点坐标（这样可以正确计算距离）
                double pointX = point.getX() + 0.5;
                double pointY = point.getY() + 0.5;
                double pointZ = point.getZ() + 0.5;
                
                // 检查点是否在包围盒内
                isInside = box.contains(pointX, pointY, pointZ);
                
                // 计算点到包围盒的距离（负值表示在内部）
                distance = calculateDistanceToBox(pointX, pointY, pointZ, box);
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_IS_INSIDE_ID, isInside);
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
    }
    
    /**
     * 计算点到包围盒的距离
     * @param x 点的X坐标
     * @param y 点的Y坐标
     * @param z 点的Z坐标
     * @param box 包围盒
     * @return 距离（负值表示在内部）
     */
    private double calculateDistanceToBox(double x, double y, double z, Box box) {
        double dx = Math.max(box.minX - x, Math.max(0, x - box.maxX));
        double dy = Math.max(box.minY - y, Math.max(0, y - box.maxY));
        double dz = Math.max(box.minZ - z, Math.max(0, z - box.maxZ));
        
        if (dx == 0 && dy == 0 && dz == 0) {
            // 点在包围盒内，计算到最近面的距离（负值）
            double dxMin = x - box.minX;
            double dxMax = box.maxX - x;
            double dyMin = y - box.minY;
            double dyMax = box.maxY - y;
            double dzMin = z - box.minZ;
            double dzMax = box.maxZ - z;
            
            // 找到最小距离（到最近面的距离）
            double minDistance = Math.min(
                Math.min(dxMin, dxMax),
                Math.min(Math.min(dyMin, dyMax), Math.min(dzMin, dzMax))
            );
            
            return -minDistance; // 返回负值表示在内部
        } else {
            // 点在包围盒外，计算欧几里得距离
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }
} 
