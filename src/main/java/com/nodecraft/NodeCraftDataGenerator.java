package com.nodecraft;

import com.nodecraft.core.NodeCraft;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * NodeCraft数据生成器
 * 用于自动生成模组所需的资源文件
 */
public class NodeCraftDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        NodeCraft.LOGGER.info("初始化NodeCraft数据生成器...");
        // 创建数据包
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        
        // 此处可以添加各种数据生成器
        // 暂时不添加任何生成器，等API稳定后再添加
    }
} 