package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Normalizes loaded {@link SavedGraph} payloads to a safe in-memory shape before migration.
 */
public final class SavedGraphNormalizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SavedGraphNormalizer.class);

    private SavedGraphNormalizer() {
    }

    /**
     * @deprecated Use {@link GraphMigrationRegistry#migrateToCurrent(SavedGraph)} for load paths.
     */
    @Deprecated
    public static SavedGraph normalize(SavedGraph graph) {
        return GraphMigrationRegistry.migrateToCurrent(graph);
    }

    static SavedGraph normalizeStructure(SavedGraph graph) {
        if (graph == null) {
            return null;
        }

        if (graph.formatVersion > GraphFormatVersion.CURRENT) {
            LOGGER.warn(
                    "Saved graph format version {} is newer than supported version {}.",
                    graph.formatVersion,
                    GraphFormatVersion.CURRENT
            );
        }

        if (graph.nodes == null) {
            graph.nodes = new ArrayList<>();
        }
        if (graph.connections == null) {
            graph.connections = new ArrayList<>();
        }
        if (graph.nodePositions == null) {
            graph.nodePositions = new HashMap<>();
        }

        return graph;
    }
}
