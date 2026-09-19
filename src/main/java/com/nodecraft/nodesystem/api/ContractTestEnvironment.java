package com.nodecraft.nodesystem.api;

/**
 * Execution environment required to fully exercise a node in contract tests.
 * <p>
 * Nodes marked {@link ContractTestEnvironment#MINECRAFT_CLIENT} are excluded from
 * headless {@code ./gradlew test} catalog fences; they should be covered by a
 * dedicated Minecraft client / gametest suite instead.
 */
public enum ContractTestEnvironment {
    /** Default — must pass in headless unit tests. */
    UNIT,
    /** Requires live Minecraft registries / client bootstrap. */
    MINECRAFT_CLIENT
}
