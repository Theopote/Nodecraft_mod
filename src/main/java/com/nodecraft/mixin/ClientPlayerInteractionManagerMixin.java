package com.nodecraft.mixin;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.screens.NodecraftScreen;
import com.nodecraft.minecraft.client.MinecraftClientController;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ClientPlayerInteractionManager的Mixin
 * 适配"幽灵相机"模式，拦截方块交互并转发给NodeCraft系统
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    // 我们想要支持的最大方块交互距离
    @Unique
    private static final double MAX_INTERACTION_DISTANCE = 100.0;
    
    /**
     * 拦截方块右键交互。
     * 在NodeCraft界面打开时，使用游戏原生的目标方块并转发给控制器。
     */
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(net.minecraft.client.network.ClientPlayerEntity player, 
                                Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.currentScreen instanceof NodecraftScreen) {
            // 在"幽灵相机"模式下，游戏原生的hitResult就是我们需要的，无需自定义射线检测。
            BlockPos pos = hitResult.getBlockPos();
            double distance = player.getEyePos().distanceTo(pos.toCenterPos());
            
            if (distance <= MAX_INTERACTION_DISTANCE) {
                MinecraftClientController controller = MinecraftClientController.getInstance();
                try {
                    ClientWorld world = client.world;
                    if (world != null) {
                        BlockState blockState = world.getBlockState(pos);
                        String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
                        
                        com.nodecraft.nodesystem.util.BlockStateData blockStateData = new com.nodecraft.nodesystem.util.BlockStateData();
                        blockState.getProperties().forEach(property -> {
                            try {
                                String key = property.getName();
                                String value = blockState.get(property).toString();
                                blockStateData.setProperty(key, value);
                            } catch (Exception e) {
                                // 忽略无法获取的属性
                            }
                        });
                        
                        // 将交互转发给NodeCraft系统处理 - 使用新的左键点击API
                        com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager interactionManager = 
                            com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager.getInstance();
                        com.nodecraft.nodesystem.util.Coordinate coordinate = 
                            new com.nodecraft.nodesystem.util.Coordinate(pos.getX(), pos.getY(), pos.getZ());
                        boolean handled = interactionManager.handleBlockLeftClick(coordinate, blockId, blockStateData);
                        
                        // 如果NodeCraft处理了事件，返回成功以消费它。
                        // 如果未处理，也消费掉，以阻止游戏默认行为（如开箱）。
                        cir.setReturnValue(handled ? ActionResult.SUCCESS : ActionResult.PASS);
                        return; // 直接返回，不再执行后续代码
                    }
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("处理NodeCraft方块右键交互时出错", e);
                }
            }
            
            // 如果距离超限或发生错误，总是阻止默认交互。
            cir.setReturnValue(ActionResult.PASS);
        }
    }
    
    /**
     * 拦截方块左键攻击。
     * 在NodeCraft界面打开时，完全阻止默认的方块破坏行为。
     */
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, net.minecraft.util.math.Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.currentScreen instanceof NodecraftScreen) {
            // 在编辑器模式下，应阻止玩家通过长按来破坏方块。
            // 任何方块移除都应通过专门的编辑器工具来完成。
            cir.setReturnValue(false);
        }
    }
} 