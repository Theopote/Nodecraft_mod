package com.nodecraft.gui.components;

final class PropertyDescriptor {
    final String name;
    final String displayName;
    final Class<?> type;
    final MethodAccessor getter;
    final MethodAccessor setter;
    final PropertyRenderer renderer;
    final String description;
    final String category;
    final int order;

    PropertyDescriptor(
            String name,
            String displayName,
            Class<?> type,
            MethodAccessor getter,
            MethodAccessor setter,
            PropertyRenderer renderer,
            String description,
            String category,
            int order
    ) {
        this.name = name;
        this.displayName = displayName;
        this.type = type;
        this.getter = getter;
        this.setter = setter;
        this.renderer = renderer;
        this.description = description;
        this.category = category;
        this.order = order;
    }
}
