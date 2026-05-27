package com.nodecraft.nodesystem.nodes.input.type_selectors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class RegistryCatalogHelper {

    private static final String[] FALLBACK_BIOME_IDS = {
        "minecraft:plains",
        "minecraft:sunflower_plains",
        "minecraft:forest",
        "minecraft:flower_forest",
        "minecraft:birch_forest",
        "minecraft:dark_forest",
        "minecraft:taiga",
        "minecraft:snowy_taiga",
        "minecraft:desert",
        "minecraft:jungle",
        "minecraft:sparse_jungle",
        "minecraft:swamp",
        "minecraft:mangrove_swamp",
        "minecraft:badlands",
        "minecraft:eroded_badlands",
        "minecraft:cherry_grove",
        "minecraft:meadow",
        "minecraft:grove",
        "minecraft:snowy_slopes",
        "minecraft:jagged_peaks",
        "minecraft:frozen_peaks",
        "minecraft:stony_peaks",
        "minecraft:river",
        "minecraft:frozen_river",
        "minecraft:beach",
        "minecraft:snowy_beach",
        "minecraft:stony_shore",
        "minecraft:ocean",
        "minecraft:deep_ocean",
        "minecraft:warm_ocean",
        "minecraft:lukewarm_ocean",
        "minecraft:cold_ocean",
        "minecraft:frozen_ocean",
        "minecraft:deep_lukewarm_ocean",
        "minecraft:deep_cold_ocean",
        "minecraft:deep_frozen_ocean",
        "minecraft:the_void",
        "minecraft:nether_wastes",
        "minecraft:crimson_forest",
        "minecraft:warped_forest",
        "minecraft:soul_sand_valley",
        "minecraft:basalt_deltas",
        "minecraft:the_end",
        "minecraft:small_end_islands",
        "minecraft:end_midlands",
        "minecraft:end_highlands",
        "minecraft:end_barrens"
    };

    private RegistryCatalogHelper() {}

    static void collectBiomeIds(List<String> target) {
        boolean collected = false;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            RegistryWrapper.WrapperLookup lookup = null;
            if (client.world != null) {
                lookup = client.world.getRegistryManager();
            } else if (client.getNetworkHandler() != null) {
                lookup = client.getNetworkHandler().getRegistryManager();
            }
            if (lookup != null) {
                RegistryWrapper.Impl<Biome> biomes = lookup.getOrThrow(RegistryKeys.BIOME);
                biomes.streamKeys()
                    .map(key -> key.getValue().toString())
                    .forEach(target::add);
                collected = !target.isEmpty();
            }
        } catch (Exception ignored) {
        }

        if (!collected) {
            for (String id : FALLBACK_BIOME_IDS) {
                target.add(id);
            }
        }
        target.sort(Comparator.naturalOrder());
    }

    static boolean isKnownBiomeId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return false;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            RegistryWrapper.WrapperLookup lookup = null;
            if (client.world != null) {
                lookup = client.world.getRegistryManager();
            } else if (client.getNetworkHandler() != null) {
                lookup = client.getNetworkHandler().getRegistryManager();
            }
            if (lookup != null) {
                RegistryWrapper.Impl<Biome> biomes = lookup.getOrThrow(RegistryKeys.BIOME);
                return biomes.getOptional(RegistryKey.of(RegistryKeys.BIOME, parsed)).isPresent();
            }
        } catch (Exception ignored) {
        }
        for (String fallback : FALLBACK_BIOME_IDS) {
            if (fallback.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
