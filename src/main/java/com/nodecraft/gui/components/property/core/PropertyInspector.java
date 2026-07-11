package com.nodecraft.gui.components.property.core;

import com.nodecraft.nodesystem.core.NodePropertyBindings;
import com.nodecraft.nodesystem.core.NodePropertyBindings.NodePropertyBinding;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PropertyInspector {
    private final Map<Class<?>, List<PropertyDescriptor>> propertyCache = new ConcurrentHashMap<>();

    public List<PropertyDescriptor> getPropertiesForNode(Class<?> nodeClass) {
        return propertyCache.computeIfAbsent(nodeClass, clazz ->
                NodePropertyBindings.bindingsFor(clazz).stream()
                        .sorted(Comparator
                                .comparing(NodePropertyBinding::category)
                                .thenComparingInt(NodePropertyBinding::order)
                                .thenComparing(NodePropertyBinding::displayName))
                        .map(PropertyInspector::toDescriptor)
                        .toList()
        );
    }

    public void clearCache() {
        propertyCache.clear();
        NodePropertyBindings.clearCache();
    }

    private static PropertyDescriptor toDescriptor(NodePropertyBinding binding) {
        return new PropertyDescriptor(
                binding.name(),
                binding.displayName(),
                binding.type(),
                new BindingAccessor(binding, true),
                binding.writable() ? new BindingAccessor(binding, false) : null,
                null,
                binding.description(),
                binding.category(),
                binding.order()
        );
    }

    private static final class BindingAccessor implements MethodAccessor {
        private final NodePropertyBinding binding;
        private final boolean read;

        private BindingAccessor(NodePropertyBinding binding, boolean read) {
            this.binding = binding;
            this.read = read;
        }

        @Override
        public Object invoke(Object obj, Object... args) throws Throwable {
            if (read) {
                return binding.read(obj);
            }
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException("Setter requires a value argument");
            }
            binding.write(obj, args[0]);
            return null;
        }

        @Override
        public Class<?> getReturnType() {
            return read ? binding.type() : void.class;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return read ? new Class<?>[0] : new Class<?>[]{binding.type()};
        }
    }
}
