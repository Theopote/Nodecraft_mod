package com.nodecraft.gui.components.customui;

import com.nodecraft.gui.layout.ImGuiChildScope;
import com.nodecraft.nodesystem.nodes.input.type_selectors.RegistryCatalog;
import com.nodecraft.nodesystem.nodes.input.type_selectors.RegistryCatalogCache;
import com.nodecraft.nodesystem.nodes.input.type_selectors.RegistrySelectorUtils;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Compact block id picker popup (registry search list, same catalog as Block Type Selector).
 */
public final class BlockPickerPopup {

    private static final int PAGE_SIZE = 16;
    private static final String[] QUICK_BLOCKS = {
        "minecraft:stone",
        "minecraft:cobblestone",
        "minecraft:oak_planks",
        "minecraft:glass",
        "minecraft:dirt",
        "minecraft:grass_block"
    };

    private static volatile List<String> cachedIds = List.of();
    private static volatile boolean catalogReady;
    private static final Map<String, ImString> SEARCH_BUFFERS = new ConcurrentHashMap<>();

    private BlockPickerPopup() {
    }

    /**
     * @param popupKey unique ImGui popup id suffix (include row index)
     * @return true when the user picked a new block id
     */
    public static boolean renderButton(
            String popupKey,
            @Nullable String blockId,
            float buttonWidth,
            Consumer<String> onSelected
    ) {
        String label = blockId == null || blockId.isBlank() ? "Pick block..." : blockId;
        if (ImGui.button(label, buttonWidth, ImGui.getFrameHeight())) {
            ImGui.openPopup("##block_picker_popup_" + popupKey);
        }
        return renderPopup("##block_picker_popup_" + popupKey, onSelected);
    }

    private static boolean renderPopup(String popupId, Consumer<String> onSelected) {
        ImGui.setNextWindowSize(320.0f, 360.0f, imgui.flag.ImGuiCond.Appearing);
        boolean changed = false;
        if (!ImGui.beginPopupModal(popupId, ImGuiWindowFlags.NoResize)) {
            SEARCH_BUFFERS.remove(popupId);
            return false;
        }
        try {
            ensureCatalog();
            ImString search = SEARCH_BUFFERS.computeIfAbsent(popupId, ignored -> new ImString(128));
            ImGui.inputTextWithHint("##block_search_" + popupId, "Search block id...", search, ImGuiInputTextFlags.None);

            List<String> filtered = filterIds(search.get());
            ImGui.text("Results: " + filtered.size());

            if (renderQuickPicks(onSelected)) {
                changed = true;
            }

            float listHeight = Math.max(120.0f, ImGui.getContentRegionAvail().y - ImGui.getFrameHeightWithSpacing());
            try (ImGuiChildScope scope = new ImGuiChildScope(
                    "##block_picker_list", ImGui.getContentRegionAvail().x, listHeight,
                    false, ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
                if (scope.isOpen()) {
                    int shown = 0;
                    for (String id : filtered) {
                        if (shown >= PAGE_SIZE) {
                            ImGui.textDisabled("... refine search for more");
                            break;
                        }
                        if (ImGui.selectable(id)) {
                            String canonical = RegistrySelectorUtils.normalizeCanonicalId(id);
                            if (canonical != null) {
                                onSelected.accept(canonical);
                                changed = true;
                                ImGui.closeCurrentPopup();
                            }
                        }
                        shown++;
                    }
                    if (filtered.isEmpty()) {
                        ImGui.textDisabled(catalogReady ? "No results" : "Block registry unavailable");
                    }
                }
            }

            if (ImGui.button("Close", 80.0f, ImGui.getFrameHeight())) {
                ImGui.closeCurrentPopup();
            }
        } finally {
            ImGui.endPopup();
        }
        return changed;
    }

    private static boolean renderQuickPicks(Consumer<String> onSelected) {
        boolean changed = false;
        for (int i = 0; i < QUICK_BLOCKS.length; i++) {
            if (i > 0) {
                ImGui.sameLine();
            }
            ImGui.pushID(i);
            if (ImGui.smallButton(shortLabel(QUICK_BLOCKS[i]))) {
                onSelected.accept(QUICK_BLOCKS[i]);
                changed = true;
                ImGui.closeCurrentPopup();
            }
            ImGui.popID();
        }
        return changed;
    }

    private static String shortLabel(String blockId) {
        int colon = blockId.indexOf(':');
        return colon >= 0 ? blockId.substring(colon + 1) : blockId;
    }

    private static List<String> filterIds(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return cachedIds.size() > PAGE_SIZE ? cachedIds.subList(0, PAGE_SIZE) : cachedIds;
        }
        List<String> out = new ArrayList<>(PAGE_SIZE);
        for (String id : cachedIds) {
            if (id.toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(id);
                if (out.size() >= PAGE_SIZE * 3) {
                    break;
                }
            }
        }
        return out;
    }

    private static void ensureCatalog() {
        RegistryCatalog catalog = RegistryCatalogCache.getOrLoad(RegistryCatalogCache.Kind.BLOCK, BlockPickerPopup::loadCatalog);
        cachedIds = catalog.ids();
        catalogReady = catalog.authoritative() && !cachedIds.isEmpty();
    }

    private static RegistryCatalog loadCatalog() {
        List<String> ids = new ArrayList<>();
        try {
            for (Identifier id : Registries.BLOCK.getIds()) {
                ids.add(id.toString());
            }
        } catch (Throwable ignored) {
            return new RegistryCatalog(List.of(), false);
        }
        return new RegistryCatalog(ids, !ids.isEmpty());
    }
}
