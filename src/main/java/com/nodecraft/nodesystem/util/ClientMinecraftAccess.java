package com.nodecraft.nodesystem.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Reflective access to client-only Minecraft types so common/server class loading never
 * hard-depends on {@code MinecraftClient} / {@code ClientWorld}.
 * <p>
 * On dedicated servers {@link Class#forName(String)} fails closed and methods return null.
 */
public final class ClientMinecraftAccess {

    private ClientMinecraftAccess() {
    }

    public static @Nullable PlayerEntity clientPlayerOrNull() {
        Object client = clientInstanceOrNull();
        if (client == null) {
            return null;
        }
        try {
            Object player = client.getClass().getField("player").get(client);
            return player instanceof PlayerEntity entity ? entity : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static @Nullable World clientWorldOrNull() {
        Object client = clientInstanceOrNull();
        if (client == null) {
            return null;
        }
        try {
            Object world = client.getClass().getField("world").get(client);
            return world instanceof World w ? w : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object clientInstanceOrNull() {
        try {
            Class<?> clientClass = Class.forName("net.minecraft.client.MinecraftClient");
            return clientClass.getMethod("getInstance").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
