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
 * Offset Coordinates 节点: 对坐标列表应用偏移
 */
@NodeInfo(
    id = "transform.basic_transforms.move_points",
    displayName = "坐标偏移",
    description = "对坐标列表中的每个坐标应用偏移量",
    category = "transform.basic_transforms"
)
public class OffsetCoordinatesNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_OFFSET_VECTOR_ID = "input_offset_vector";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";

    // --- 构造函数 ---
    public OffsetCoordinatesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.move_points");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "The coordinates to offset", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_OFFSET_VECTOR_ID, "Offset Vector", 
                "Vector to translate by", NodeDataType.VECTOR, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", 
                "Offset coordinates", NodeDataType.BLOCK_LIST, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Translates a list of coordinates by a vector";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Offset Coordinates";
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object offsetObj = inputValues.get(INPUT_OFFSET_VECTOR_ID);
        
        // 默认空的坐标列表
        BlockPosList result = new BlockPosList();
        
        // 检查输入是否为方块坐标列表
        if (coordinatesObj instanceof BlockPosList && offsetObj instanceof Vector3d) {
            BlockPosList coordinates = (BlockPosList) coordinatesObj;
            Vector3d offset = (Vector3d) offsetObj;
            
            // 获取整数偏移量（四舍五入到最接近的整数）
            int offsetX = (int) Math.round(offset.x);
            int offsetY = (int) Math.round(offset.y);
            int offsetZ = (int) Math.round(offset.z);
            
            // 为每个坐标应用偏移
            for (BlockPos pos : coordinates) {
                BlockPos offsetPos = new BlockPos(
                    pos.getX() + offsetX,
                    pos.getY() + offsetY,
                    pos.getZ() + offsetZ
                );
                result.add(offsetPos);
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_COORDINATES_ID, result);
    }
} 
