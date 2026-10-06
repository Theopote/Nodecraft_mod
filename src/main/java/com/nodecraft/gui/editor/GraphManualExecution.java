package com.nodecraft.gui.editor;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.ExecutionPlan;
import com.nodecraft.nodesystem.execution.runtime.ExecutionSession;
import com.nodecraft.nodesystem.execution.runtime.NodeExecutionScheduler;
import com.nodecraft.nodesystem.graph.NodeGraph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Submits a MANUAL graph run (exec-flow when EXEC wires exist; WORLD_WRITE allowed).
 */
public final class GraphManualExecution {

    private GraphManualExecution() {
    }

    public static @Nullable ExecutionSession submit(NodeGraph graph) {
        if (graph == null || graph.getNodes().isEmpty()) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            NodeCraft.LOGGER.error("Cannot submit manual graph run: client is not in a world");
            return null;
        }

        World world = client.world;
        ServerPlayerEntity serverPlayer = null;
        IntegratedServer integratedServer = client.getServer();
        if (integratedServer != null && client.player != null) {
            serverPlayer = integratedServer.getPlayerManager().getPlayer(client.player.getUuid());
            if (serverPlayer != null) {
                world = integratedServer.getOverworld();
            }
        }

        ExecutionContext context = new ExecutionContext(world, serverPlayer);
        return NodeExecutionScheduler.client().submit(graph, context, ExecutionPlan.manual(null), 0L);
    }
}
