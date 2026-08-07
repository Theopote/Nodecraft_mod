package com.nodecraft.nodesystem.io;

/**
 * @deprecated Use {@link GraphFormatVersion}. Kept as a compatibility alias for older call sites.
 */
@Deprecated
public final class GraphFormat {

    /** @deprecated Use {@link GraphFormatVersion#LEGACY_UNSPECIFIED}. */
    @Deprecated
    public static final int LEGACY_UNSPECIFIED = GraphFormatVersion.LEGACY_UNSPECIFIED;

    /** @deprecated Use {@link GraphFormatVersion#CURRENT}. */
    @Deprecated
    public static final int CURRENT = GraphFormatVersion.CURRENT;

    private GraphFormat() {
    }
}
