package com.nodecraft.core.event;

import com.nodecraft.core.NodeCraft;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 一个简单的事件总线实现。
 */
public class EventBus {

    // 存储监听器及其处理方法
    private final Map<Class<? extends Event>, List<ListenerMethod>> listeners = new ConcurrentHashMap<>();
    private final Map<Object, List<ListenerMethod>> listenerCache = new ConcurrentHashMap<>();

    /**
     * 注册一个监听器对象。
     * 扫描对象中所有带有 @Subscribe 注解的方法，并将其注册到对应的事件类型。
     *
     * @param listener 要注册的监听器对象。
     */
    public void register(Object listener) {
        if (listenerCache.containsKey(listener)) {
            return; // 防止重复注册
        }

        List<ListenerMethod> methods = new ArrayList<>();
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Subscribe.class)) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1 && Event.class.isAssignableFrom(parameterTypes[0])) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Event> eventType = (Class<? extends Event>) parameterTypes[0];
                    method.setAccessible(true); // 允许访问私有方法
                    ListenerMethod listenerMethod = new ListenerMethod(listener, method);
                    listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listenerMethod);
                    methods.add(listenerMethod);
                } else {
                    NodeCraft.LOGGER.warn("方法 " + method.getName() + " 在类 " +
                                        listener.getClass().getSimpleName() +
                                        " 上有 @Subscribe 注解，但参数不符合要求（需要一个 Event 子类参数）。");
                }
            }
        }
        if (!methods.isEmpty()) {
             listenerCache.put(listener, methods);
        }
    }

    /**
     * 注销一个监听器对象。
     *
     * @param listener 要注销的监听器对象。
     */
    public void unregister(Object listener) {
        List<ListenerMethod> methods = listenerCache.remove(listener);
        if (methods != null) {
            for (ListenerMethod lm : methods) {
                listeners.values().forEach(list -> list.removeIf(m -> m.listener == listener && m.method.equals(lm.method)));
            }
            // 清理空的事件类型列表 (可选)
            listeners.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

    /**
     * 发布一个事件。
     * 将事件分发给所有注册了该事件类型或其父类型的监听器方法。
     *
     * @param event 要发布的事件对象。
     * @return 如果有任何监听器处理了该事件（调用了 event.setHandled(true)），则返回 true。
     */
    public boolean post(Event event) {
        Class<?> eventType = event.getClass();
        boolean handled = false;

        // 遍历所有监听此事件类型及其父类型的监听器
        while (eventType != null && Event.class.isAssignableFrom(eventType)) {
             List<ListenerMethod> eventListeners = listeners.get(eventType);
             if (eventListeners != null) {
                 for (ListenerMethod listenerMethod : eventListeners) {
                     try {
                         listenerMethod.method.invoke(listenerMethod.listener, event);
                         if (event.isHandled()) {
                            handled = true;
                            // 事件已被处理，是否需要停止分发？
                            // 如果需要，可以在这里 return true;
                         }
                     } catch (Exception e) {
                         NodeCraft.LOGGER.error("调用事件监听器时出错: " + listenerMethod.method.getName(), e);
                     }
                 }
             }
             // 检查父类型
             eventType = eventType.getSuperclass();
        }

        return handled;
    }

    /**
     * 内部类，用于存储监听器对象和对应的方法。
     */
    private static class ListenerMethod {
        final Object listener;
        final Method method;

        ListenerMethod(Object listener, Method method) {
            this.listener = listener;
            this.method = method;
        }
    }
} 