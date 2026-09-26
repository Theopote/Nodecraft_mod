package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.NodeDataType;
import org.jetbrains.annotations.Nullable;

/**
 * Typed user-variable slot: type is fixed on first concrete write.
 */
public record VariableEntry(NodeDataType type, @Nullable Object value) {

    public VariableEntry {
        if (type == null) {
            type = NodeDataType.ANY;
        }
    }

    public static VariableEntry of(NodeDataType type, @Nullable Object value) {
        return new VariableEntry(type == null ? NodeDataType.ANY : type, value);
    }
}
