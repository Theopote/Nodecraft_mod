package com.nodecraft.nodesystem.core;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers node properties (matching {@code PropertyInspector} rules) and
 * serializes them for graph save/load.
 */
public final class NodePropertyBindings {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodePropertyBindings.class);

    private static final String GET_PREFIX = "get";
    private static final String IS_PREFIX = "is";
    private static final String SET_PREFIX = "set";
    private static final Set<Class<?>> SYSTEM_DECLARING_CLASSES = Set.of(
            INode.class,
            BaseNode.class
    );

    private static final Map<Class<?>, List<Binding>> BINDING_CACHE = new ConcurrentHashMap<>();

    private NodePropertyBindings() {
    }

    public static Map<String, Object> serialize(BaseNode node) {
        Map<String, Object> state = new HashMap<>();
        for (Binding binding : bindingsFor(node.getClass())) {
            try {
                Object value = binding.read(node);
                if (value != null) {
                    state.put(binding.name(), value);
                }
            } catch (Throwable e) {
                LOGGER.warn("Failed to serialize property '{}' on {}", binding.name(), node.getClass().getName(), e);
            }
        }
        return state;
    }

    public static void deserialize(BaseNode node, Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return;
        }

        Map<String, Binding> bindingByName = new HashMap<>();
        for (Binding binding : bindingsFor(node.getClass())) {
            bindingByName.put(binding.name(), binding);
        }

        for (Map.Entry<String, Object> entry : state.entrySet()) {
            Binding binding = bindingByName.get(entry.getKey());
            if (binding == null || !binding.writable()) {
                continue;
            }
            try {
                Object coerced = coerceValue(entry.getValue(), binding.type());
                if (coerced != null || entry.getValue() == null) {
                    binding.write(node, coerced);
                }
            } catch (Throwable e) {
                LOGGER.warn("Failed to restore property '{}' on {}", entry.getKey(), node.getClass().getName(), e);
            }
        }
    }

    static List<Binding> bindingsFor(Class<?> nodeClass) {
        return BINDING_CACHE.computeIfAbsent(nodeClass, NodePropertyBindings::discoverBindings);
    }

    static void clearCache() {
        BINDING_CACHE.clear();
    }

    private static List<Binding> discoverBindings(Class<?> nodeClass) {
        List<Binding> bindings = new ArrayList<>();
        Map<String, Method> getters = new HashMap<>();
        Map<String, Method> setters = new HashMap<>();

        collectAnnotatedFields(nodeClass, bindings);
        collectAnnotatedMethods(nodeClass, bindings);

        Class<?> currentClass = nodeClass;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (method.getDeclaringClass().equals(Object.class)) {
                    continue;
                }
                if (SYSTEM_DECLARING_CLASSES.contains(method.getDeclaringClass())) {
                    continue;
                }
                int modifiers = method.getModifiers();
                if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) {
                    continue;
                }

                String methodName = method.getName();
                if (method.getParameterCount() == 0) {
                    if (methodName.startsWith(GET_PREFIX) && methodName.length() > GET_PREFIX.length()) {
                        getters.putIfAbsent(extractPropertyName(methodName, GET_PREFIX), method);
                    } else if (methodName.startsWith(IS_PREFIX)
                            && methodName.length() > IS_PREFIX.length()
                            && (method.getReturnType().equals(boolean.class)
                            || method.getReturnType().equals(Boolean.class))) {
                        getters.putIfAbsent(extractPropertyName(methodName, IS_PREFIX), method);
                    }
                } else if (method.getParameterCount() == 1
                        && methodName.startsWith(SET_PREFIX)
                        && method.getReturnType().equals(void.class)) {
                    setters.putIfAbsent(extractPropertyName(methodName, SET_PREFIX), method);
                }
            }

            if (currentClass.isRecord()) {
                for (RecordComponent component : currentClass.getRecordComponents()) {
                    Method accessor = component.getAccessor();
                    if (!SYSTEM_DECLARING_CLASSES.contains(accessor.getDeclaringClass())) {
                        getters.putIfAbsent(component.getName(), accessor);
                    }
                }
            }

            currentClass = currentClass.getSuperclass();
        }

        for (Map.Entry<String, Method> getterEntry : getters.entrySet()) {
            String propertyName = getterEntry.getKey();
            if (bindings.stream().anyMatch(binding -> binding.name().equals(propertyName))) {
                continue;
            }

            Method getterMethod = getterEntry.getValue();
            Method setterMethod = setters.get(propertyName);
            if (setterMethod != null
                    && (Modifier.isStatic(setterMethod.getModifiers())
                    || !Modifier.isPublic(setterMethod.getModifiers())
                    || !setterMethod.getParameterTypes()[0].isAssignableFrom(getterMethod.getReturnType()))) {
                setterMethod = null;
            }

            bindings.add(new MethodBinding(propertyName, getterMethod, setterMethod));
        }

        return bindings;
    }

    private static void collectAnnotatedFields(Class<?> nodeClass, List<Binding> bindings) {
        Class<?> currentClass = nodeClass;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            for (Field field : currentClass.getDeclaredFields()) {
                NodeProperty annotation = field.getAnnotation(NodeProperty.class);
                if (annotation == null) {
                    continue;
                }

                String propertyName = field.getName();
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    LOGGER.error("Invalid @NodeProperty: static field '{}' on '{}', ignoring",
                            propertyName, currentClass.getName());
                    continue;
                }

                boolean readOnly = annotation.readOnly() || Modifier.isFinal(modifiers);
                bindings.add(new FieldBinding(propertyName, field, readOnly));
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    private static void collectAnnotatedMethods(Class<?> nodeClass, List<Binding> bindings) {
        Class<?> currentClass = nodeClass;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            for (Method method : currentClass.getDeclaredMethods()) {
                NodeProperty annotation = method.getAnnotation(NodeProperty.class);
                if (annotation == null) {
                    continue;
                }

                String methodName = method.getName();
                int modifiers = method.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    LOGGER.error("Invalid @NodeProperty: static method '{}' on '{}', ignoring",
                            methodName, currentClass.getName());
                    continue;
                }
                if (method.getParameterCount() != 0 || method.getReturnType().equals(void.class)) {
                    continue;
                }

                String propertyName;
                boolean standardGetterNaming = true;
                if (methodName.startsWith(GET_PREFIX) && methodName.length() > GET_PREFIX.length()) {
                    propertyName = extractPropertyName(methodName, GET_PREFIX);
                } else if (methodName.startsWith(IS_PREFIX)
                        && methodName.length() > IS_PREFIX.length()
                        && (method.getReturnType().equals(boolean.class)
                        || method.getReturnType().equals(Boolean.class))) {
                    propertyName = extractPropertyName(methodName, IS_PREFIX);
                } else {
                    standardGetterNaming = false;
                    propertyName = methodName;
                }

                Method setterMethod = null;
                if (!annotation.readOnly() && standardGetterNaming) {
                    String setterName = SET_PREFIX
                            + Character.toUpperCase(propertyName.charAt(0))
                            + propertyName.substring(1);
                    try {
                        setterMethod = currentClass.getMethod(setterName, method.getReturnType());
                        if (Modifier.isStatic(setterMethod.getModifiers())
                                || !Modifier.isPublic(setterMethod.getModifiers())
                                || setterMethod.getParameterTypes().length != 1
                                || !setterMethod.getParameterTypes()[0].equals(method.getReturnType())) {
                            setterMethod = null;
                        }
                    } catch (NoSuchMethodException ignored) {
                        setterMethod = null;
                    }
                }

                bindings.add(new MethodBinding(propertyName, method, setterMethod));
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    static Object coerceValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType.isEnum() && value instanceof String text) {
            return enumValueOfUnchecked(targetType, text);
        }
        if (value instanceof Number number) {
            if (targetType == int.class || targetType == Integer.class) {
                return number.intValue();
            }
            if (targetType == long.class || targetType == Long.class) {
                return number.longValue();
            }
            if (targetType == float.class || targetType == Float.class) {
                return number.floatValue();
            }
            if (targetType == double.class || targetType == Double.class) {
                return number.doubleValue();
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return number.byteValue();
            }
            if (targetType == short.class || targetType == Short.class) {
                return number.shortValue();
            }
        }
        if ((targetType == boolean.class || targetType == Boolean.class) && value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == UUID.class && value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValueOfUnchecked(Class<?> targetType, String text) {
        return Enum.valueOf((Class<? extends Enum>) targetType, text);
    }

    private static String extractPropertyName(String methodName, String prefix) {
        String name = methodName.substring(prefix.length());
        if (name.isEmpty()) {
            return "";
        }
        if (name.length() == 1) {
            return name.toLowerCase();
        }

        char firstChar = name.charAt(0);
        char secondChar = name.charAt(1);
        if (Character.isUpperCase(firstChar) && Character.isUpperCase(secondChar)) {
            return name;
        }
        if (Character.isUpperCase(firstChar) && Character.isLowerCase(secondChar)) {
            return Character.toLowerCase(firstChar) + name.substring(1);
        }
        return name;
    }

    interface Binding {
        String name();

        Class<?> type();

        boolean writable();

        Object read(Object target) throws Throwable;

        void write(Object target, Object value) throws Throwable;
    }

    private static final class FieldBinding implements Binding {
        private final String name;
        private final MethodHandle getter;
        private final MethodHandle setter;
        private final Class<?> type;
        private final boolean writable;

        private FieldBinding(String name, Field field, boolean readOnly) {
            this.name = name;
            this.type = field.getType();
            this.writable = !readOnly;
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                field.setAccessible(true);
                getter = lookup.unreflectGetter(field);
                setter = writable ? lookup.unreflectSetter(field) : null;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to bind field " + field, e);
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Class<?> type() {
            return type;
        }

        @Override
        public boolean writable() {
            return writable;
        }

        @Override
        public Object read(Object target) throws Throwable {
            return getter.invoke(target);
        }

        @Override
        public void write(Object target, Object value) throws Throwable {
            if (setter != null) {
                setter.invoke(target, value);
            }
        }
    }

    private static final class MethodBinding implements Binding {
        private final String name;
        private final MethodHandle getter;
        private final MethodHandle setter;
        private final Class<?> type;
        private final boolean writable;

        private MethodBinding(String name, Method getterMethod, Method setterMethod) {
            this.name = name;
            this.type = getterMethod.getReturnType();
            this.writable = setterMethod != null;
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                getterMethod.setAccessible(true);
                getter = lookup.unreflect(getterMethod);
                if (setterMethod != null) {
                    setterMethod.setAccessible(true);
                    setter = lookup.unreflect(setterMethod);
                } else {
                    setter = null;
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to bind methods for property " + name, e);
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Class<?> type() {
            return type;
        }

        @Override
        public boolean writable() {
            return writable;
        }

        @Override
        public Object read(Object target) throws Throwable {
            return getter.invoke(target);
        }

        @Override
        public void write(Object target, Object value) throws Throwable {
            if (setter != null) {
                setter.invoke(target, value);
            }
        }
    }
}
