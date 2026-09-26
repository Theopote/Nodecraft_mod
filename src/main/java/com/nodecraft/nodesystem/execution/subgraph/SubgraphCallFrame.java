package com.nodecraft.nodesystem.execution.subgraph;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Isolated input/output bindings for a single subgraph invocation.
 */
public final class SubgraphCallFrame {

    private final String ref;
    private final Map<String, Object> inputs;
    private final Map<String, Object> outputs;
    private final int depth;
    @Nullable
    private final SubgraphCallFrame parent;

    public SubgraphCallFrame(
            String ref,
            Map<String, Object> inputs,
            int depth,
            @Nullable SubgraphCallFrame parent
    ) {
        this.ref = ref == null ? "" : ref.trim();
        this.inputs = new LinkedHashMap<>();
        if (inputs != null) {
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                if (entry.getKey() != null) {
                    this.inputs.put(entry.getKey(), entry.getValue());
                }
            }
        }
        this.outputs = new LinkedHashMap<>();
        this.depth = Math.max(0, depth);
        this.parent = parent;
    }

    public String ref() {
        return ref;
    }

    public Map<String, Object> inputs() {
        return inputs;
    }

    public Map<String, Object> outputs() {
        return outputs;
    }

    public int depth() {
        return depth;
    }

    @Nullable
    public SubgraphCallFrame parent() {
        return parent;
    }
}
