package com.nodecraft.core.item;

import com.nodecraft.core.NodeCraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * NodeCraft tool item. Opens the node editor on right-click (client only).
 * <p>
 * Intentionally free of client-only class references so it can load on dedicated
 * servers and Fabric GameTest (EnvType.SERVER).
 */
public class NodeCraftToolItem extends Item {

    public NodeCraftToolItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);

        if (world.isClient()) {
            NodeCraft.LOGGER.info("NodeCraft工具右键点击 - 准备打开编辑器界面");

            world.playSound(
                user,
                user.getBlockPos(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                SoundCategory.BLOCKS,
                1.0F,
                1.0F
            );

            openEditorReflectively();
        }

        return ActionResult.SUCCESS;
    }

    /**
     * Loads client UI via reflection so this common class never mentions
     * {@code net.minecraft.client.*} types at the bytecode level.
     */
    private static void openEditorReflectively() {
        try {
            Class<?> opener = Class.forName("com.nodecraft.core.client.NodeCraftToolClient");
            opener.getMethod("openEditor").invoke(null);
            NodeCraft.LOGGER.info("NodeCraft工具右键点击 - 编辑器界面已打开");
        } catch (ClassNotFoundException e) {
            NodeCraft.LOGGER.error("无法找到 NodeCraftToolClient 类: {}", e.getMessage());
        } catch (NoClassDefFoundError e) {
            NodeCraft.LOGGER.error("NodeCraft 编辑器依赖类缺失: {}", e.getMessage());
        } catch (Exception e) {
            NodeCraft.LOGGER.error("无法打开 NodeCraft 编辑器界面: {}", e.getMessage(), e);
        }
    }
}
