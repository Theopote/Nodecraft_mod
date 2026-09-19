package com.nodecraft.nodesystem.graph;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.nodecraft.core.NodeCraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the V0→V1 graph migration manifest bundled under {@code nodecraft/migration/v0-to-v1.json}.
 */
public final class GraphMigrationManifest {

    private static final String RESOURCE_PATH = "/nodecraft/migration/v0-to-v1.json";
    private static final Gson GSON = new Gson();
    private static volatile GraphMigrationManifest INSTANCE;

    @SerializedName("manifestVersion")
    private int manifestVersion;
    @SerializedName("fromFormatVersion")
    private int fromFormatVersion;
    @SerializedName("toFormatVersion")
    private int toFormatVersion;
    @SerializedName("nodeTypes")
    private Map<String, String> nodeTypes = Map.of();
    @SerializedName("globalOutputPortAliases")
    private Map<String, String> globalOutputPortAliases = Map.of();
    @SerializedName("globalInputPortAliases")
    private Map<String, String> globalInputPortAliases = Map.of();
    @SerializedName("nodePortAliases")
    private Map<String, Map<String, String>> nodePortAliases = Map.of();
    @SerializedName("nodePropertyRenames")
    private Map<String, Map<String, String>> nodePropertyRenames = Map.of();
    @SerializedName("enumValueRenames")
    private Map<String, Map<String, String>> enumValueRenames = Map.of();
    @SerializedName("removedNodeReplacements")
    private Map<String, String> removedNodeReplacements = Map.of();

    private GraphMigrationManifest() {
    }

    public static GraphMigrationManifest get() {
        GraphMigrationManifest cached = INSTANCE;
        if (cached != null) {
            return cached;
        }
        synchronized (GraphMigrationManifest.class) {
            if (INSTANCE == null) {
                INSTANCE = load();
            }
            return INSTANCE;
        }
    }

    static void resetForTests() {
        INSTANCE = null;
    }

    private static GraphMigrationManifest load() {
        try (InputStream stream = GraphMigrationManifest.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Missing migration manifest resource: " + RESOURCE_PATH);
            }
            GraphMigrationManifest manifest = GSON.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    GraphMigrationManifest.class
            );
            if (manifest == null) {
                throw new IllegalStateException("Migration manifest parsed to null: " + RESOURCE_PATH);
            }
            manifest.freezeMaps();
            return manifest;
        } catch (Exception e) {
            throw new IllegalStateException("Failed loading migration manifest: " + RESOURCE_PATH, e);
        }
    }

    private void freezeMaps() {
        nodeTypes = freezeStringMap(nodeTypes);
        globalOutputPortAliases = freezeStringMap(globalOutputPortAliases);
        globalInputPortAliases = freezeStringMap(globalInputPortAliases);
        nodePortAliases = freezeNestedMap(nodePortAliases);
        nodePropertyRenames = freezeNestedMap(nodePropertyRenames);
        enumValueRenames = freezeNestedMap(enumValueRenames);
        removedNodeReplacements = freezeStringMap(removedNodeReplacements);
    }

    private static Map<String, String> freezeStringMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey().toLowerCase(), entry.getValue().toLowerCase());
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static Map<String, Map<String, String>> freezeNestedMap(Map<String, Map<String, String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> normalized = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey().toLowerCase(), freezeStringMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(normalized);
    }

    public int manifestVersion() {
        return manifestVersion;
    }

    public int fromFormatVersion() {
        return fromFormatVersion;
    }

    public int toFormatVersion() {
        return toFormatVersion;
    }

    public String migrateNodeTypeId(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return typeId;
        }
        String normalized = typeId.toLowerCase();
        String migrated = nodeTypes.getOrDefault(normalized, normalized);
        String replacement = removedNodeReplacements.get(migrated);
        if (replacement != null) {
            migrated = replacement;
        }
        return migrated;
    }

    public String migratePortId(String nodeTypeId, String portId, boolean isOutput) {
        if (portId == null || portId.isBlank()) {
            return portId;
        }
        if (portId.startsWith("output_") || portId.startsWith("input_")) {
            return portId;
        }

        String normalizedType = nodeTypeId == null ? "" : nodeTypeId.toLowerCase();
        Map<String, String> nodeSpecific = nodePortAliases.get(normalizedType);
        if (nodeSpecific != null) {
            String mapped = nodeSpecific.get(portId.toLowerCase());
            if (mapped != null) {
                return mapped;
            }
        }

        Map<String, String> global = isOutput ? globalOutputPortAliases : globalInputPortAliases;
        return global.getOrDefault(portId.toLowerCase(), prefixPort(portId, isOutput));
    }

    public Object migrateNodeState(String nodeTypeId, Object state) {
        if (!(state instanceof Map<?, ?> stateMap) || nodeTypeId == null) {
            return state;
        }
        Map<String, String> propertyRenames = nodePropertyRenames.get(nodeTypeId.toLowerCase());
        if (propertyRenames == null || propertyRenames.isEmpty()) {
            return state;
        }

        Map<String, Object> migrated = new HashMap<>();
        for (Map.Entry<?, ?> entry : stateMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            String newKey = propertyRenames.getOrDefault(key, key);
            Object value = entry.getValue();
            Map<String, String> enumRenames = enumValueRenames.get(nodeTypeId.toLowerCase());
            if (enumRenames != null && value instanceof String stringValue) {
                value = enumRenames.getOrDefault(stringValue, stringValue);
            }
            migrated.put(newKey, value);
        }
        return migrated;
    }

    public Map<String, String> nodeTypeAliases() {
        return nodeTypes;
    }

    private static String prefixPort(String portId, boolean isOutput) {
        String prefix = isOutput ? "output_" : "input_";
        return prefix + portId.toLowerCase();
    }

    static GraphMigrationManifest loadUncheckedForTests() {
        try {
            return load();
        } catch (RuntimeException e) {
            NodeCraft.LOGGER.error("Migration manifest load failed during tests", e);
            throw e;
        }
    }
}
