package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Find Blocks 节点: 在 Region 内按 BlockInfo 条件查找 Coordinate 列表。
 */
@NodeInfo(
    id = "world.read.find_blocks",
    displayName = "查找方块",
    description = "在区域内按方块类型查找坐标列表",
    category = "world.read"
)
public class FindBlocksNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "在区域内按方块类型查找坐标列表";
    private int maxResults = 1000; // 最大结果数量，防止过度搜索

    // --- 输入端口 IDs ---
    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_BLOCK_INFO_ID = "input_block_info";
    private static final String INPUT_EXACT_MATCH_ID = "input_exact_match";
    private static final String INPUT_MAX_RESULTS_ID = "input_max_results";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_FOUND_BLOCKS_ID = "output_found_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_FIRST_POS_ID = "output_first_pos";
    private static final String OUTPUT_FOUND_ANY_ID = "output_found_any";

    // --- 构造函数 ---
    public FindBlocksNode() {
        super(UUID.randomUUID(), "world.read.find_blocks");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", 
                "要搜索的区域", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", 
                "要查找的方块信息", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_EXACT_MATCH_ID, "Exact Match", 
                "是否需要完全匹配（包括方块状态）", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_RESULTS_ID, "Max Results", 
                "最大结果数量", NodeDataType.INTEGER, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_FOUND_BLOCKS_ID, "Found Blocks", 
                "找到的方块坐标列表", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", 
                "找到的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FIRST_POS_ID, "First Position", 
                "第一个找到的位置", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ANY_ID, "Found Any", 
                "是否找到任何匹配方块", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        BlockPosList foundBlocks = new BlockPosList();
        int count = 0;
        BlockPos firstPos = null;
        boolean foundAny = false;
        
        // 获取输入值
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object blockInfoObj = inputValues.get(INPUT_BLOCK_INFO_ID);
        
        // 获取布尔值参数，如果没有提供则使用默认值
        boolean exactMatch = true;
        Object exactMatchObj = inputValues.get(INPUT_EXACT_MATCH_ID);
        if (exactMatchObj instanceof Boolean) {
            exactMatch = (Boolean) exactMatchObj;
        }
        
        // 获取最大结果数量
        int maxResultsValue = this.maxResults;
        Object maxResultsObj = inputValues.get(INPUT_MAX_RESULTS_ID);
        if (maxResultsObj instanceof Number) {
            maxResultsValue = ((Number) maxResultsObj).intValue();
            if (maxResultsValue <= 0) {
                maxResultsValue = Integer.MAX_VALUE; // 无限制
            }
        }
        
        // 检查执行上下文、区域和方块信息输入是否有效
        if (context != null && context.getWorld() != null && 
                regionObj instanceof RegionData && blockInfoObj != null) {
            RegionData region = (RegionData) regionObj;
            
            // 确保区域完整且有效
            if (region.isComplete()) {
                BlockPos minCorner = region.getMinCorner();
                BlockPos maxCorner = region.getMaxCorner();
                
                // 遍历区域内的所有方块
                blockSearch:
                for (BlockPos pos : BlockPos.iterate(minCorner, maxCorner)) {
                    BlockPos immutablePos = pos.toImmutable();
                    
                    try {
                        // 获取方块状态
                        Object blockState = context.getWorld().getBlockState(immutablePos);
                        
                        // 匹配逻辑，实际实现中应根据Minecraft API进行正确的比较
                        boolean isMatch = false;
                        
                        if (exactMatch) {
                            // 精确匹配（方块类型和状态都必须匹配）
                            isMatch = blockInfoObj.equals(blockState);
                        } else {
                            // 只匹配方块类型，不考虑状态
                            // 这里需要根据Minecraft API调整实现
                            String blockStateType = blockState.toString().split("\\[")[0];
                            String targetType = blockInfoObj.toString().split("\\[")[0];
                            isMatch = blockStateType.equals(targetType);
                        }
                        
                        if (isMatch) {
                            // 找到匹配方块
                            foundBlocks.add(immutablePos);
                            count++;
                            
                            // 记录第一个找到的位置
                            if (firstPos == null) {
                                firstPos = immutablePos;
                                foundAny = true;
                            }
                            
                            // 如果达到最大结果数量则停止搜索
                            if (count >= maxResultsValue) {
                                break blockSearch;
                            }
                        }
                    } catch (Exception e) {
                        // 记录错误但继续处理其他方块
                        System.err.println("Error checking block at " + pos + ": " + e.getMessage());
                    }
                }
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_FOUND_BLOCKS_ID, foundBlocks);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_FIRST_POS_ID, firstPos);
        outputValues.put(OUTPUT_FOUND_ANY_ID, foundAny);
    }
    
    // --- Getters/Setters for Properties ---
    
    public int getMaxResults() {
        return maxResults;
    }
    
    public void setMaxResults(int maxResults) {
        if (maxResults > 0) {
            this.maxResults = maxResults;
            markDirty();
        }
    }
} 
