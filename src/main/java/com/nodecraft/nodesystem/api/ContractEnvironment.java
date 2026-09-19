package com.nodecraft.nodesystem.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which contract-test environment must cover this node type.
 * <p>
 * Prefer this annotation on the node class; {@code minecraft-client-only-nodes.txt}
 * is a transitional allowlist for nodes not yet annotated.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ContractEnvironment {
    ContractTestEnvironment value();
}
