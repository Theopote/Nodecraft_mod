package com.nodecraft.nodesystem.core;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.catalog.NodeCatalog;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.spi.INodeProvider;

/**
 * Registers built-in NodeCraft categories and nodes.
 */
public class DefaultNodeProvider implements INodeProvider {

    @Override
    public void registerNodes(NodeRegistry registry) {
        NodeCraft.LOGGER.debug("Starting built-in node registration...");

        try {
            // Register top-level categories first.
            registerMainCategories(registry);

            // Primary path: build-time catalog (see docs/architecture/node-catalog.md).
            int nodeCount = NodeCatalog.registerAll(registry);
            if (nodeCount > 0) {
                NodeCraft.LOGGER.info(
                        "Registered {} built-in nodes from build-time catalog (entries={}).",
                        nodeCount,
                        NodeCatalog.entryCount());
            } else {
                NodeCraft.LOGGER.warn("Build-time NodeCatalog registered no nodes; falling back to AutoNodeScanner.");
                nodeCount = AutoNodeScanner.scanAndRegisterNodes(registry);
            }

            // If both paths find nothing, log diagnostics and register fallback categories.
            if (nodeCount == 0) {
                NodeCraft.LOGGER.warn("Node registration found no nodes. Check the following:");
                NodeCraft.LOGGER.warn("1. generateNodeCatalog ran before compile (Gradle task)");
                NodeCraft.LOGGER.warn("2. Node classes are under com.nodecraft.nodesystem.nodes");
                NodeCraft.LOGGER.warn("3. Node classes correctly implement INode with @NodeInfo");
                NodeCraft.LOGGER.warn("4. Node classes expose a no-argument constructor");

                // Keep the editor bootable even when scanning fails.
                registerExampleNodes(registry);
            }

            NodeCraft.LOGGER.info("Built-in node registration completed. Total nodes: {}", registry.getNodeCount());
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Built-in node registration failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Registers all top-level categories exposed by the built-in provider.
     */
    private void registerMainCategories(NodeRegistry registry) {
        // Keep the mainline v1 entry focused on canonical domains.
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry", "Geometry"));
        registry.registerCategory(new NodeRegistry.NodeCategory("input", "Input"));
        registry.registerCategory(new NodeRegistry.NodeCategory("input.values", "Values"));
        registry.registerCategory(new NodeRegistry.NodeCategory("material", "Material"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math", "Math & Logic"));
        registry.registerCategory(new NodeRegistry.NodeCategory("output", "Output"));
        registry.registerCategory(new NodeRegistry.NodeCategory("pattern", "Pattern"));
        registry.registerCategory(new NodeRegistry.NodeCategory("reference", "Reference"));
        registry.registerCategory(new NodeRegistry.NodeCategory("transform", "Transform"));
        registry.registerCategory(new NodeRegistry.NodeCategory("utilities", "Utilities"));
        registry.registerCategory(new NodeRegistry.NodeCategory("utilities.assist", "Assist"));
        registry.registerCategory(new NodeRegistry.NodeCategory("utilities.fileio", "File I/O"));
        registry.registerCategory(new NodeRegistry.NodeCategory("utilities.organization", "Organization"));
        registry.registerCategory(new NodeRegistry.NodeCategory("utilities.morphology", "Morphology"));
        registry.registerCategory(new NodeRegistry.NodeCategory("world", "World"));

        NodeCraft.LOGGER.debug("Registered top-level categories for the built-in provider.");
    }

    /**
     * Registers fallback subcategories used when automatic scanning fails.
     */
    private void registerExampleNodes(NodeRegistry registry) {
        NodeCraft.LOGGER.info("Registering fallback example categories...");

        // Register representative subcategories so the editor still has a usable taxonomy.
        registry.registerCategory(new NodeRegistry.NodeCategory("input.numeric", "Numeric"));
        registry.registerCategory(new NodeRegistry.NodeCategory("input.values", "Values"));
        registry.registerCategory(new NodeRegistry.NodeCategory("input.context", "Context"));
        registry.registerCategory(new NodeRegistry.NodeCategory("input.type_selectors", "Type Selectors"));
        registry.registerCategory(new NodeRegistry.NodeCategory("reference.points", "Points"));
        registry.registerCategory(new NodeRegistry.NodeCategory("reference.vectors", "Vectors"));
        registry.registerCategory(new NodeRegistry.NodeCategory("reference.planes", "Planes"));
        registry.registerCategory(new NodeRegistry.NodeCategory("reference.frames", "Frames"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.boolean", "Boolean"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.combine", "Combine"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.sdf", "SDF"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.analysis", "Analysis"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.architectural_primitives", "Architectural Primitives"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.curves", "Curves"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.primitives", "Primitives"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.profiles", "Profiles"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.solids", "Solids"));
        registry.registerCategory(new NodeRegistry.NodeCategory("material.basic_assignment", "Basic Assignment"));
        registry.registerCategory(new NodeRegistry.NodeCategory("material.gradient_mapping", "Gradient Mapping"));
        registry.registerCategory(new NodeRegistry.NodeCategory("material.directional_mapping", "Directional Mapping"));
        registry.registerCategory(new NodeRegistry.NodeCategory("material.pattern_mapping", "Pattern Mapping"));
        registry.registerCategory(new NodeRegistry.NodeCategory("material.block_state", "Block State"));
        registry.registerCategory(new NodeRegistry.NodeCategory("material.surface_aging", "Surface Aging"));
        registry.registerCategory(new NodeRegistry.NodeCategory("pattern.linear", "Linear"));
        registry.registerCategory(new NodeRegistry.NodeCategory("pattern.grid", "Grid"));
        registry.registerCategory(new NodeRegistry.NodeCategory("pattern.radial", "Radial"));
        registry.registerCategory(new NodeRegistry.NodeCategory("pattern.surface_volume_distribution", "Surface / Volume Distribution"));
        registry.registerCategory(new NodeRegistry.NodeCategory("transform.basic_transforms", "Basic Transforms"));
        registry.registerCategory(new NodeRegistry.NodeCategory("transform.deformations", "Deformations"));
        registry.registerCategory(new NodeRegistry.NodeCategory("transform.orientation", "Orientation"));
        registry.registerCategory(new NodeRegistry.NodeCategory("utilities.fileio", "File I/O"));
        registry.registerCategory(new NodeRegistry.NodeCategory("utilities.organization", "Organization"));
        registry.registerCategory(new NodeRegistry.NodeCategory("utilities.morphology", "Morphology"));
        registry.registerCategory(new NodeRegistry.NodeCategory("world.selection", "Selection"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math.list", "List"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math.sequence", "Sequence"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math.logic", "Logic"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math.compare", "Compare"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math.random", "Random"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math.scalar_math", "Scalar Math"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math.trigonometry", "Trigonometry"));

        // Example node implementations are intentionally not registered here.
        NodeCraft.LOGGER.info("Fallback categories registered. Example node implementations are not provided by this provider.");
        NodeCraft.LOGGER.info("Ensure node classes are implemented under the com.nodecraft.nodesystem.nodes package.");
    }
}
