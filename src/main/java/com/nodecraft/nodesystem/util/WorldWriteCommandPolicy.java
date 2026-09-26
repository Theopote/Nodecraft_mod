package com.nodecraft.nodesystem.util;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gate for {@code world.write.execute_command}. Default denied; tests may enable via {@link #setAllowed}.
 */
public final class WorldWriteCommandPolicy {

    private static final AtomicBoolean ALLOWED = new AtomicBoolean(false);

    private WorldWriteCommandPolicy() {
    }

    public static boolean isAllowed() {
        return ALLOWED.get();
    }

    public static void setAllowed(boolean allowed) {
        ALLOWED.set(allowed);
    }

    public static void reset() {
        ALLOWED.set(false);
    }
}
