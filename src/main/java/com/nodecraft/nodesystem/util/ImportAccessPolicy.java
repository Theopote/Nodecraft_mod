package com.nodecraft.nodesystem.util;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Injectable gate for default (non-external) import path reads.
 * Tests may replace the policy via {@link #setCurrent(ImportAccessPolicy)} and restore with {@link #reset()}.
 */
public interface ImportAccessPolicy {

    boolean allows(Path path, ImportPathUtil.ImportKind kind);

    /**
     * Default policy: allow only paths accepted by {@link ImportPathUtil#isAllowedDefaultPath(Path, ImportPathUtil.ImportKind)}.
     */
    static ImportAccessPolicy allowlistOnly() {
        return ImportPathUtil::isAllowedDefaultPath;
    }

    static ImportAccessPolicy current() {
        return Holder.CURRENT.get();
    }

    static void setCurrent(ImportAccessPolicy policy) {
        Holder.CURRENT.set(policy != null ? policy : allowlistOnly());
    }

    static void reset() {
        Holder.CURRENT.set(allowlistOnly());
    }

    final class Holder {
        private static final AtomicReference<ImportAccessPolicy> CURRENT =
            new AtomicReference<>(allowlistOnly());

        private Holder() {
        }
    }
}
