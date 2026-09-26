package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.io.SavedGraph;
import org.jetbrains.annotations.Nullable;

/**
 * Validates graph-level input/output interface invariants.
 */
public final class GraphInterfaceValidator {

    private GraphInterfaceValidator() {
    }

    public record ValidationResult(boolean valid, @Nullable String error) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String error) {
            return new ValidationResult(false, error);
        }
    }

    public static ValidationResult validate(SavedGraph saved) {
        SubgraphInterfaceScanner.InterfaceSpec spec = SubgraphInterfaceScanner.scan(saved);
        if (!spec.valid()) {
            return ValidationResult.fail(spec.error());
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validate(NodeGraph graph) {
        SubgraphInterfaceScanner.InterfaceSpec spec = SubgraphInterfaceScanner.scan(graph);
        if (!spec.valid()) {
            return ValidationResult.fail(spec.error());
        }
        return ValidationResult.ok();
    }
}
