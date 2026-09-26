package com.nodecraft.nodesystem.execution;

import com.nodecraft.core.exception.NodeExecutionException;
import com.nodecraft.nodesystem.minecraft.DefaultPlayerAccessor;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import com.nodecraft.nodesystem.execution.runtime.CancellationToken;
import com.nodecraft.nodesystem.execution.subgraph.SubgraphCallFrame;
import com.nodecraft.nodesystem.io.SavedGraph;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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

    private final Deque<SubgraphCallFrame> subgraphCallFrames = new ArrayDeque<>();
    private Map<String, SavedGraph> subgraphDefinitions = Map.of();
    private boolean skipOutputExecuteSideEffects;
    @Nullable
    private ExecutionRunGuard sharedExecutionRunGuard;
    private IncrementalExecutionOptions parentIncrementalOptions = IncrementalExecutionOptions.defaults();
    private ExecutionRunLimits parentRunLimits = ExecutionRunLimits.defaults();
    private CancellationToken parentCancellation = CancellationToken.none();
    
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

    /**
     * Runs world-sensitive work on the owning Minecraft server thread when possible.
     *
     * <p>Node graph math can run on worker threads, but Minecraft world and chunk
     * reads are not generally thread-safe. Query/read/write nodes should enter
     * through this helper so server worlds are accessed from the tick thread.</p>
     */
    public <T> T callOnWorldThread(Supplier<T> supplier) {
        if (supplier == null) {
            return null;
        }
        if (world instanceof ServerWorld serverWorld) {
            MinecraftServer server = serverWorld.getServer();
            if (server != null && server.isOnThread()) {
                return supplier.get();
            }
            try {
                CompletableFuture<T> future = null;
                if (server != null) {
                    future = server.submit(supplier);
                }
                if (future != null) {
                    return future.get();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NodeExecutionException("Failed to run node work on the Minecraft server thread", e);
            } catch (ExecutionException e) {
                throw new NodeExecutionException("Node work failed on the Minecraft server thread", e.getCause());
            }
        }
        return supplier.get();
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
        Object raw = variables.get(key);
        if (raw instanceof VariableEntry entry) {
            return entry.value();
        }
        return raw;
    }
    
    @Override
    public Map<String, Object> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * Raw map value including {@link VariableEntry} wrappers used by typed variable slots.
     */
    public @Nullable Object getVariableStorage(String key) {
        return variables.get(key);
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
        Object raw = variables.remove(key);
        if (raw instanceof VariableEntry entry) {
            return entry.value();
        }
        return raw;
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

    public void pushSubgraphCallFrame(SubgraphCallFrame frame) {
        if (frame != null) {
            subgraphCallFrames.push(frame);
        }
    }

    @Nullable
    public SubgraphCallFrame popSubgraphCallFrame() {
        return subgraphCallFrames.isEmpty() ? null : subgraphCallFrames.pop();
    }

    @Nullable
    public SubgraphCallFrame peekSubgraphCallFrame() {
        return subgraphCallFrames.peek();
    }

    public Map<String, SavedGraph> getSubgraphDefinitions() {
        return subgraphDefinitions;
    }

    public void setSubgraphDefinitions(@Nullable Map<String, SavedGraph> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            subgraphDefinitions = Map.of();
            return;
        }
        subgraphDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public boolean isSkipOutputExecuteSideEffects() {
        return skipOutputExecuteSideEffects;
    }

    public void setSkipOutputExecuteSideEffects(boolean skipOutputExecuteSideEffects) {
        this.skipOutputExecuteSideEffects = skipOutputExecuteSideEffects;
    }

    /**
     * Publishes the active parent executor policy so nested subgraph runs inherit cancellation,
     * run limits, and preview side-effect policy.
     */
    public void configureParentExecutionPolicy(
            IncrementalExecutionOptions incrementalOptions,
            ExecutionRunLimits runLimits,
            CancellationToken cancellation
    ) {
        parentIncrementalOptions = incrementalOptions == null
                ? IncrementalExecutionOptions.defaults()
                : incrementalOptions;
        parentRunLimits = runLimits == null ? ExecutionRunLimits.defaults() : runLimits;
        parentCancellation = cancellation != null ? cancellation : CancellationToken.none();
    }

    public IncrementalExecutionOptions getParentIncrementalOptions() {
        return parentIncrementalOptions;
    }

    public ExecutionRunLimits getParentRunLimits() {
        return parentRunLimits;
    }

    public CancellationToken getParentCancellationToken() {
        return parentCancellation;
    }

    @Nullable
    public ExecutionRunGuard getSharedExecutionRunGuard() {
        return sharedExecutionRunGuard;
    }

    public void setSharedExecutionRunGuard(@Nullable ExecutionRunGuard guard) {
        this.sharedExecutionRunGuard = guard;
    }

    public void clearSharedExecutionRunGuard() {
        this.sharedExecutionRunGuard = null;
    }
}
