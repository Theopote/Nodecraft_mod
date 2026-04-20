package com.nodecraft.gui.components;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class PropertyInspector {
    private static final String GET_PREFIX = "get";
    private static final String IS_PREFIX = "is";
    private static final String SET_PREFIX = "set";
    private static final Set<Class<?>> SYSTEM_DECLARING_CLASSES = Set.of(
            INode.class,
            BaseNode.class
    );

    private final Map<Class<?>, List<PropertyDescriptor>> propertyCache = new ConcurrentHashMap<>();

    List<PropertyDescriptor> getPropertiesForNode(Class<?> nodeClass) {
        return propertyCache.computeIfAbsent(nodeClass, clazz -> {
            List<PropertyDescriptor> descriptors = new ArrayList<>();
            Map<String, Method> getters = new HashMap<>();
            Map<String, Method> setters = new HashMap<>();

            processAnnotatedFields(clazz, descriptors);
            processAnnotatedMethods(clazz, descriptors);

            Class<?> currentClass = clazz;
            while (currentClass != null && !currentClass.equals(Object.class)) {
                for (Method method : currentClass.getMethods()) {
                    if (method.getDeclaringClass().equals(Object.class)) {
                        continue;
                    }
                    if (SYSTEM_DECLARING_CLASSES.contains(method.getDeclaringClass())) {
                        continue;
                    }

                    String methodName = method.getName();
                    if (method.getParameterCount() == 0) {
                        if (methodName.startsWith(GET_PREFIX) && methodName.length() > GET_PREFIX.length()) {
                            getters.put(extractPropertyName(methodName, GET_PREFIX), method);
                        } else if (methodName.startsWith(IS_PREFIX)
                                && methodName.length() > IS_PREFIX.length()
                                && (method.getReturnType().equals(boolean.class)
                                || method.getReturnType().equals(Boolean.class))) {
                            getters.put(extractPropertyName(methodName, IS_PREFIX), method);
                        }
                    } else if (method.getParameterCount() == 1
                            && methodName.startsWith(SET_PREFIX)
                            && method.getReturnType().equals(void.class)) {
                        setters.put(extractPropertyName(methodName, SET_PREFIX), method);
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
                Method getterMethod = getterEntry.getValue();

                if (descriptors.stream().anyMatch(d -> d.name.equals(propertyName))) {
                    continue;
                }

                Method setterMethod = setters.get(propertyName);
                if (setterMethod != null
                        && (Modifier.isStatic(setterMethod.getModifiers())
                        || !Modifier.isPublic(setterMethod.getModifiers())
                        || !setterMethod.getParameterTypes()[0].isAssignableFrom(getterMethod.getReturnType()))) {
                    NodeCraft.LOGGER.trace("属性 {} 的 setter 方法类型或修饰符不匹配，将视为只读", propertyName);
                    setterMethod = null;
                }

                descriptors.add(new PropertyDescriptor(
                        propertyName,
                        formatPropertyName(propertyName),
                        getterMethod.getReturnType(),
                        new MethodWrapper(getterMethod),
                        setterMethod != null ? new MethodWrapper(setterMethod) : null,
                        null,
                        null,
                        "",
                        100
                ));
            }

            descriptors.sort(Comparator
                    .comparing((PropertyDescriptor descriptor) -> descriptor.category)
                    .thenComparingInt(descriptor -> descriptor.order)
                    .thenComparing(descriptor -> descriptor.displayName));
            return descriptors;
        });
    }

    void clearCache() {
        propertyCache.clear();
    }

    private void processAnnotatedFields(Class<?> clazz, List<PropertyDescriptor> descriptors) {
        Class<?> currentClass = clazz;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            for (Field field : currentClass.getDeclaredFields()) {
                NodeProperty annotation = field.getAnnotation(NodeProperty.class);
                if (annotation == null) {
                    continue;
                }

                String propertyName = field.getName();
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    NodeCraft.LOGGER.error("无效的 @NodeProperty 用法: 静态字段 '{}' 在类 '{}' 中，将忽略",
                            propertyName, currentClass.getName());
                    continue;
                }

                if (Modifier.isFinal(modifiers) && !annotation.readOnly()) {
                    NodeCraft.LOGGER.warn("@NodeProperty 在 final 字段 '{}' 上未标记为 readOnly，将强制视为只读", propertyName);
                }

                String displayName = annotation.displayName().isEmpty()
                        ? formatPropertyName(propertyName)
                        : annotation.displayName();

                MethodAccessor getter = createFieldGetter(field);
                MethodAccessor setter = (annotation.readOnly() || Modifier.isFinal(modifiers))
                        ? null
                        : createFieldSetter(field);

                descriptors.add(new PropertyDescriptor(
                        propertyName,
                        displayName,
                        field.getType(),
                        getter,
                        setter,
                        null,
                        annotation.description(),
                        annotation.category(),
                        annotation.order()
                ));
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    private void processAnnotatedMethods(Class<?> clazz, List<PropertyDescriptor> descriptors) {
        Class<?> currentClass = clazz;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            for (Method method : currentClass.getDeclaredMethods()) {
                NodeProperty annotation = method.getAnnotation(NodeProperty.class);
                if (annotation == null) {
                    continue;
                }

                String methodName = method.getName();
                int modifiers = method.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    NodeCraft.LOGGER.error("无效的 @NodeProperty 用法: 静态方法 '{}' 在类 '{}' 中，将忽略",
                            methodName, currentClass.getName());
                    continue;
                }
                if (method.getParameterCount() != 0) {
                    NodeCraft.LOGGER.error("无效的 @NodeProperty 用法: 方法 '{}' 有参数，只允许无参 getter", methodName);
                    continue;
                }
                if (method.getReturnType().equals(void.class)) {
                    NodeCraft.LOGGER.error("无效的 @NodeProperty 用法: 方法 '{}' 返回 void，只允许有返回值 getter", methodName);
                    continue;
                }

                String propertyName;
                if (methodName.startsWith(GET_PREFIX) && methodName.length() > GET_PREFIX.length()) {
                    propertyName = extractPropertyName(methodName, GET_PREFIX);
                } else if (methodName.startsWith(IS_PREFIX)
                        && methodName.length() > IS_PREFIX.length()
                        && (method.getReturnType().equals(boolean.class)
                        || method.getReturnType().equals(Boolean.class))) {
                    propertyName = extractPropertyName(methodName, IS_PREFIX);
                } else {
                    propertyName = methodName;
                    NodeCraft.LOGGER.warn("方法 '{}' 使用 @NodeProperty 但不遵循标准 getter 命名，使用方法名作为属性名", methodName);
                }

                String displayName = annotation.displayName().isEmpty()
                        ? formatPropertyName(propertyName)
                        : annotation.displayName();

                Method setterMethod = null;
                if (!annotation.readOnly()) {
                    String setterName = SET_PREFIX + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
                    try {
                        setterMethod = currentClass.getMethod(setterName, method.getReturnType());
                        if (Modifier.isStatic(setterMethod.getModifiers())
                                || !Modifier.isPublic(setterMethod.getModifiers())
                                || setterMethod.getParameterTypes().length != 1
                                || !setterMethod.getParameterTypes()[0].equals(method.getReturnType())) {
                            NodeCraft.LOGGER.warn("属性 '{}' 的 setter 方法 '{}' 类型或修饰符不匹配，将视为只读",
                                    propertyName, setterName);
                            setterMethod = null;
                        }
                    } catch (NoSuchMethodException e) {
                        NodeCraft.LOGGER.debug("属性 '{}' 未找到对应的 setter 方法 '{}'，将作为只读属性处理",
                                propertyName, setterName);
                    }
                }

                descriptors.add(new PropertyDescriptor(
                        propertyName,
                        displayName,
                        method.getReturnType(),
                        new MethodWrapper(method),
                        setterMethod != null ? new MethodWrapper(setterMethod) : null,
                        null,
                        annotation.description(),
                        annotation.category(),
                        annotation.order()
                ));
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    private static MethodAccessor createFieldGetter(Field field) {
        try {
            return new FieldHandleGetter(field);
        } catch (Throwable e) {
            NodeCraft.LOGGER.warn("创建 MethodHandle getter for field {} 失败，回退到反射: {}", field.getName(), e.getMessage());
            return new FieldGetter(field);
        }
    }

    private static MethodAccessor createFieldSetter(Field field) {
        try {
            return new FieldHandleSetter(field);
        } catch (Throwable e) {
            NodeCraft.LOGGER.warn("创建 MethodHandle setter for field {} 失败，回退到反射: {}", field.getName(), e.getMessage());
            return new FieldSetter(field);
        }
    }

    private String extractPropertyName(String methodName, String prefix) {
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

    private String formatPropertyName(String propertyName) {
        if (propertyName == null || propertyName.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        if (propertyName.length() >= 2
                && Character.isUpperCase(propertyName.charAt(0))
                && Character.isUpperCase(propertyName.charAt(1))) {
            int upperCaseEnd = 2;
            while (upperCaseEnd < propertyName.length()
                    && Character.isUpperCase(propertyName.charAt(upperCaseEnd))) {
                upperCaseEnd++;
            }

            if (upperCaseEnd == propertyName.length()) {
                return propertyName;
            }

            result.append(propertyName, 0, upperCaseEnd);
            char nextChar = propertyName.charAt(upperCaseEnd);
            if (Character.isLetterOrDigit(nextChar)) {
                result.append(' ');
            }

            propertyName = propertyName.substring(upperCaseEnd);
            if (Character.isLowerCase(propertyName.charAt(0))) {
                propertyName = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            }
        } else {
            result.append(Character.toUpperCase(propertyName.charAt(0)));
            propertyName = propertyName.substring(1);
        }

        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);
            if (Character.isUpperCase(c)
                    && (i > 0 && !Character.isUpperCase(propertyName.charAt(i - 1)))
                    && (i + 1 < propertyName.length() && Character.isLowerCase(propertyName.charAt(i + 1)))) {
                result.append(' ');
            }
            result.append(c);
        }
        return result.toString();
    }

    private static final class MethodHandleAccessorImpl implements MethodAccessor {
        private final MethodHandle handle;
        private final Class<?> returnType;
        private final Class<?>[] parameterTypes;

        private MethodHandleAccessorImpl(Method method) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                method.setAccessible(true);
                handle = lookup.unreflect(method);
                returnType = method.getReturnType();
                parameterTypes = method.getParameterTypes();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to create MethodHandle for " + method, e);
            }
        }

        @Override
        public Object invoke(Object obj, Object... args) throws Throwable {
            if (args == null || args.length == 0) {
                return handle.invoke(obj);
            }
            if (args.length == 1) {
                return handle.invoke(obj, args[0]);
            }

            Object[] invokeArgs = new Object[args.length + 1];
            invokeArgs[0] = obj;
            System.arraycopy(args, 0, invokeArgs, 1, args.length);
            return handle.invokeWithArguments(invokeArgs);
        }

        @Override
        public Class<?> getReturnType() {
            return returnType;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }
    }

    private static final class FieldHandleGetter implements MethodAccessor {
        private final MethodHandle getter;
        private final Class<?> fieldType;

        private FieldHandleGetter(Field field) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                field.setAccessible(true);
                getter = lookup.unreflectGetter(field);
                fieldType = field.getType();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to create MethodHandle getter for field " + field, e);
            }
        }

        @Override
        public Object invoke(Object obj, Object... args) throws Throwable {
            return getter.invoke(obj);
        }

        @Override
        public Class<?> getReturnType() {
            return fieldType;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return new Class<?>[0];
        }
    }

    private static final class FieldHandleSetter implements MethodAccessor {
        private final MethodHandle setter;
        private final Class<?> fieldType;

        private FieldHandleSetter(Field field) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                field.setAccessible(true);
                setter = lookup.unreflectSetter(field);
                fieldType = field.getType();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to create MethodHandle setter for field " + field, e);
            }
        }

        @Override
        public Object invoke(Object obj, Object... args) throws Throwable {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException("Setter requires a value argument");
            }
            setter.invoke(obj, args[0]);
            return null;
        }

        @Override
        public Class<?> getReturnType() {
            return void.class;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return new Class<?>[]{fieldType};
        }
    }

    private static final class MethodWrapper implements MethodAccessor {
        private final MethodHandleAccessorImpl methodHandleAccessor;

        private MethodWrapper(Method method) {
            methodHandleAccessor = new MethodHandleAccessorImpl(method);
        }

        @Override
        public Object invoke(Object obj, Object... args) throws Throwable {
            return methodHandleAccessor.invoke(obj, args);
        }

        @Override
        public Class<?> getReturnType() {
            return methodHandleAccessor.getReturnType();
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return methodHandleAccessor.getParameterTypes();
        }
    }

    private static final class FieldGetter implements MethodAccessor {
        private final Field field;

        private FieldGetter(Field field) {
            this.field = field;
            field.setAccessible(true);
        }

        @Override
        public Object invoke(Object obj, Object... args) throws IllegalAccessException {
            return field.get(obj);
        }

        @Override
        public Class<?> getReturnType() {
            return field.getType();
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return new Class<?>[0];
        }
    }

    private static final class FieldSetter implements MethodAccessor {
        private final Field field;

        private FieldSetter(Field field) {
            this.field = field;
            field.setAccessible(true);
        }

        @Override
        public Object invoke(Object obj, Object... args) throws IllegalAccessException {
            if (args != null && args.length > 0) {
                field.set(obj, args[0]);
            }
            return null;
        }

        @Override
        public Class<?> getReturnType() {
            return void.class;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return new Class<?>[]{field.getType()};
        }
    }
}
