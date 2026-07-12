package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Random;
import java.util.UUID;

/**
 * Populate Region 节点: 在指定区域内随机或均匀生成坐标列表
 */
@NodeInfo(
    id = "pattern.surface_volume_distribution.populate_region",
    displayName = "Populate Region",
    description = "在指定区域内随机或均匀生成坐标列表",
    category = "pattern.surface_volume_distribution",
    order = 0
)
public class PopulateRegionNode extends BaseNode {

    // --- 节点属性 ---
    public enum DistributionType {
        RANDOM,      // 随机分布
        UNIFORM      // 均匀分布（网格）
    }
    
    private DistributionType distributionType = DistributionType.RANDOM;

    // --- 输入端口 IDs ---
    private static final String INPUT_MIN_CORNER_ID = "input_min_corner";
    private static final String INPUT_MAX_CORNER_ID = "input_max_corner";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_SPACING_X_ID = "input_spacing_x";
    private static final String INPUT_SPACING_Y_ID = "input_spacing_y";
    private static final String INPUT_SPACING_Z_ID = "input_spacing_z";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";

    // --- 构造函数 ---
    public PopulateRegionNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.populate_region");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_MIN_CORNER_ID, "Min Corner", 
                "区域的最小角坐标", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_MAX_CORNER_ID, "Max Corner", 
                "区域的最大角坐标", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", 
                "要生成的坐标数量", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", 
                "随机种子（仅用于随机分布）", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SPACING_X_ID, "X Spacing", 
                "X轴间距（仅用于均匀分布）", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPACING_Y_ID, "Y Spacing", 
                "Y轴间距（仅用于均匀分布）", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPACING_Z_ID, "Z Spacing", 
                "Z轴间距（仅用于均匀分布）", NodeDataType.DOUBLE, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", 
                "生成的坐标列表", NodeDataType.BLOCK_LIST, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "在指定区域内随机或均匀生成坐标列表";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Populate Region";
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object minCornerObj = inputValues.get(INPUT_MIN_CORNER_ID);
        Object maxCornerObj = inputValues.get(INPUT_MAX_CORNER_ID);
        Object countObj = inputValues.get(INPUT_COUNT_ID);
        Object seedObj = inputValues.get(INPUT_SEED_ID);
        Object spacingXObj = inputValues.get(INPUT_SPACING_X_ID);
        Object spacingYObj = inputValues.get(INPUT_SPACING_Y_ID);
        Object spacingZObj = inputValues.get(INPUT_SPACING_Z_ID);
        
        // 默认空的坐标列表
        BlockPosList result = new BlockPosList();
        
        // 检查输入是否为方块坐标
        if (minCornerObj instanceof BlockPos && maxCornerObj instanceof BlockPos) {
            BlockPos minCorner = (BlockPos) minCornerObj;
            BlockPos maxCorner = (BlockPos) maxCornerObj;
            
            // 确保最小、最大角落正确
            BlockPos realMinCorner = new BlockPos(
                Math.min(minCorner.getX(), maxCorner.getX()),
                Math.min(minCorner.getY(), maxCorner.getY()),
                Math.min(minCorner.getZ(), maxCorner.getZ())
            );
            
            BlockPos realMaxCorner = new BlockPos(
                Math.max(minCorner.getX(), maxCorner.getX()),
                Math.max(minCorner.getY(), maxCorner.getY()),
                Math.max(minCorner.getZ(), maxCorner.getZ())
            );
            
            // 计算区域尺寸
            int sizeX = realMaxCorner.getX() - realMinCorner.getX() + 1;
            int sizeY = realMaxCorner.getY() - realMinCorner.getY() + 1;
            int sizeZ = realMaxCorner.getZ() - realMinCorner.getZ() + 1;
            
            // 如果区域无效（尺寸为零），直接返回空结果
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                outputValues.put(OUTPUT_COORDINATES_ID, result);
                return;
            }
            
            // 获取数量
            int count = (countObj instanceof Number) ? ((Number) countObj).intValue() : 10;
            count = GenerationLimits.clampPositiveCount(count); // 确保至少生成1个坐标
            
