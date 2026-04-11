package com.nodecraft.nodesystem.nodes.input.context;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 获取游戏内当前时间的节点
 */
@NodeInfo(
    id = "input.context.current_time",
    displayName = "当前时间",
    description = "获取当前游戏世界的时间信息",
    category = "input.context"
)
public class CurrentTimeNode extends BaseNode {
    
    // --- 输出端口 ---
    private static final String OUTPUT_TIME_TICKS_ID = "output_time_ticks";
    private static final String OUTPUT_DAY_ID = "output_day";
    private static final String OUTPUT_DAY_TIME_ID = "output_day_time";
    private static final String OUTPUT_HOUR_ID = "output_hour";
    private static final String OUTPUT_MINUTE_ID = "output_minute";
    private static final String OUTPUT_IS_DAY_ID = "output_is_day";
    private static final String OUTPUT_IS_NIGHT_ID = "output_is_night";
    private static final String OUTPUT_IS_RAINING_ID = "output_is_raining";
    private static final String OUTPUT_IS_THUNDERING_ID = "output_is_thundering";
    
    // --- 节点属性 ---
    private String description = "Gets the current time in the Minecraft world."; // 节点描述
    
    /**
     * 构造一个新的游戏时间节点
     */
    public CurrentTimeNode() {
        // 使用新的分类命名 - inputs.minecraft.current_time
        super(UUID.randomUUID(), "input.context.current_time");
        
        // 创建并添加输出端口
        IPort timeTicksOutput = new BasePort(OUTPUT_TIME_TICKS_ID, "Time (Ticks)", 
                "The current world time in ticks", NodeDataType.INTEGER, this);
        addOutputPort(timeTicksOutput);
        
        IPort dayOutput = new BasePort(OUTPUT_DAY_ID, "Day", 
                "The current world day", NodeDataType.INTEGER, this);
        addOutputPort(dayOutput);
        
        IPort dayTimeOutput = new BasePort(OUTPUT_DAY_TIME_ID, "Day Time", 
                "The time of day in ticks (0-24000)", NodeDataType.INTEGER, this);
        addOutputPort(dayTimeOutput);
        
        IPort hourOutput = new BasePort(OUTPUT_HOUR_ID, "Hour", 
                "The current hour (0-23)", NodeDataType.INTEGER, this);
        addOutputPort(hourOutput);
        
        IPort minuteOutput = new BasePort(OUTPUT_MINUTE_ID, "Minute", 
                "The current minute (0-59)", NodeDataType.INTEGER, this);
        addOutputPort(minuteOutput);
        
        IPort isDayOutput = new BasePort(OUTPUT_IS_DAY_ID, "Is Day", 
                "Whether it is currently daytime", NodeDataType.BOOLEAN, this);
        addOutputPort(isDayOutput);
        
        IPort isNightOutput = new BasePort(OUTPUT_IS_NIGHT_ID, "Is Night", 
                "Whether it is currently nighttime", NodeDataType.BOOLEAN, this);
        addOutputPort(isNightOutput);
        
        IPort isRainingOutput = new BasePort(OUTPUT_IS_RAINING_ID, "Is Raining", 
                "Whether it is currently raining", NodeDataType.BOOLEAN, this);
        addOutputPort(isRainingOutput);
        
        IPort isThunderingOutput = new BasePort(OUTPUT_IS_THUNDERING_ID, "Is Thundering", 
                "Whether it is currently thundering", NodeDataType.BOOLEAN, this);
        addOutputPort(isThunderingOutput);
        
        // 设置默认输出值
        resetOutputs();
    }
    
    /**
     * 实现INode接口的getDescription方法
     * @return 节点描述
     */
    @Override
    public String getDescription() {
        return this.description;
    }
    
    /**
     * 节点的计算逻辑
     * @param context 执行上下文
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) {
            resetOutputs();
            return;
        }
        
        // 获取世界时间
        PlayerAccessor playerAccessor = context.getPlayerAccessor();
        if (playerAccessor == null) {
            resetOutputs();
            return;
        }
        
        updateOutputsFromAccessor(playerAccessor);
    }
    
    /**
     * 从PlayerAccessor更新输出值
     */
    private void updateOutputsFromAccessor(PlayerAccessor playerAccessor) {
        // 获取世界时间（单位：tick）
        long worldTimeTicks = playerAccessor.getWorldTime();
        int worldDay = playerAccessor.getWorldDay();
        
        // 计算一天中的时间（0-24000）
        int dayTime = (int)(worldTimeTicks % 24000);
        
        // 转换为小时和分钟
        // Minecraft中，时间0对应早上6点
        // 6:00 = 0 ticks, 12:00 = 6000 ticks, 18:00 = 12000 ticks, 0:00 = 18000 ticks
        int adjustedTime = (dayTime + 6000) % 24000;
        int hour = adjustedTime / 1000;
        int minute = (int)((adjustedTime % 1000) / (1000.0f / 60.0f));
        
        // 检查是否为白天（从日出到日落：0-12000）
        boolean isDay = playerAccessor.isDaytime();
        boolean isNight = !isDay;
        
        // 检查天气
        boolean isRaining = playerAccessor.isRaining();
        boolean isThundering = playerAccessor.isThundering();
        
        // 更新输出值
        outputValues.put(OUTPUT_TIME_TICKS_ID, worldTimeTicks);
        outputValues.put(OUTPUT_DAY_ID, worldDay);
        outputValues.put(OUTPUT_DAY_TIME_ID, dayTime);
        outputValues.put(OUTPUT_HOUR_ID, hour);
        outputValues.put(OUTPUT_MINUTE_ID, minute);
        outputValues.put(OUTPUT_IS_DAY_ID, isDay);
        outputValues.put(OUTPUT_IS_NIGHT_ID, isNight);
        outputValues.put(OUTPUT_IS_RAINING_ID, isRaining);
        outputValues.put(OUTPUT_IS_THUNDERING_ID, isThundering);
    }
    
    /**
     * 重置输出端口的值为默认值
     */
    private void resetOutputs() {
        outputValues.put(OUTPUT_TIME_TICKS_ID, 0);
        outputValues.put(OUTPUT_DAY_ID, 0);
        outputValues.put(OUTPUT_DAY_TIME_ID, 0);
        outputValues.put(OUTPUT_HOUR_ID, 6); // 默认为早上6点
        outputValues.put(OUTPUT_MINUTE_ID, 0);
        outputValues.put(OUTPUT_IS_DAY_ID, true);
        outputValues.put(OUTPUT_IS_NIGHT_ID, false);
        outputValues.put(OUTPUT_IS_RAINING_ID, false);
        outputValues.put(OUTPUT_IS_THUNDERING_ID, false);
    }
    
    // 此节点没有需要保存的状态
    @Override
    public Object getNodeState() {
        return null;
    }
    
    @Override
    public void setNodeState(Object state) {
        // 无状态需要恢复
    }
} 
