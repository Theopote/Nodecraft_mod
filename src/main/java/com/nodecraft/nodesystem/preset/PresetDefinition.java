package com.nodecraft.nodesystem.preset;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a complete preset definition loaded from JSON.
 *
 * <p>A preset encapsulates a reusable node graph template with configurable parameters.
 * Users can instantiate presets to quickly create common building patterns without
 * manually constructing node graphs.</p>
 */
public record PresetDefinition(String presetId, String version, String schemaVersion, PresetMetadata metadata,
                               List<PresetParameter> parameters, PresetGraph graph, PresetDocumentation documentation,
                               PresetThumbnails thumbnails) {

    /**
     * Gets a parameter by its ID.
     *
     * @param parameterId the parameter identifier
     * @return the parameter, or null if not found
     */
    public PresetParameter getParameter(String parameterId) {
        return parameters.stream()
                .filter(p -> p.id().equals(parameterId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets default parameter values as a map.
     *
     * @return map of parameter ID to default value
     */
    public Map<String, Object> getDefaultParameterValues() {
        return parameters.stream()
                .collect(Collectors.toMap(
                        PresetParameter::id,
                        PresetParameter::defaultValue
                ));
    }

    @Override
    public @NonNull String toString() {
        return "PresetDefinition{" +
                "presetId='" + presetId + '\'' +
                ", version='" + version + '\'' +
                ", name='" + metadata.getName() + '\'' +
                '}';
    }
}
