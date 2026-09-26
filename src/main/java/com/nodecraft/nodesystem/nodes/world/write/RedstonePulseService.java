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
        private final BlockState expectedSupportState;
        private final BlockPos sourcePos;
        private final BlockState previousSourceState;
        private final BlockState expectedSourceState;
        private int remainingTicks;

        private Pulse(
            World world,
            BlockPos supportPos,
            BlockState previousSupportState,
            BlockState expectedSupportState,
            BlockPos sourcePos,
            BlockState previousSourceState,
            BlockState expectedSourceState,
            int remainingTicks
        ) {
            this.world = world;
            this.supportPos = supportPos;
            this.previousSupportState = previousSupportState;
            this.expectedSupportState = expectedSupportState;
            this.sourcePos = sourcePos;
            this.previousSourceState = previousSourceState;
            this.expectedSourceState = expectedSourceState;
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
        BlockState expectedSupportState,
        BlockPos sourcePos,
        BlockState previousSourceState,
        BlockState expectedSourceState,
        int durationTicks
    ) {
        pulses.addLast(new Pulse(
            world,
            supportPos,
            previousSupportState,
            expectedSupportState,
            sourcePos,
            previousSourceState,
            expectedSourceState,
            Math.max(1, durationTicks)
        ));
    }

    private void onServerTick(MinecraftServer server) {
        synchronized (this) {
            int remaining = pulses.size();
            while (remaining-- > 0) {
                Pulse pulse = pulses.removeFirst();
                pulse.remainingTicks--;
                if (pulse.remainingTicks <= 0) {
                    // Skip restore when another write replaced the temporary cells.
                    if (pulse.world.getBlockState(pulse.sourcePos).equals(pulse.expectedSourceState)) {
                        pulse.world.setBlockState(pulse.sourcePos, pulse.previousSourceState, 3);
                    }
                    if (pulse.world.getBlockState(pulse.supportPos).equals(pulse.expectedSupportState)) {
                        pulse.world.setBlockState(pulse.supportPos, pulse.previousSupportState, 3);
                    }
                } else {
                    pulses.addLast(pulse);
                }
            }
        }
    }
}
