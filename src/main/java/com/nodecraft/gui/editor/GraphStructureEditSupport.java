package com.nodecraft.gui.editor;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.subgraph.SubgraphEditService;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Runs a multi-step graph structure mutation as a single {@link ImGuiNodeHistory} graph transaction.
 * <p>
 * Pattern matches subgraph create: pause recording, mutate, then one before/after snapshot undo step.
 * On failure, restores the before snapshot and records nothing.
 */
public final class GraphStructureEditSupport {

    public interface Host {
        EditorDocumentState document();

        @Nullable
        ImGuiNodeHistory history();

        boolean restoreDocument(SavedGraph snapshot);
    }

    private GraphStructureEditSupport() {
    }

    public static SavedGraph snapshotDocument(EditorDocumentState document) {
        Objects.requireNonNull(document, "document");
        return SubgraphEditService.toSavedGraphWithDocument(
                document.getGraph(),
                document.getNodePositions(),
                document.getSubgraphDefinitions(),
                document.getComments(),
                document.getGroups()
        );
    }

    /**
     * Executes {@code mutation} under a paused history scope.
     *
     * @return true when mutation completed and (if recording) a transaction was recorded;
     *         false when mutation reported failure via {@link Mutation#run()} returning false
     *         or restore-after-failure ran
     */
    public static boolean run(Host host, String label, Mutation mutation) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(mutation, "mutation");

        EditorDocumentState document = host.document();
        ImGuiNodeHistory history = host.history();
        boolean wasRecording = history != null && history.isRecording();
        SavedGraph before = null;

        if (wasRecording) {
            before = snapshotDocument(document);
            history.pauseRecording();
        }

        try {
            boolean ok = mutation.run();
            if (!ok) {
                if (wasRecording && before != null) {
                    host.restoreDocument(before);
                }
                return false;
            }

            if (wasRecording && history != null && before != null) {
                SavedGraph after = snapshotDocument(document);
                history.resumeRecording();
                history.recordGraphTransaction(label, before, after);
            }
            return true;
        } catch (RuntimeException e) {
            if (wasRecording && before != null) {
                host.restoreDocument(before);
            }
            NodeCraft.LOGGER.error("Graph structure edit '{}' failed: {}", label, e.getMessage(), e);
            return false;
        } finally {
            if (wasRecording && history != null && !history.isRecording()) {
                history.resumeRecording();
            }
        }
    }

    @FunctionalInterface
    public interface Mutation {
        /**
         * @return false to abort and restore the before snapshot (when recording)
         */
        boolean run();
    }
}
