package com.nodecraft.gui.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlanChangeSummaryTest {

    @Test
    void buildReturnsNullWhenNoDiffAvailable() {
        assertNull(AiPlanChangeSummary.build(null, null));
    }

    @Test
    void mappedHeadlineCountsNodesUpdatesConnectionsAndKeep() {
        AiGraphDiffService.MappedDiffSummary mapped = new AiGraphDiffService.MappedDiffSummary(
                2,
                3,
                5,
                4,
                1,
                2,
                3,
                List.of("reuse-a"),
                List.of("new-a", "new-b"),
                List.of("upd-a"),
                List.of("conn-add"),
                List.of("conn-rm"),
                List.of("in-repl")
        );

        AiPlanChangeSummary.View view = AiPlanChangeSummary.build(mapped, null);
        assertNotNull(view);
        assertEquals("+ 3 nodes · ~ 4 updated · ↔ 6 connections · keep: 5", view.headline());
        assertTrue(view.detailLines().stream().anyMatch(line -> line.startsWith("New nodes:")));
        assertTrue(view.detailLines().stream().anyMatch(line -> line.startsWith("Updated:")));
        assertFalse(view.detailLines().stream().anyMatch(line -> line.toLowerCase().contains("heuristic")));
        assertFalse(view.detailLines().stream().anyMatch(line -> line.toLowerCase().contains("delete")));
    }

    @Test
    void detailsLabelCanvasOnlyNodesAsNotInThisPlan() {
        AiGraphDiffService.MappedDiffSummary mapped = new AiGraphDiffService.MappedDiffSummary(
                0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        AiGraphDiffService.GraphDiffSummary heuristic = new AiGraphDiffService.GraphDiffSummary(
                0,
                2,
                0,
                0,
                List.of(),
                List.of("canvas-node-a", "canvas-node-b"),
                List.of(),
                List.of()
        );

        AiPlanChangeSummary.View view = AiPlanChangeSummary.build(mapped, heuristic);
        assertNotNull(view);
        assertTrue(view.detailLines().stream().anyMatch(line ->
                line.contains("not in this plan (still on canvas)")));
        assertTrue(view.detailLines().stream().anyMatch(line ->
                line.contains("canvas-node-a")));
        assertFalse(view.detailLines().stream().anyMatch(line ->
                line.toLowerCase().contains("delet")));
    }

    @Test
    void heuristicFallbackUsesAdditionCountsWithoutDeleteClaims() {
        AiGraphDiffService.GraphDiffSummary heuristic = new AiGraphDiffService.GraphDiffSummary(
                2,
                1,
                3,
                1,
                List.of("add-1", "add-2"),
                List.of("missing-1"),
                List.of("c-add"),
                List.of("c-miss")
        );

        AiPlanChangeSummary.View view = AiPlanChangeSummary.build(null, heuristic);
        assertNotNull(view);
        assertEquals("+ 2 nodes · ~ 0 updated · ↔ 4 connections · keep: 0", view.headline());
        assertTrue(view.detailLines().stream().anyMatch(line ->
                line.contains("not in this plan (still on canvas)")));
        assertFalse(view.detailLines().stream().anyMatch(line ->
                line.toLowerCase().contains("heuristic")));
    }

    @Test
    void detailsCapSamplesAtSixPerGroup() {
        List<String> many = List.of("1", "2", "3", "4", "5", "6", "7", "8");
        AiGraphDiffService.MappedDiffSummary mapped = new AiGraphDiffService.MappedDiffSummary(
                0, 8, 0, 0, 0, 0, 0,
                List.of(), many, List.of(), List.of(), List.of(), List.of()
        );

        AiPlanChangeSummary.View view = AiPlanChangeSummary.build(mapped, null);
        assertNotNull(view);
        long newNodeLines = view.detailLines().stream()
                .filter(line -> line.startsWith("New nodes:"))
                .count();
        assertEquals(6, newNodeLines);
    }
}
