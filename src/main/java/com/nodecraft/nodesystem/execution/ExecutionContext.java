package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.minecraft.DefaultPlayerAccessor;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 节点图执行上下文，持有执行期间所需的世界、玩家和变量信息。
 * 实现 api.ExecutionContext 接口以统一上下文访问方式。
 */
public class ExecutionContext implements com.nodecraft.nodesystem.api.ExecutionContext {
    
    private final World world;
    @Nullable
    private final ServerPlayerEntity player;
    
    // 存储上下文变量
    private final Map<String, Object> variables = new HashMap<>();
    
    // 玩家数据访问器
    private PlayerAccessor playerAccessor;
    private final PlayerAccessor defaultPlayerAccessor;
    
    // 执行状态
    private boolean success = true;
    private String errorMessage = null;
    private Object result = null;
    
    public ExecutionContext(World world, @Nullable ServerPlayerEntity player) {
        this.world = world;
        this.player = player;
        this.defaultPlayerAccessor = new DefaultPlayerAccessor(world, player);
    }
    
    public World getWorld() {
        return world;
    }
    
    @Nullable
    public ServerPlayerEntity getPlayer() {
        return player;
    }
    
    // 静态方法创建默认/空的上下文
    public static ExecutionContext createEmpty(World world) {
        return new ExecutionContext(world, null);
    }
    
    /**
     * 获取玩家数据访问器
     * @return 玩家数据访问器，如果不可用则返回null
     */
    public PlayerAccessor getPlayerAccessor() {
        return playerAccessor != null ? playerAccessor : defaultPlayerAccessor;
    }
    
    /**
     * 设置玩家数据访问器
     * @param playerAccessor 玩家数据访问器
     */
    public void setPlayerAccessor(@Nullable PlayerAccessor playerAccessor) {
        this.playerAccessor = playerAccessor;
    }
    
    // === api.ExecutionContext 接口实现 ===
    
    @Override
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }
    
    @Override
    public Object getVariable(String key) {
        return variables.get(key);
    }
    
    @Override
    public Map<String, Object> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }
    
    @Override
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    @Override
    public boolean isSuccess() {
        return success;
    }
    
    @Override
    public void setErrorMessage(String message) {
        this.errorMessage = message;
    }
    
    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public void setResult(Object result) {
        this.result = result;
    }
    
    @Override
    public Object getResult() {
        return result;
    }
    
    // === 额外便捷方法 ===
    
    /**
     * 检查变量是否存在
     * @param key 变量名
     * @return 变量是否存在
     */
    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }
    
    /**
     * 删除变量
     * @param key 变量名
     * @return 被删除的变量值，如果不存在则返回null
     */
    public Object removeVariable(String key) {
        return variables.remove(key);
    }
    
    /**
     * 清除所有变量
     */
    public void clearVariables() {
        variables.clear();
    }
    
    /**
     * 获取所有变量的只读视图
     * @return 变量映射的不可修改视图
     */
    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }
}
