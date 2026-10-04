package com.nodecraft.nodesystem.util;

import net.minecraft.SharedConstants;

/**
 * Central Minecraft data-version lookup for structure export formats.
 */
public final class MinecraftFormatVersion {

    /** Fallback when game version APIs are unavailable (e.g. unit-test classpath). */
    public static final int FALLBACK_DATA_VERSION = 3700;

    private MinecraftFormatVersion() {
    }

    public static int dataVersion() {
        try {
            SharedConstants.createGameVersion();
            return SharedConstants.getGameVersion().dataVersion().id();
        } catch (Throwable ignored) {
            return FALLBACK_DATA_VERSION;
        }
    }
}
