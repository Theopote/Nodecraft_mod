package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.math.NumericListReduction;
import com.nodecraft.nodesystem.math.ScalarResult;
import com.nodecraft.nodesystem.nodes.math.list_sequence.AverageNumbersNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.ListStatisticsNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.MapListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.ProductNumbersNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.SortNumbersNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.SortTextNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.SumNumbersNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.DivisionNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.RoundNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Numeric List Reduction & Scalar Consistency v2 (Graph V128).
 */
class ListNumericLanguageV2ContractTest {

    private static final List<Double> OVERFLOW_SUM_INPUT = List.of(1e308, 1e308);
    private static final List<Double> OVERFLOW_PRODUCT_INPUT = List.of(1e200, 1e200);

    @Test
    void currentGraphFormatIsAtLeastV128() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void sumOverflowFailsClosed() {
        Map<String, Object> sum = new SumNumbersNode().compute(Map.of("input_list", OVERFLOW_SUM_INPUT));
        assertFalse((Boolean) sum.get("output_valid"));
        assertTrue(Double.isNaN((Double) sum.get("output_value")));

        Map<String, Object> stats = new ListStatisticsNode().compute(Map.of("input_list", OVERFLOW_SUM_INPUT));
        assertFalse((Boolean) stats.get("output_valid"));
        assertTrue(Double.isNaN((Double) stats.get("output_sum")));
    }

    @Test
    void averageLargeEqualValuesSucceeds() {
        Map<String, Object> avg = new AverageNumbersNode().compute(Map.of("input_list", OVERFLOW_SUM_INPUT));
        assertTrue((Boolean) avg.get("output_valid"));
        assertEquals(1e308, (Double) avg.get("output_value"));
    }

    @Test
    void productOverflowFailsClosed() {
        Map<String, Object> product = new ProductNumbersNode().compute(Map.of("input_list", OVERFLOW_PRODUCT_INPUT));
        assertFalse((Boolean) product.get("output_valid"));
        assertTrue(Double.isNaN((Double) product.get("output_value")));
    }

    @Test
    void statisticsMedianAvoidsMidpointOverflow() {
        ScalarResult median = NumericListReduction.medianSorted(List.of(1e308, 1e308));
        assertTrue(median.valid());
        assertEquals(1e308, median.value());
    }

    @Test
    void mapDivideMatchesScalarDivision() {
        MapListNode map = new MapListNode();
        map.setOperation(MapListNode.Operation.DIVIDE);
        Map<String, Object> mapped = map.compute(Map.of(
                "input_list", List.of(1.0),
                "input_value", 1e-13
        ));

        DivisionNode division = new DivisionNode();
        Map<String, Object> scalar = division.compute(Map.of(
                "input_a", 1.0,
                "input_b", 1e-13
        ));

        assertTrue((Boolean) mapped.get("output_valid"));
        assertTrue((Boolean) scalar.get("output_valid"));
        @SuppressWarnings("unchecked")
        List<Double> resultList = (List<Double>) mapped.get("output_list");
        assertEquals((Double) scalar.get("output_quotient"), resultList.getFirst());
    }

    @Test
    void mapRoundMatchesScalarRound() {
        MapListNode map = new MapListNode();
        map.setOperation(MapListNode.Operation.ROUND);
        Map<String, Object> mapped = map.compute(Map.of("input_list", List.of(2.5)));

        RoundNode round = new RoundNode();
        Map<String, Object> scalar = round.compute(Map.of("input_value", 2.5));

        assertTrue((Boolean) mapped.get("output_valid"));
        assertTrue((Boolean) scalar.get("output_valid"));
        @SuppressWarnings("unchecked")
        List<Double> resultList = (List<Double>) mapped.get("output_list");
        assertEquals((Double) scalar.get("output_rounded"), resultList.getFirst());
        assertEquals(2.0, resultList.getFirst());
    }

    @Test
    void nanElementFailsAllReductionNodes() {
        List<Double> input = List.of(1.0, Double.NaN, 2.0);
        assertFalse((Boolean) new SumNumbersNode().compute(Map.of("input_list", input)).get("output_valid"));
        assertFalse((Boolean) new ProductNumbersNode().compute(Map.of("input_list", input)).get("output_valid"));
        assertFalse((Boolean) new AverageNumbersNode().compute(Map.of("input_list", input)).get("output_valid"));
        assertFalse((Boolean) new ListStatisticsNode().compute(Map.of("input_list", input)).get("output_valid"));
    }

    @Test
    void emptyListFailsReductionNodes() {
        assertFalse((Boolean) new SumNumbersNode().compute(Map.of("input_list", List.of())).get("output_valid"));
        assertFalse((Boolean) new ProductNumbersNode().compute(Map.of("input_list", List.of())).get("output_valid"));
        assertFalse((Boolean) new AverageNumbersNode().compute(Map.of("input_list", List.of())).get("output_valid"));
        assertFalse((Boolean) new ListStatisticsNode().compute(Map.of("input_list", List.of())).get("output_valid"));
    }

    @Test
    void sortNumbersAscendingAndDescendingDoNotMutateInput() {
        List<Double> input = new ArrayList<>(List.of(3.0, 1.0, 2.0));

        SortNumbersNode ascending = new SortNumbersNode();
        ascending.setDescending(false);
        Map<String, Object> asc = ascending.compute(Map.of("input_list", input));
        assertTrue((Boolean) asc.get("output_valid"));
        assertEquals(List.of(1.0, 2.0, 3.0), asc.get("output_list"));
        assertEquals(List.of(3.0, 1.0, 2.0), input);

        SortNumbersNode descending = new SortNumbersNode();
        descending.setDescending(true);
        Map<String, Object> desc = descending.compute(Map.of("input_list", new ArrayList<>(List.of(3.0, 1.0, 2.0))));
        assertTrue((Boolean) desc.get("output_valid"));
        assertEquals(List.of(3.0, 2.0, 1.0), desc.get("output_list"));
    }

    @Test
    void sortTextDoesNotMutateInput() {
        List<String> input = new ArrayList<>(List.of("c", "a", "b"));
        SortTextNode node = new SortTextNode();
        Map<String, Object> outputs = node.compute(Map.of("input_list", input));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(List.of("a", "b", "c"), outputs.get("output_list"));
        assertEquals(List.of("c", "a", "b"), input);
    }

    @Test
    void sumMatchesStatisticsSum() {
        List<Double> input = List.of(1.0, 2.0, 3.0, 4.0);
        Map<String, Object> sum = new SumNumbersNode().compute(Map.of("input_list", input));
        Map<String, Object> stats = new ListStatisticsNode().compute(Map.of("input_list", input));
        assertTrue((Boolean) sum.get("output_valid"));
        assertTrue((Boolean) stats.get("output_valid"));
        assertEquals(sum.get("output_value"), stats.get("output_sum"));
    }
}
