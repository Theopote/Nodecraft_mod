package com.nodecraft.nodesystem.nodes.world.write;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Deque;

final class RedstonePulseService {

    private static final class Pulse {
        private final World world;
        private final BlockPos supportPos;
        private final BlockState previousSupportState;
        private final BlockPos sourcePos;
        private final BlockState previousSourceState;
        private int remainingTicks;

        private Pulse(
            World world,
            BlockPos supportPos,
            BlockState previousSupportState,
            BlockPos sourcePos,
            BlockState previousSourceState,
            int remainingTicks
        ) {
            this.world = world;
            this.supportPos = supportPos;
            this.previousSupportState = previousSupportState;
            this.sourcePos = sourcePos;
            this.previousSourceState = previousSourceState;
            this.remainingTicks = remainingTicks;
        }
    }

    private static final RedstonePulseService INSTANCE = new RedstonePulseService();

    private final Deque<Pulse> pulses = new ArrayDeque<>();
    private boolean registered;

    static RedstonePulseService getInstance() {
        return INSTANCE;
    }

    synchronized void ensureRegistered() {
        if (registered) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        registered = true;
    }

    synchronized void enqueue(
        World world,
        BlockPos supportPos,
        BlockState previousSupportState,
        BlockPos sourcePos,
        BlockState previousSourceState,
        int durationTicks
    ) {
        pulses.addLast(new Pulse(world, supportPos, previousSupportState, sourcePos, previousSourceState, Math.max(1, durationTicks)));
    }

    private void onServerTick(MinecraftServer server) {
        synchronized (this) {
            int remaining = pulses.size();
            while (remaining-- > 0) {
                Pulse pulse = pulses.removeFirst();
                pulse.remainingTicks--;
                if (pulse.remainingTicks <= 0) {
                    pulse.world.setBlockState(pulse.sourcePos, pulse.previousSourceState, 3);
                    pulse.world.setBlockState(pulse.supportPos, pulse.previousSupportState, 3);
                } else {
                    pulses.addLast(pulse);
                }
            }
        }
    }
}
