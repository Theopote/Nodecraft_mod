package com.nodecraft.core;

import net.minecraft.item.ItemGroups;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nodecraft.core.item.ModItems; // 统一通过 ModItems 类管理物品注册
import com.nodecraft.core.item.NodeCraftToolItem;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.List;

public class NodeCraft implements ModInitializer {
	public static final String MOD_ID = "nodecraft";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// NodeCraft工具物品实例，应通过 ModItems 访问，而不是直接在这里注册
	// 移除此行，由 ModItems 统一管理：private static NodeCraftToolItem NODECRAFT_TOOL_INSTANCE;

	/**
	 * 获取NodeCraft工具物品实例。
	 * 此方法应在 ModItems 已经注册物品后调用。
	 * @return NodeCraftToolItem 实例。
	 */
	public static NodeCraftToolItem getNodecraftTool() {
		// 直接返回 ModItems 中已注册的实例
		// 确保 ModItems.NODECRAFT_TOOL 在此方法被调用前已初始化 (即 ModItems.registerItems() 已被调用)
		return ModItems.NODECRAFT_TOOL;
	}

	@Override
	public void onInitialize() {
		LOGGER.info("NodeCraft模组初始化中...");

		// 1. 注册物品 (在初始化早期进行，确保物品在节点系统或其他依赖前可用)
		registerItems();

		// 2. 初始化节点系统
		// (确保在物品注册后进行，如果节点有物品依赖，或者 RegistryScan 需要确保所有 ModItems 已加载)
		initializeNodeSystem();

		// 3. 初始化世界交互模块
		initializeWorldInteraction();

		// 4. 初始化数据管理模块
		initializeDataManagement();

		// 5. 注册游戏命令
		registerCommands();

		LOGGER.info("NodeCraft模组初始化完成！");
	}

	/**
	 * 注册物品的辅助方法（此方法不再需要，因为 ModItems 统一管理注册）
	 * private static NodeCraftToolItem register(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
	 * 	// ... (此方法应被移除，或其逻辑移动到 ModItems 内部)
	 * }
	 */

	private void initializeNodeSystem() {
		LOGGER.debug("初始化节点系统...");

		NodeRegistry registry = NodeRegistry.getInstance();

		// 确保 NodeRegistry 已初始化。
		// NodeRegistry.initialize() 应该负责扫描并注册所有节点。
		if (!registry.isInitialized()) {
			registry.initialize(); // 这一步应触发所有节点的注册，包括自动扫描
		}

		// 验证节点注册结果
		int totalNodes = registry.getNodeCount();
		int totalCategories = registry.getCategoryCount();
		LOGGER.info("节点注册完成。总计: {} 个节点, {} 个分类", totalNodes, totalCategories);

		if (totalNodes == 0) {
			LOGGER.error("警告：没有节点被成功注册！请检查 NodeRegistry 的初始化和节点扫描配置。");
		} else {
			// 调试信息：打印部分已注册的节点ID，帮助验证是否注册了期望的节点
			LOGGER.debug("已注册节点示例:");
			List<String> allNodeIds = registry.getAllNodeIds();
			for (int i = 0; i < Math.min(allNodeIds.size(), 10); i++) { // 只打印前10个，避免日志过长
				String nodeId = allNodeIds.get(i);
				// 再次检查 getNodeInfo 以确保它真的可获取到
				if (registry.getNodeInfo(nodeId) != null) {
					LOGGER.debug("  - {}", nodeId);
				}
			}
			if (allNodeIds.size() > 10) {
				LOGGER.debug("  ... (还有 {} 个节点)", allNodeIds.size() - 10);
			}
		}
	}

	private void initializeWorldInteraction() {
		LOGGER.debug("初始化世界交互模块...");
		com.nodecraft.nodesystem.bake.BakePlacementService.getInstance().registerTickHandler();
	}

	private void initializeDataManagement() {
		LOGGER.debug("初始化数据管理模块...");
		// TODO: 在这里添加实际的数据管理模块初始化代码
	}

	private void registerItems() {
		LOGGER.debug("注册NodeCraft物品...");
		try {
			// 统一通过 ModItems 类注册所有物品。
			// 确保 ModItems.java 中有 registerItems() 方法，并且在该方法内通过 Items.register 注册了 NODECRAFT_TOOL。
			ModItems.registerItems();

			// 将物品添加到创造模式物品栏的"工具和实用工具"标签中
			// 确保 ModItems.NODECRAFT_TOOL 是一个 public static final 字段
			ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> content.add(ModItems.NODECRAFT_TOOL));

			LOGGER.debug("已注册NodeCraft物品");
		} catch (Exception e) {
			LOGGER.error("注册NodeCraft物品失败", e);
			// 在 ModInitializer 中，注册失败是严重问题，应抛出运行时异常
			throw new RuntimeException("Failed to register NodeCraft items during initialization", e);
		}
	}

	private void registerCommands() {
		LOGGER.debug("注册模组命令...");
		// 示例：注册一个简单的命令 (如果您的命令注册在 CommandRegistrationCallback 中完成，则此处可为空)
		// CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
		// 	// dispatcher.register(...)
		// });
	}
}