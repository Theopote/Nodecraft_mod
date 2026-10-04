package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.input.type_selectors.BiomeSelectorNode;
import com.nodecraft.nodesystem.nodes.input.type_selectors.RegistryCatalogHelper;
import com.nodecraft.nodesystem.nodes.input.type_selectors.RegistrySelectorUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Type Selector Authoritative Registry Contract v2 (Graph V113).
 * Graph Valid = Identifier syntax + Allow Modded policy (not registry membership).
 */
class TypeSelectorsLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV113() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void isKnownBiomeIdWithoutLiveRegistryIsFalse() {
        assertFalse(RegistryCatalogHelper.isKnownBiomeId("minecraft:plains"));
    }

    @Test
    void biomeNonAuthoritativeCatalogPreservesIdWithValidTrue() {
        BiomeSelectorNode node = new BiomeSelectorNode();
        node.forceNonAuthoritativeCatalogForTest(List.of("minecraft:plains", "minecraft:forest"));
        node.setNodeState(Map.of(
                "selectedBiome", "minecraft:plains",
                "allowModded", true,
                "selectedCategory", "all",
                "minecraftOnly", false
        ));
        assertEquals("minecraft:plains", node.getOutput("output_biome_id"));
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertFalse(node.isRegistryAuthoritativeForTest());
    }

    @Test
    void computeValidIsSyntaxAndAllowModdedOnly() {
        assertTrue(RegistrySelectorUtils.computeValid("minecraft:stone", false));
        assertTrue(RegistrySelectorUtils.computeValid("minecraft:stone", true));
        assertTrue(RegistrySelectorUtils.computeValid("mod_a:custom", true));
        assertFalse(RegistrySelectorUtils.computeValid("mod_a:custom", false));
        assertFalse(RegistrySelectorUtils.computeValid(null, true));
        assertFalse(RegistrySelectorUtils.computeValid("not a valid id!!!", true));
        // Deprecated overloads ignore registry membership / authoritative flags.
        assertTrue(RegistrySelectorUtils.computeValid("minecraft:stone", true));
        assertTrue(RegistrySelectorUtils.computeValid("mod_a:x", true));
    }

    @Test
    void unknownWellFormedModIdValidWhenAllowModded() {
        BiomeSelectorNode node = new BiomeSelectorNode();
        node.forceNonAuthoritativeCatalogForTest(List.of("minecraft:plains"));
        node.setNodeState(Map.of(
                "selectedBiome", "mod_a:custom_biome",
                "allowModded", true,
                "selectedCategory", "all",
                "minecraftOnly", false
        ));
        assertEquals("mod_a:custom_biome", node.getOutput("output_biome_id"));
        assertTrue((Boolean) node.getOutput("output_valid"));
    }

    @Test
    void allowModdedFalseRejectsModdedIdWithoutRewriting() {
        assertFalse(RegistrySelectorUtils.computeValid("mod_a:marble", false));
        assertEquals("mod_a:marble", RegistrySelectorUtils.normalizeCanonicalId("mod_a:marble"));

        BiomeSelectorNode node = new BiomeSelectorNode();
        node.forceNonAuthoritativeCatalogForTest(List.of("mod_a:custom_biome", "minecraft:plains"));
        node.setNodeState(Map.of(
                "selectedBiome", "mod_a:custom_biome",
                "allowModded", false,
                "selectedCategory", "all",
                "minecraftOnly", true
        ));
        assertEquals("mod_a:custom_biome", node.getOutput("output_biome_id"));
        assertFalse((Boolean) node.getOutput("output_valid"));
    }
}
