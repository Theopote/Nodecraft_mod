package com.nodecraft.nodesystem.spi;

import com.nodecraft.nodesystem.registry.NodeRegistry; // Corrected import

/**
 * Service Provider Interface (SPI) for node plugins.
 * Implementations of this interface are responsible for registering 
 * custom node types with the NodeRegistry.
 * <p>
 * Plugins should provide an implementation of this interface and declare it 
 * in their META-INF/services/com.nodecraft.nodesystem.spi.INodeProvider file.
 */
@FunctionalInterface // Good practice for single-method interfaces
public interface INodeProvider {

    /**
     * Called by the core system during initialization to allow the plugin 
     * to register its node types.
     * 
     * @param registry The central NodeRegistry instance.
     */
    void registerNodes(NodeRegistry registry);
} 