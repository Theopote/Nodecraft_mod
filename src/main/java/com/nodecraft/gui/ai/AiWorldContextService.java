package com.nodecraft.gui.ai;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class AiWorldContextService {

    private static final double CROSSHAIR_RAYCAST_DISTANCE = 100.0d;

    private AiWorldContextService() {
    }

    public static AiWorldContextSnapshot capture(
            NodeGraph graph,
            INode selectedNode,
            boolean includePlayerAndView,
            boolean includeSelectedRegion
    ) {
        long capturedAtMs = System.currentTimeMillis();
        AiWorldContextSnapshot.SelectedRegionContext selectedRegion = includeSelectedRegion
                ? AiWorldRegionResolver.resolve(graph, selectedNode)
                : null;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return new AiWorldContextSnapshot(
                    false, "not_in_world", capturedAtMs, null, null, selectedRegion
            );
        }
        if (!client.isOnThread()) {
            return new AiWorldContextSnapshot(
                    false, "capture_requires_client_thread", capturedAtMs, null, null, selectedRegion
            );
        }
        if (!includePlayerAndView) {
            return new AiWorldContextSnapshot(true, null, capturedAtMs, null, null, selectedRegion);
        }

        Vec3d look = client.player.getRotationVec(1.0f).normalize();
        AiWorldContextSnapshot.PlayerContext player = new AiWorldContextSnapshot.PlayerContext(
                client.world.getRegistryKey().getValue().toString(),
                vec(client.player.getX(), client.player.getY(), client.player.getZ()),
                vec(client.player.getX(), client.player.getEyeY(), client.player.getZ()),
                client.player.getYaw(),
                client.player.getPitch(),
                vec(look.x, look.y, look.z)
        );

        return new AiWorldContextSnapshot(
                true,
                null,
                capturedAtMs,
                player,
                captureCrosshairTarget(client, look),
                selectedRegion
        );
    }

    private static AiWorldContextSnapshot.CrosshairTarget captureCrosshairTarget(
            MinecraftClient client,
            Vec3d lookDirection
    ) {
        Vec3d start = new Vec3d(client.player.getX(), client.player.getEyeY(), client.player.getZ());
        Vec3d end = start.add(lookDirection.multiply(CROSSHAIR_RAYCAST_DISTANCE));
        BlockHitResult blockHit = client.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        if (blockHit == null || blockHit.getType() == HitResult.Type.MISS) {
            return AiWorldContextSnapshot.CrosshairTarget.miss();
        }
        BlockPos pos = blockHit.getBlockPos();
        Vec3d hitPos = blockHit.getPos();
        String blockId = Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
        return new AiWorldContextSnapshot.CrosshairTarget(
                true,
                new AiWorldContextSnapshot.BlockCoordinate(pos.getX(), pos.getY(), pos.getZ()),
                vec(hitPos.x, hitPos.y, hitPos.z),
                blockHit.getSide().asString(),
                blockId
        );
    }

    private static AiWorldContextSnapshot.Vec3 vec(double x, double y, double z) {
        return new AiWorldContextSnapshot.Vec3(x, y, z);
    }
}
