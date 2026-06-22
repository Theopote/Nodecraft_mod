package com.nodecraft.gui.preset;

import com.nodecraft.core.NodeCraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GraphPresetCatalog {

    public static final String DEFAULT_USER_CATEGORY_ID = "user.my_presets";
    public static final String PRESET_DRAG_PAYLOAD = "DND_PRESET_FROM_LIBRARY";
    public static final String PRESET_REORDER_PAYLOAD = "DND_PRESET_LIBRARY_REORDER";

    public enum EntrySource {
        BUILTIN,
        USER
    }

    public record CategoryView(GraphPresetRules.PresetCategory category, EntrySource source) {
        public boolean isEditable() {
            return source == EntrySource.USER;
        }
    }

    public record PresetView(
            GraphPresetRules.GraphPresetDefinition preset,
            String categoryId,
            EntrySource source,
            int indexInCategory) {
        public boolean isEditable() {
            return source == EntrySource.USER;
        }

        public boolean isApplicable() {
            return "composite".equalsIgnoreCase(preset.kind);
        }
    }

    private static GraphPresetCatalog instance;

    private GraphPresetRules builtinRules = new GraphPresetRules();
    private GraphPresetRules userRules = UserGraphPresetStore.defaultRules();

    private GraphPresetCatalog() {
        reload();
    }

    public static GraphPresetCatalog getInstance() {
        if (instance == null) {
            instance = new GraphPresetCatalog();
        }
        return instance;
    }

    public synchronized void reload() {
        builtinRules = GraphPresetLoader.load();
        userRules = UserGraphPresetStore.load();
        ensureDefaultUserCategory();
    }

    public synchronized List<CategoryView> getCategories() {
        List<CategoryView> views = new ArrayList<>();
        appendCategoryViews(builtinRules, EntrySource.BUILTIN, views);
        appendCategoryViews(userRules, EntrySource.USER, views);
        return List.copyOf(views);
    }

    public synchronized List<CategoryView> getUserCategories() {
        return getCategories().stream().filter(CategoryView::isEditable).toList();
    }

    public synchronized PresetView findPreset(String presetId) {
        if (presetId == null) {
            return null;
        }
        PresetView found = findPresetInRules(builtinRules, EntrySource.BUILTIN, presetId);
        if (found != null) {
            return found;
        }
        return findPresetInRules(userRules, EntrySource.USER, presetId);
    }

    public synchronized GraphPresetRules.GraphPresetDefinition getPresetDefinition(String presetId) {
        PresetView view = findPreset(presetId);
        return view != null ? view.preset() : null;
    }

    public synchronized String addUserPreset(String categoryId, GraphPresetRules.GraphPresetDefinition preset) {
        if (preset == null) {
            return null;
        }
        ensureDefaultUserCategory();
        GraphPresetRules.PresetCategory category = findUserCategory(categoryId);
        if (category == null) {
            category = findUserCategory(DEFAULT_USER_CATEGORY_ID);
        }
        if (category.presets == null) {
            category.presets = new ArrayList<>();
        }

        preset.id = generateUserPresetId(preset.displayName);
        preset.kind = "composite";
        category.presets.add(preset);
        persistUserRules();
        return preset.id;
    }

    public synchronized boolean deleteUserPreset(String presetId) {
        if (presetId == null) {
            return false;
        }
        for (GraphPresetRules.PresetCategory category : userRules.categories) {
            if (category == null || category.presets == null) {
                continue;
            }
            if (category.presets.removeIf(p -> p != null && presetId.equals(p.id))) {
                persistUserRules();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean renameUserPreset(String presetId, String displayName, String description) {
        GraphPresetRules.GraphPresetDefinition preset = findUserPreset(presetId);
        if (preset == null || isBlank(displayName)) {
            return false;
        }
        preset.displayName = displayName.trim();
        if (description != null) {
            preset.description = description.trim();
        }
        persistUserRules();
        return true;
    }

    public synchronized String createUserCategory(String displayName) {
        if (isBlank(displayName)) {
            return null;
        }
        ensureDefaultUserCategory();
        if (userRules.categories == null) {
            userRules.categories = new ArrayList<>();
        }

        GraphPresetRules.PresetCategory category = new GraphPresetRules.PresetCategory();
        category.id = "user.cat." + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        category.displayName = displayName.trim();
        category.presets = new ArrayList<>();
        userRules.categories.add(category);
        persistUserRules();
        return category.id;
    }

    public synchronized boolean renameUserCategory(String categoryId, String displayName) {
        GraphPresetRules.PresetCategory category = findUserCategory(categoryId);
        if (category == null || isBlank(displayName)) {
            return false;
        }
        category.displayName = displayName.trim();
        persistUserRules();
        return true;
    }

    public synchronized boolean deleteUserCategory(String categoryId) {
        if (categoryId == null || userRules.categories == null) {
            return false;
        }
        if (DEFAULT_USER_CATEGORY_ID.equals(categoryId)) {
            return false;
        }
        GraphPresetRules.PresetCategory target = findUserCategory(categoryId);
        if (target == null) {
            return false;
        }
        if (target.presets != null && !target.presets.isEmpty()) {
            GraphPresetRules.PresetCategory fallback = findUserCategory(DEFAULT_USER_CATEGORY_ID);
            if (fallback != null) {
                if (fallback.presets == null) {
                    fallback.presets = new ArrayList<>();
                }
                fallback.presets.addAll(target.presets);
            }
        }
        boolean removed = userRules.categories.removeIf(c -> c != null && categoryId.equals(c.id));
        if (removed) {
            persistUserRules();
        }
        return removed;
    }

    public synchronized boolean moveUserPreset(String presetId, String targetCategoryId, int targetIndex) {
        String sourceCategoryId = null;
        int sourceIndex = -1;
        if (userRules.categories != null) {
            for (GraphPresetRules.PresetCategory category : userRules.categories) {
                if (category == null || category.presets == null) {
                    continue;
                }
                for (int i = 0; i < category.presets.size(); i++) {
                    GraphPresetRules.GraphPresetDefinition candidate = category.presets.get(i);
                    if (candidate != null && presetId.equals(candidate.id)) {
                        sourceCategoryId = category.id;
                        sourceIndex = i;
                        break;
                    }
                }
                if (sourceCategoryId != null) {
                    break;
                }
            }
        }

        GraphPresetRules.GraphPresetDefinition preset = removeUserPresetFromCategory(presetId);
        if (preset == null) {
            return false;
        }
        GraphPresetRules.PresetCategory target = findUserCategory(targetCategoryId);
        if (target == null) {
            target = findUserCategory(DEFAULT_USER_CATEGORY_ID);
        }
        if (target.presets == null) {
            target.presets = new ArrayList<>();
        }

        int clampedIndex = Math.max(0, Math.min(targetIndex, target.presets.size()));
        if (sourceCategoryId != null
                && sourceCategoryId.equals(target.id)
                && sourceIndex >= 0
                && sourceIndex < clampedIndex) {
            clampedIndex--;
        }
        target.presets.add(clampedIndex, preset);
        persistUserRules();
        return true;
    }

    public synchronized boolean moveUserPresetBefore(String presetId, String targetCategoryId, String beforePresetId) {
        GraphPresetRules.PresetCategory target = findUserCategory(targetCategoryId);
        if (target == null || target.presets == null) {
            return moveUserPreset(presetId, targetCategoryId, Integer.MAX_VALUE);
        }
        int index = target.presets.size();
        for (int i = 0; i < target.presets.size(); i++) {
            GraphPresetRules.GraphPresetDefinition candidate = target.presets.get(i);
            if (candidate != null && beforePresetId.equals(candidate.id)) {
                index = i;
                break;
            }
        }
        return moveUserPreset(presetId, targetCategoryId, index);
    }

    private GraphPresetRules.GraphPresetDefinition removeUserPresetFromCategory(String presetId) {
        for (GraphPresetRules.PresetCategory category : userRules.categories) {
            if (category == null || category.presets == null) {
                continue;
            }
            for (int i = 0; i < category.presets.size(); i++) {
                GraphPresetRules.GraphPresetDefinition preset = category.presets.get(i);
                if (preset != null && presetId.equals(preset.id)) {
                    category.presets.remove(i);
                    return preset;
                }
            }
        }
        return null;
    }

    private GraphPresetRules.GraphPresetDefinition findUserPreset(String presetId) {
        PresetView view = findPresetInRules(userRules, EntrySource.USER, presetId);
        return view != null ? view.preset() : null;
    }

    private PresetView findPresetInRules(GraphPresetRules rules, EntrySource source, String presetId) {
        if (rules == null || rules.categories == null) {
            return null;
        }
        for (GraphPresetRules.PresetCategory category : rules.categories) {
            if (category == null || category.presets == null) {
                continue;
            }
            for (int i = 0; i < category.presets.size(); i++) {
                GraphPresetRules.GraphPresetDefinition preset = category.presets.get(i);
                if (preset != null && presetId.equals(preset.id)) {
                    return new PresetView(preset, category.id, source, i);
                }
            }
        }
        return null;
    }

    private GraphPresetRules.PresetCategory findUserCategory(String categoryId) {
        if (userRules.categories == null || categoryId == null) {
            return null;
        }
        for (GraphPresetRules.PresetCategory category : userRules.categories) {
            if (category != null && categoryId.equals(category.id)) {
                return category;
            }
        }
        return null;
    }

    private void appendCategoryViews(GraphPresetRules rules, EntrySource source, List<CategoryView> views) {
        if (rules == null || rules.categories == null) {
            return;
        }
        for (GraphPresetRules.PresetCategory category : rules.categories) {
            if (category != null) {
                views.add(new CategoryView(category, source));
            }
        }
    }

    private void ensureDefaultUserCategory() {
        if (userRules.categories == null) {
            userRules.categories = new ArrayList<>();
        }
        if (findUserCategory(DEFAULT_USER_CATEGORY_ID) == null) {
            GraphPresetRules.PresetCategory category = new GraphPresetRules.PresetCategory();
            category.id = DEFAULT_USER_CATEGORY_ID;
            category.displayName = "我的预设";
            category.presets = new ArrayList<>();
            userRules.categories.add(0, category);
            persistUserRules();
        }
    }

    private void persistUserRules() {
        UserGraphPresetStore.save(userRules);
    }

    private static String generateUserPresetId(String displayName) {
        String slug = displayName == null ? "preset" : displayName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", "_")
                .replaceAll("_+", "_");
        if (slug.isBlank() || slug.equals("_")) {
            slug = "preset";
        }
        if (slug.length() > 32) {
            slug = slug.substring(0, 32);
        }
        return "user." + slug + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
