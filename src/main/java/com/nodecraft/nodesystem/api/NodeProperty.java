package com.nodecraft.nodesystem.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于标记节点的属性，支持字段或方法
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface NodeProperty {
    /** 属性在UI中显示的名称，默认使用字段名自动格式化 */
    String displayName() default "";
    
    /** 属性是否为只读 */
    boolean readOnly() default false;
    
    /** 属性的分类，用于在属性面板中分组显示 */
    String category() default "常规";
    
    /** 属性的排序权重，值越小排序越靠前 */
    int order() default 100;
    
    /** 属性的描述，会显示在工具提示中 */
    String description() default "";

    /**
     * Minimum allowed value for numeric properties.
     * Unbounded when {@link Double#NEGATIVE_INFINITY}.
     */
    double min() default Double.NEGATIVE_INFINITY;

    /**
     * Maximum allowed value for numeric properties.
     * Unbounded when {@link Double#POSITIVE_INFINITY}.
     */
    double max() default Double.POSITIVE_INFINITY;

    /** Drag/step increment hint. Zero means auto. */
    double step() default 0.0;
} 