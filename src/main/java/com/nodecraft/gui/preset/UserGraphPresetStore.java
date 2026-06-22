package com.nodecraft.gui.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nodecraft.core.NodeCraft;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class UserGraphPresetStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private UserGraphPresetStore() {
    }

    public static Path resolvePath() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            return gameDir.resolve("nodecraft").resolve("config").resolve("user_graph_presets.json");
        } catch (IllegalStateException e) {
            NodeCraft.LOGGER.warn("Fabric game directory unavailable, falling back to local user preset path.");
            return Paths.get("nodecraft", "config", "user_graph_presets.json");
        }
    }

    public static GraphPresetRules load() {
        Path path = resolvePath();
        try {
            if (!Files.isRegularFile(path)) {
                return defaultRules();
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            GraphPresetRules rules = GSON.fromJson(json, GraphPresetRules.class);
            if (rules == null) {
                return defaultRules();
            }
            if (rules.categories == null) {
                rules.categories = defaultRules().categories;
            }
            return rules;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to load user graph presets: {}", e.getMessage(), e);
            return defaultRules();
        }
    }

    public static void save(GraphPresetRules rules) {
        if (rules == null) {
            return;
        }
        Path path = resolvePath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(rules), StandardCharsets.UTF_8);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to save user graph presets: {}", e.getMessage(), e);
        }
    }

    public static GraphPresetRules defaultRules() {
        GraphPresetRules rules = new GraphPresetRules();
        rules.version = 1;
        GraphPresetRules.PresetCategory category = new GraphPresetRules.PresetCategory();
        category.id = GraphPresetCatalog.DEFAULT_USER_CATEGORY_ID;
        category.displayName = "我的预设";
        category.presets = new java.util.ArrayList<>();
        rules.categories = new java.util.ArrayList<>();
        rules.categories.add(category);
        return rules;
    }
}
