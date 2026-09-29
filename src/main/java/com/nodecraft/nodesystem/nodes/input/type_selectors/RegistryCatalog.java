package com.nodecraft.nodesystem.nodes.input.type_selectors;

import java.util.List;

/**
 * Registry id catalog for type-selector pickers.
 *
 * @param ids             sorted registry or UI-fallback ids
 * @param authoritative   {@code true} when ids come from a live/static registry that may
 *                        prove Graph {@code Valid}; {@code false} for UI-only fallbacks
 */
public record RegistryCatalog(List<String> ids, boolean authoritative) {

    public RegistryCatalog {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}
