package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Graph load entry for format version handling (development stage).
 * <p>
 * Does <strong>not</strong> run historical port/type remaps. Payloads older than
 * {@link GraphFormatVersion#CURRENT} are normalized and stamped to CURRENT.
 * Future versions load best-effort without stamping.
 */
public final class GraphMigrationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphMigrationRegistry.class);

    private GraphMigrationRegistry() {
    }

    /**
     * Normalize structure and align {@code formatVersion} to {@link GraphFormatVersion#CURRENT}
     * when the payload is older. No compatibility remaps.
     */
    public static SavedGraph migrateToCurrent(SavedGraph input) {
        if (input == null) {
            return null;
        }

        SavedGraph graph = SavedGraphNormalizer.normalizeStructure(input);
        int version = GraphFormatVersion.normalize(graph.formatVersion);

        if (GraphFormatVersion.isNewerThanCurrent(version)) {
            LOGGER.warn(
                    "Saved graph format version {} is newer than supported version {}. Loading best-effort without migration.",
                    version,
                    GraphFormatVersion.CURRENT
            );
            return graph;
        }

        if (version != GraphFormatVersion.CURRENT) {
            LOGGER.debug(
                    "Stamping graph format version {} → {} (dev policy: no historical remaps).",
                    version,
                    GraphFormatVersion.CURRENT
            );
            graph.formatVersion = GraphFormatVersion.CURRENT;
        }
        return graph;
    }
}
