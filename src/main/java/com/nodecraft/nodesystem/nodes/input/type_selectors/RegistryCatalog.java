package com.nodecraft.nodesystem.nodes.input.type_selectors;

import java.util.List;

/**
 * Registry id catalog for type-selector pickers (editor convenience).
 *
 * @param ids             sorted registry or UI-fallback ids
 * @param authoritative   {@code true} when ids come from a live/static registry;
 *                        {@code false} for UI-only fallbacks. Does not gate Graph {@code Valid}.
 */
public record RegistryCatalog(List<String> ids, boolean authoritative) {

    public RegistryCatalog {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}