            // 根据分布类型进行处理
            if (distributionType == DistributionType.RANDOM) {
                // 随机分布
                long seed = (seedObj instanceof Number) ? ((Number) seedObj).longValue() : System.currentTimeMillis();
                generateRandomDistribution(realMinCorner, realMaxCorner, count, seed, result);
            } else {
                // 均匀分布
                double spacingX = (spacingXObj instanceof Number) ? ((Number) spacingXObj).doubleValue() : 1.0;
                double spacingY = (spacingYObj instanceof Number) ? ((Number) spacingYObj).doubleValue() : 1.0;
                double spacingZ = (spacingZObj instanceof Number) ? ((Number) spacingZObj).doubleValue() : 1.0;
                
                // 限制间距为正数
                spacingX = Math.max(0.1, spacingX);
                spacingY = Math.max(0.1, spacingY);
                spacingZ = Math.max(0.1, spacingZ);
                
                generateUniformDistribution(realMinCorner, realMaxCorner, count, spacingX, spacingY, spacingZ, result);
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_COORDINATES_ID, result);
    }
    
    /**
     * 生成随机分布的坐标列表
     * @param minCorner 区域最小角落
     * @param maxCorner 区域最大角落
     * @param count 坐标数量
     * @param seed 随机种子
     * @param result 结果列表
     */
    private void generateRandomDistribution(BlockPos minCorner, BlockPos maxCorner, 
                                           int count, long seed, BlockPosList result) {
        Random random = new Random(seed);
        
        int xRange = maxCorner.getX() - minCorner.getX() + 1;
        int yRange = maxCorner.getY() - minCorner.getY() + 1;
        int zRange = maxCorner.getZ() - minCorner.getZ() + 1;
        
        for (int i = 0; i < count; i++) {
            int x = minCorner.getX() + random.nextInt(xRange);
            int y = minCorner.getY() + random.nextInt(yRange);
            int z = minCorner.getZ() + random.nextInt(zRange);
            
            result.add(new BlockPos(x, y, z));
        }
    }
    
    /**
     * 生成均匀分布的坐标列表
     * @param minCorner 区域最小角落
     * @param maxCorner 区域最大角落
     * @param targetCount 目标坐标数量
     * @param spacingX X轴间距
     * @param spacingY Y轴间距
     * @param spacingZ Z轴间距
     * @param result 结果列表
     */
    private void generateUniformDistribution(BlockPos minCorner, BlockPos maxCorner, 
                                            int targetCount, double spacingX, double spacingY, double spacingZ,
                                            BlockPosList result) {
        // 区域尺寸
        double xSize = maxCorner.getX() - minCorner.getX() + 1;
        double ySize = maxCorner.getY() - minCorner.getY() + 1;
        double zSize = maxCorner.getZ() - minCorner.getZ() + 1;
        
        // 计算每个维度的点数
        int numPointsX = Math.max(1, (int)(xSize / spacingX));
        int numPointsY = Math.max(1, (int)(ySize / spacingY));
        int numPointsZ = Math.max(1, (int)(zSize / spacingZ));
        
        // 调整间距以均匀填充区域
        double adjustedSpacingX = xSize / numPointsX;
        double adjustedSpacingY = ySize / numPointsY;
        double adjustedSpacingZ = zSize / numPointsZ;
        
        // 计算总点数
        int totalPoints = numPointsX * numPointsY * numPointsZ;
        
        // 如果总点数超过目标数量，我们需要采样
        if (totalPoints > targetCount) {
            // 系数确定采样频率
            double sampleRate = (double) targetCount / totalPoints;
            Random random = new Random();
            
            for (int x = 0; x < numPointsX; x++) {
                for (int y = 0; y < numPointsY; y++) {
                    for (int z = 0; z < numPointsZ; z++) {
                        // 采样决定是否保留此点
                        if (random.nextDouble() <= sampleRate) {
                            // 计算实际坐标
                            int posX = minCorner.getX() + (int)(x * adjustedSpacingX + adjustedSpacingX / 2);
                            int posY = minCorner.getY() + (int)(y * adjustedSpacingY + adjustedSpacingY / 2);
                            int posZ = minCorner.getZ() + (int)(z * adjustedSpacingZ + adjustedSpacingZ / 2);
                            
                            result.add(new BlockPos(posX, posY, posZ));
                            
                            // 如果已经达到目标数量，提前退出
                            if (result.size() >= targetCount) {
                                return;
                            }
                        }
                    }
                }
            }
        } else {
            // 如果总点数小于目标数量，使用所有点
            for (int x = 0; x < numPointsX; x++) {
                for (int y = 0; y < numPointsY; y++) {
                    for (int z = 0; z < numPointsZ; z++) {
                        // 计算实际坐标
                        int posX = minCorner.getX() + (int)(x * adjustedSpacingX + adjustedSpacingX / 2);
                        int posY = minCorner.getY() + (int)(y * adjustedSpacingY + adjustedSpacingY / 2);
                        int posZ = minCorner.getZ() + (int)(z * adjustedSpacingZ + adjustedSpacingZ / 2);
                        
                        result.add(new BlockPos(posX, posY, posZ));
                    }
                }
            }
        }
    }
    
    // --- Getters/Setters for Properties ---
    
    public DistributionType getDistributionType() {
        return distributionType;
    }
    
    public void setDistributionType(DistributionType distributionType) {
        this.distributionType = distributionType;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("distributionType", distributionType.name());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("distributionType")) {
                Object distributionTypeObj = stateMap.get("distributionType");
                if (distributionTypeObj instanceof String) {
                    try {
                        setDistributionType(DistributionType.valueOf((String) distributionTypeObj));
                    } catch (IllegalArgumentException e) {
                        // 忽略无效的枚举值
                    }
                }
            }
        }
    }
} 
