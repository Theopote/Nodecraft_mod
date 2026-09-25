package com.nodecraft.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.nodecraft.core.NodeCraft;

/**
 * 编辑器事件总线
 * 负责管理事件监听器和分发事件
 */
public class EditorEventBus {

    private final CopyOnWriteArrayList<EditorEventListener> listeners = new CopyOnWriteArrayList<>();
    private boolean debugMode = false;
    
    /**
     * 注册一个事件监听器
     * @param listener 要注册的监听器
     */
    public void registerListener(EditorEventListener listener) {
        if (listener == null) {
            NodeCraft.LOGGER.warn("尝试注册空的事件监听器");
            return;
        }
        
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            // 根据优先级排序
            sortListeners();
            NodeCraft.LOGGER.debug("注册事件监听器: {} (优先级: {})", 
                    listener.getName(), listener.getPriority());
        } else {
            NodeCraft.LOGGER.debug("监听器已经注册: {}", listener.getName());
        }
    }
    
    /**
     * 注销一个事件监听器
     *
     * @param listener 要注销的监听器
     */
    public void unregisterListener(EditorEventListener listener) {
        if (listener == null) return;
        
        boolean removed = listeners.remove(listener);
        if (removed) {
            NodeCraft.LOGGER.debug("注销事件监听器: {}", listener.getName());
        }
    }
    
    /**
     * 分发一个事件到所有注册的监听器
     * @param event 要分发的事件
     * @return 如果至少有一个监听器处理了事件，则返回true
     */
    public boolean postEvent(EditorEvent event) {
        if (event == null) {
            NodeCraft.LOGGER.warn("尝试分发空事件");
            return false;
        }
        
        if (debugMode) {
            NodeCraft.LOGGER.debug("分发事件: {} 到 {} 个监听器", 
                    event.getEventType(), listeners.size());
        }
        
        boolean handled = false;
        int processedCount = 0;
        
        for (EditorEventListener listener : listeners) {
            if (listener.handlesEventType(event.getClass())) {
                try {
                    boolean listenerHandled = listener.onEvent(event);
                    if (listenerHandled) {
                        handled = true;
                        event.setHandled();
                        
                        if (debugMode) {
                            NodeCraft.LOGGER.debug("事件 {} 被监听器 {} 处理", 
                                    event.getEventType(), listener.getName());
                        }
                        
                        // 如果事件被处理且不再传播，则停止
                        break;
                    }
                    processedCount++;
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("监听器 {} 处理事件 {} 时发生错误: {}", 
                            listener.getName(), event.getEventType(), e.getMessage(), e);
                }
            }
        }
        
        if (debugMode && !handled) {
            NodeCraft.LOGGER.debug("事件 {} 未被任何监听器完全处理 (传递给 {} 个监听器)", 
                    event.getEventType(), processedCount);
        }
        
        return handled;
    }
    
    /**
     * 根据优先级对监听器进行排序
     */
    private void sortListeners() {
        List<EditorEventListener> tempList = new ArrayList<>(listeners);
        // 按优先级降序排列，优先级高的先处理
        tempList.sort((l1, l2) -> Integer.compare(l2.getPriority(), l1.getPriority()));
        
        listeners.clear();
        listeners.addAll(tempList);
    }
    
    /**
     * 获取当前注册的监听器数量
     * @return 监听器数量
     */
    public int getListenerCount() {
        return listeners.size();
    }
    
    /**
     * 清除所有监听器
     */
    public void clearAllListeners() {
        int count = listeners.size();
        listeners.clear();
        NodeCraft.LOGGER.debug("清除所有事件监听器 (共 {} 个)", count);
    }
    
    /**
     * 设置调试模式
     * @param debug 是否启用调试模式
     */
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
        NodeCraft.LOGGER.info("事件总线调试模式: {}", debug ? "开启" : "关闭");
    }
    
    /**
     * 检查调试模式是否启用
     * @return 是否启用调试模式
     */
    public boolean isDebugMode() {
        return debugMode;
    }
} 