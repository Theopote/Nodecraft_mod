package com.nodecraft.gui.ai;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMockPlanServiceTest {

    @Test
    void createTowerIsNotStolenByPlacement() {
        assertEquals(AiMockPlanService.MockTemplateKind.TOWER,
                AiMockPlanService.selectPrimaryTemplateKind("Create a tower"));
        assertEquals(AiMockPlanService.MockTemplateKind.SPHERE,
                AiMockPlanService.selectPrimaryTemplateKind("Create a sphere"));
        assertEquals(AiMockPlanService.MockTemplateKind.HELIX_PATH,
                AiMockPlanService.selectPrimaryTemplateKind("Add a helix"));
        assertEquals(AiMockPlanService.MockTemplateKind.ARCH_PATH,
                AiMockPlanService.selectPrimaryTemplateKind("Create an arch"));
    }

    @Test
    void explicitNodePlacementSelectsPlacement() {
        assertEquals(AiMockPlanService.MockTemplateKind.PLACEMENT,
                AiMockPlanService.selectPrimaryTemplateKind("place a node"));
        assertEquals(AiMockPlanService.MockTemplateKind.PLACEMENT,
                AiMockPlanService.selectPrimaryTemplateKind("添加一个节点"));
        assertEquals(AiMockPlanService.MockTemplateKind.PLACEMENT,
                AiMockPlanService.selectPrimaryTemplateKind("block selector on canvas"));
    }

    @Test
    void unknownAndMobiusAbstain() {
        AiMockPlanService.MockPlan unknown = AiMockPlanService.buildMockPlan("帮我做一个入口空间");
        assertTrue(unknown.abstained());
        assertEquals(AiMockPlanService.ABSTAIN_CODE, unknown.abstainCode());
        assertTrue(unknown.nodes().isEmpty());

        AiMockPlanService.MockPlan mobius = AiMockPlanService.buildMockPlan("build a möbius ring");
        assertTrue(mobius.abstained());
        assertTrue(mobius.summary().toLowerCase().contains("möbius")
                || mobius.summary().toLowerCase().contains("mobius"));
        assertNull(AiMockPlanService.selectPrimaryTemplateKind("莫比乌斯"));
    }

    @Test
    void previewFirstOmitsApplyUnlessWorldIntent() {
        AiMockPlanService.MockPlan preview = AiMockPlanService.buildMockPlan("sphere");
        assertFalse(preview.abstained(), preview.summary());
        Set<String> previewTypes = typeIds(preview);
        assertTrue(previewTypes.contains("geometry.primitives.sphere"));
        assertTrue(previewTypes.contains("material.basic_assignment.assign_block_type"));
        assertTrue(previewTypes.contains("output.preview.preview_blocks"));
        assertFalse(previewTypes.contains("output.execute.apply_changes"));
        assertFalse(previewTypes.contains("output.preview.geometry_viewer"));

        AiMockPlanService.MockPlan apply = AiMockPlanService.buildMockPlan("sphere apply to world");
        assertFalse(apply.abstained(), apply.summary());
        assertTrue(typeIds(apply).contains("output.execute.apply_changes"));
    }

    @Test
    void archAndHelixUseSweepNotCurveArray() {
        AiMockPlanService.MockPlan arch = AiMockPlanService.buildMockPlan("arch");
        assertFalse(arch.abstained(), arch.summary());
        Set<String> archTypes = typeIds(arch);
        assertTrue(archTypes.contains("geometry.curves.arc"));
        assertTrue(archTypes.contains("geometry.solids.sweep"));
        assertTrue(archTypes.contains("output.execute.bake_surface_strip_to_blocks"));
        assertFalse(archTypes.contains("pattern.linear.curve_array"));

        AiMockPlanService.MockPlan helix = AiMockPlanService.buildMockPlan("helix");
        assertFalse(helix.abstained(), helix.summary());
        Set<String> helixTypes = typeIds(helix);
        assertTrue(helixTypes.contains("geometry.curves.helix"));
        assertTrue(helixTypes.contains("geometry.solids.sweep"));
        assertFalse(helixTypes.contains("pattern.linear.curve_array"));
    }

    @Test
    void ringIsAnnularFloorNotTorus() {
        AiMockPlanService.MockPlan ring = AiMockPlanService.buildMockPlan("ring walkway");
        assertFalse(ring.abstained(), ring.summary());
        Set<String> types = typeIds(ring);
        assertTrue(types.contains("geometry.profiles.boolean_2d"));
        assertTrue(types.contains("geometry.solids.extrude_region"));
        assertFalse(types.contains("geometry.primitives.torus"));
    }

    @Test
    void towerIsMassingNotCylinder() {
        AiMockPlanService.MockPlan tower = AiMockPlanService.buildMockPlan("tower");
        assertFalse(tower.abstained(), tower.summary());
        Set<String> types = typeIds(tower);
        assertTrue(types.contains("geometry.combine.geometry"));
        assertTrue(types.contains("geometry.primitives.box"));
        assertFalse(types.contains("geometry.primitives.cylinder"));
    }

    @Test
    void placementTemplateIsSelectedBlockOnly() {
        AiMockPlanService.MockPlan plan = AiMockPlanService.buildMockPlan("place a node on canvas");
        assertFalse(plan.abstained(), plan.summary());
        assertEquals(1, plan.nodes().size());
        assertEquals("world.selection.selected_block", plan.nodes().getFirst().typeId());
    }

    private static Set<String> typeIds(AiMockPlanService.MockPlan plan) {
        assertNotNull(plan);
        return plan.nodes().stream().map(AiMockPlanService.MockNode::typeId).collect(Collectors.toSet());
    }
}
