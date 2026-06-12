package com.nodecraft.nodesystem.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to describe a node class for automatic registration.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NodeInfo {

    /**
     * Canonical node id.
     */
    String id() default "";

    /**
     * User-facing node title.
     */
    String displayName() default "";

    /**
     * Short node description.
     */
    String description() default "";

    /**
     * Canonical category id.
     */
    String category();

    /**
     * Optional display order within the category.
     * Lower values appear first.
     */
    int order() default Integer.MAX_VALUE;

    /**
     * Optional icon override.
     * <p>
     * Accepted values are either a node icon id such as
     * {@code geometry/primitives/sphere} or a full resource path such as
     * {@code textures/icons/nodes/geometry/primitives/sphere.svg}.
     * When omitted, the UI derives the icon path from {@link #id()} and
     * {@link #category()}.
     */
    String icon() default "";
}
