package com.nodecraft.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.visual.SelectionVisualFeedback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * 调试命令，用于测试方块高亮功能
 */
public class HighlightTestCommand {
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("nodecraft")
            .then(ClientCommandManager.literal("test")
                .then(ClientCommandManager.literal("highlight")
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                .executes(HighlightTestCommand::testHighlight)
                            )
                        )
                    )
                    .executes(HighlightTestCommand::testHighlightAtPlayer)
                )
                .then(ClientCommandManager.literal("clear")
                    .executes(HighlightTestCommand::clearHighlights)
                )
                .then(ClientCommandManager.literal("info")
                    .executes(HighlightTestCommand::showInfo)
                )
            )
        );
    }
    
    private static int testHighlight(CommandContext<FabricClientCommandSource> context) {
        try {
            int x = IntegerArgumentType.getInteger(context, "x");
            int y = IntegerArgumentType.getInteger(context, "y");
            int z = IntegerArgumentType.getInteger(context, "z");
            
            Coordinate position = new Coordinate(x, y, z);
            
            // 测试 SelectionVisualFeedback
            SelectionVisualFeedback.getInstance().showBlockSelection(
                "test_command", 
                position, 
                SelectionVisualFeedback.SelectionState.SELECTED
            );
            
            context.getSource().sendFeedback(Text.literal(
                String.format("已在 (%d, %d, %d) 创建测试高亮", x, y, z)
            ));
            
            NodeCraft.LOGGER.info("测试命令: 在 {} 创建高亮", position);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("创建高亮失败: " + e.getMessage()));
            NodeCraft.LOGGER.error("测试高亮命令失败", e);
            return 0;
        }
    }
    
    private static int testHighlightAtPlayer(CommandContext<FabricClientCommandSource> context) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                context.getSource().sendError(Text.literal("玩家不存在"));
                return 0;
            }
            
            BlockPos playerPos = client.player.getBlockPos();
            Coordinate position = new Coordinate(playerPos.getX(), playerPos.getY() + 1, playerPos.getZ());
            
            // 测试 SelectionVisualFeedback
            SelectionVisualFeedback.getInstance().showBlockSelection(
                "test_command", 
                position, 
                SelectionVisualFeedback.SelectionState.SELECTED
            );
            
            context.getSource().sendFeedback(Text.literal(
                String.format("已在玩家上方 (%d, %d, %d) 创建测试高亮", 
                    position.x(), position.y(), position.z())
            ));
            
            NodeCraft.LOGGER.info("测试命令: 在玩家上方 {} 创建高亮", position);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("创建高亮失败: " + e.getMessage()));
            NodeCraft.LOGGER.error("测试高亮命令失败", e);
            return 0;
        }
    }
    
    private static int clearHighlights(CommandContext<FabricClientCommandSource> context) {
        try {
            // 清除测试高亮
            SelectionVisualFeedback.getInstance().clearFeedback("test_command");
            
            // 清除所有预览
            PreviewRenderer.getInstance().clearAllPreviews();
            
            context.getSource().sendFeedback(Text.literal("已清除所有测试高亮"));
            NodeCraft.LOGGER.info("测试命令: 清除所有高亮");
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("清除高亮失败: " + e.getMessage()));
            NodeCraft.LOGGER.error("清除高亮命令失败", e);
            return 0;
        }
    }
    
    private static int showInfo(CommandContext<FabricClientCommandSource> context) {
        try {
            PreviewRenderer renderer = PreviewRenderer.getInstance();
            
            boolean globalEnabled = renderer.isGlobalPreviewEnabled();
            float globalOpacity = renderer.getGlobalOpacity();
            int activeCount = renderer.getActiveElementCount();
            
            context.getSource().sendFeedback(Text.literal(
                String.format("""
                                预览渲染器状态:
                                - 全局启用: %s
                                - 全局透明度: %.2f
                                - 活跃元素数量: %d""",
                    globalEnabled ? "是" : "否",
                    globalOpacity,
                    activeCount
                )
            ));
            
            NodeCraft.LOGGER.info("预览渲染器状态: 全局启用={}, 透明度={}, 活跃元素={}", 
                globalEnabled, globalOpacity, activeCount);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("获取信息失败: " + e.getMessage()));
            NodeCraft.LOGGER.error("获取预览信息命令失败", e);
            return 0;
        }
    }
}
