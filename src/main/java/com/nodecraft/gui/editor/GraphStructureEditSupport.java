package com.nodecraft.gui.editor;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.subgraph.SubgraphEditService;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Runs a multi-step graph structure mutation with snapshot rollback independent of Undo recording.
 * <p>
 * Always snapshots before mutating and restores on mutation/commit failures that leave the graph
 * inconsistent. When the mutation and dirty notify succeed but Undo recording fails, the edit is
 * kept and {@link Outcome#SUCCESS_WITHOUT_UNDO} is returned so callers can surface a visible warning.
 */
public final class GraphStructureEditSupport {

    public enum Outcome {
        /** Mutation completed; Undo recorded when history was recording at entry. */
        SUCCESS,
        /**
         * Mutation and dirty notify completed, but Undo recording failed while history was expected.
         * The graph keeps the edit; callers must not treat this as a silent full success.
         */
        SUCCESS_WITHOUT_UNDO,
        /** Mutation aborted or threw; before-snapshot restored successfully. */
        ABORTED_ROLLED_BACK,
        /** Mutation aborted and restoreDocument returned false (document may be inconsistent). */
        ABORTED_ROLLBACK_FAILED
    }

    public interface Host {
        EditorDocumentState document();

        @Nullable
        ImGuiNodeHistory history();

        boolean restoreDocument(SavedGraph snapshot);

        /** Invoked once after a successful mutation so auto-preview can invalidate. */
        void notifyStructureDirty();

        /**
         * Captures the current document for rollback / Undo. Override in tests to inject commit failures.
         */
        default SavedGraph captureSnapshot() {
            return GraphStructureEditSupport.snapshotDocument(document());
        }

        /**
         * Visible feedback when a destructive structure edit applied but Undo could not be recorded.
         * Default is no-op for headless hosts; the editor overrides with a dialog.
         */
        default void notifyHistoryRecordFailed(String label, @Nullable Throwable cause) {
        }
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

    /** True when the graph mutation was applied (with or without an Undo record). */
    public static boolean succeeded(Outcome outcome) {
        return outcome == Outcome.SUCCESS || outcome == Outcome.SUCCESS_WITHOUT_UNDO;
    }

    /** True only when mutation applied and Undo was recorded (or recording was not expected). */
    public static boolean fullySucceeded(Outcome outcome) {
        return outcome == Outcome.SUCCESS;
    }

    public static boolean appliedWithoutUndo(Outcome outcome) {
        return outcome == Outcome.SUCCESS_WITHOUT_UNDO;
    }

    /**
     * Executes {@code mutation} with always-on snapshot rollback.
     *
     * @return {@link Outcome#SUCCESS}, {@link Outcome#SUCCESS_WITHOUT_UNDO}, or {@link Outcome#ABORTED_ROLLED_BACK}
     * @throws StructureEditRollbackException when abort restore fails
     */
    public static Outcome run(Host host, String label, Mutation mutation) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(mutation, "mutation");

        EditorDocumentState document = host.document();
        if (document == null || document.getGraph() == null) {
            NodeCraft.LOGGER.warn("Graph structure edit '{}': no document graph", label);
            return Outcome.ABORTED_ROLLED_BACK;
        }

        ImGuiNodeHistory history = host.history();
        boolean wasRecording = history != null && history.isRecording();
        SavedGraph before = host.captureSnapshot();

        if (wasRecording) {
            history.pauseRecording();
        }

        try {
            boolean ok;
            try {
                ok = mutation.run();
            } catch (RuntimeException e) {
                NodeCraft.LOGGER.error("Graph structure edit '{}' failed: {}", label, e.getMessage(), e);
                restoreOrThrow(host, before, label, e);
                return Outcome.ABORTED_ROLLED_BACK;
            }

            if (!ok) {
                restoreOrThrow(host, before, label, null);
                return Outcome.ABORTED_ROLLED_BACK;
            }

            try {
                commitAfterMutation(host, history, wasRecording, before, label);
            } catch (HistoryRecordException historyError) {
                // Mutation + dirty notify already applied; only Undo recording failed. Keep the edit
                // so Delete/structure changes do not silently snap back when Gson/history chokes.
                NodeCraft.LOGGER.error(
                        "Graph structure edit '{}' history record failed after mutation; keeping edit without Undo: {}",
                        label,
                        historyError.getMessage(),
                        historyError);
                try {
                    host.notifyHistoryRecordFailed(label, historyError.getCause());
                } catch (RuntimeException notifyError) {
                    NodeCraft.LOGGER.error(
                            "Graph structure edit '{}' history-failure notify threw: {}",
                            label,
                            notifyError.getMessage(),
                            notifyError);
                }
                return Outcome.SUCCESS_WITHOUT_UNDO;
            } catch (RuntimeException commitError) {
                NodeCraft.LOGGER.error(
                        "Graph structure edit '{}' commit failed: {}",
                        label,
                        commitError.getMessage(),
                        commitError);
                restoreOrThrow(host, before, label, commitError);
                return Outcome.ABORTED_ROLLED_BACK;
            }
            return Outcome.SUCCESS;
        } finally {
            if (history != null) {
                history.setRecording(wasRecording);
            }
        }
    }

    /**
     * After-snapshot → dirty notify → history record.
     * Notify runs before history so a notify failure cannot leave an Undo for a rolled-back graph.
     */
    private static void commitAfterMutation(
            Host host,
            @Nullable ImGuiNodeHistory history,
            boolean wasRecording,
            SavedGraph before,
            String label
    ) {
        SavedGraph after = null;
        if (wasRecording && history != null) {
            after = host.captureSnapshot();
        }
        host.notifyStructureDirty();
        if (wasRecording && history != null) {
            history.setRecording(true);
            int undoSizeBefore = history.undoStackSize();
            try {
                history.recordGraphTransaction(label, before, after);
            } catch (RuntimeException recordError) {
                while (history.undoStackSize() > undoSizeBefore) {
                    history.discardLastUndo();
                }
                throw new HistoryRecordException(recordError);
            }
        }
    }

    /** History Undo recording failed after the graph mutation and dirty notify already succeeded. */
    private static final class HistoryRecordException extends RuntimeException {
        private HistoryRecordException(Throwable cause) {
            super(cause);
        }
    }

    private static void restoreOrThrow(
            Host host,
            SavedGraph before,
            String label,
            @Nullable Throwable mutationCause
    ) {
        boolean restored;
        try {
            restored = host.restoreDocument(before);
        } catch (RuntimeException restoreError) {
            NodeCraft.LOGGER.error(
                    "Graph structure edit '{}': restore threw after abort",
                    label,
                    restoreError);
            throw new StructureEditRollbackException(
                    label,
                    "Structure edit '" + label + "' aborted and restore threw; document may be inconsistent",
                    restoreError);
        }
        if (!restored) {
            NodeCraft.LOGGER.error(
                    "Graph structure edit '{}': restoreDocument returned false after abort",
                    label);
            StructureEditRollbackException ex = new StructureEditRollbackException(
                    label,
                    "Structure edit '" + label + "' aborted and restore failed; document may be inconsistent");
            if (mutationCause != null) {
                ex.addSuppressed(mutationCause);
            }
            throw ex;
        }
    }

    @FunctionalInterface
    public interface Mutation {
        /**
         * @return false to abort and restore the before snapshot
         */
        boolean run();
    }
}
