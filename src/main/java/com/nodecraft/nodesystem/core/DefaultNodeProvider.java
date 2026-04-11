package com.nodecraft.nodesystem.core;

import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.spi.INodeProvider;
import com.nodecraft.core.NodeCraft;

/**
 * 注册默认的NodeCraft内置节点
 */
public class DefaultNodeProvider implements INodeProvider {

    @Override
    public void registerNodes(NodeRegistry registry) {
        NodeCraft.LOGGER.debug("开始注册默认节点...");
        
        try {
            // 注册主分类
            registerMainCategories(registry);
            
            // 使用自动节点扫描器注册所有节点
            int nodeCount = AutoNodeScanner.scanAndRegisterNodes(registry);
            
            // 如果没有节点被注册，提供更详细的错误信息
            if (nodeCount == 0) {
                NodeCraft.LOGGER.warn("自动节点扫描器未能注册任何节点，请检查：");
                NodeCraft.LOGGER.warn("1. 节点类是否位于正确的包路径下 (com.nodecraft.nodesystem.nodes)");
                NodeCraft.LOGGER.warn("2. 节点类是否正确实现了INode接口");
                NodeCraft.LOGGER.warn("3. 节点类是否有无参构造函数");
                NodeCraft.LOGGER.warn("4. 节点类是否有正确的包结构 (如 inputs.basic, math.logic 等)");
                
                // 尝试注册一些示例节点以确保功能正常
                registerExampleNodes(registry);
            }
            
            NodeCraft.LOGGER.info("注册默认节点完成，总计{}个节点", registry.getNodeCount());
        } catch (Exception e) {
            NodeCraft.LOGGER.error("注册默认节点时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 注册所有主分类
     */
    private void registerMainCategories(NodeRegistry registry) {
        // 主分类（按字母顺序排列）
        registry.registerCategory(new NodeRegistry.NodeCategory("animation", "Animation & Effects"));
        registry.registerCategory(new NodeRegistry.NodeCategory("data", "Data & Lists"));
        registry.registerCategory(new NodeRegistry.NodeCategory("flora", "Flora & Nature"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry", "Geometry"));
        registry.registerCategory(new NodeRegistry.NodeCategory("input", "Input"));
        registry.registerCategory(new NodeRegistry.NodeCategory("inputs", "Inputs & Parameters"));
        registry.registerCategory(new NodeRegistry.NodeCategory("material", "Material & Mapping"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math", "Math & Logic"));
        registry.registerCategory(new NodeRegistry.NodeCategory("output", "Output & Execution"));
        registry.registerCategory(new NodeRegistry.NodeCategory("reference", "Reference"));
        registry.registerCategory(new NodeRegistry.NodeCategory("spatial", "Spatial & Geometry"));
        registry.registerCategory(new NodeRegistry.NodeCategory("transform", "Transform"));
        registry.registerCategory(new NodeRegistry.NodeCategory("utilities", "Utilities & Workflow"));
        registry.registerCategory(new NodeRegistry.NodeCategory("utilities.assist", "Assist & Reroute"));
        registry.registerCategory(new NodeRegistry.NodeCategory("world", "World Interaction"));
        
        NodeCraft.LOGGER.debug("注册了9个主分类");
    }
    
    /**
     * 注册示例节点（仅当自动扫描失败时使用）
     */
    private void registerExampleNodes(NodeRegistry registry) {
        NodeCraft.LOGGER.info("注册示例节点...");
        
        // 注册一些子分类
        registry.registerCategory(new NodeRegistry.NodeCategory("input.numeric", "Numeric"));
        registry.registerCategory(new NodeRegistry.NodeCategory("input.context", "Context"));
        registry.registerCategory(new NodeRegistry.NodeCategory("input.type_selectors", "Type Selectors"));
        registry.registerCategory(new NodeRegistry.NodeCategory("reference.points", "Points"));
        registry.registerCategory(new NodeRegistry.NodeCategory("reference.vectors", "Vectors"));
        registry.registerCategory(new NodeRegistry.NodeCategory("reference.planes", "Planes"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.boolean", "Boolean"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.curves", "Curves"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.primitives", "Primitives"));
        registry.registerCategory(new NodeRegistry.NodeCategory("geometry.profiles", "Profiles"));
        registry.registerCategory(new NodeRegistry.NodeCategory("transform.basic_transforms", "Basic Transforms"));
        registry.registerCategory(new NodeRegistry.NodeCategory("transform.orientation", "Orientation"));
        registry.registerCategory(new NodeRegistry.NodeCategory("world.selection", "Selection"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math.list_sequence", "List / Sequence"));
        registry.registerCategory(new NodeRegistry.NodeCategory("math.basic", "Basic Operations"));
        registry.registerCategory(new NodeRegistry.NodeCategory("data.text", "Text Processing"));
        
        // 暂时不注册示例节点，因为没有示例实现类
        NodeCraft.LOGGER.info("已注册示例分类，但暂不注册示例节点（没有实现类）。");
        NodeCraft.LOGGER.info("请确保在 com.nodecraft.nodesystem.nodes 包下正确实现节点类。");
    }
} 
