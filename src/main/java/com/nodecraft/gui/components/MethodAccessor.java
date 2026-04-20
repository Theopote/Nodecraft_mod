package com.nodecraft.gui.components;

interface MethodAccessor {
    Object invoke(Object obj, Object... args) throws Throwable;

    Class<?> getReturnType();

    Class<?>[] getParameterTypes();
}
