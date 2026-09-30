package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pattern Grid v1 language fence (Graph V42), updated for V80 fail-closed semantics.
 */
class PatternGridLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> CANONICAL_GRID_IDS = Set.of(
            "pattern.grid.grid_array",
            "pattern.grid.facade_grid",
            "pattern.grid.staggered_grid",
            "pattern.grid.hex_grid",
            "pattern.grid.triangular_grid"
    );

    /** Generic lattice layout producers sharing {@code output_points}. */
    private static final List<String> GENERIC_LAYOUT_PRODUCER_IDS = List.of(
            "pattern.grid.staggered_grid",
            "pattern.grid.hex_grid",
            "pattern.grid.triangular_grid"
    );

    private static final String FACADE_GRID_ID = "pattern.grid.facade_grid";

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void patternGridFreezeVersionIsV42() {
    }

    @Test
    void exactlyFiveCanonicalGridNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("pattern.grid."))
                .sorted()
                .toList();
        assertEquals(5, ids.size(), "Expected 5 pattern.grid nodes: " + ids);
        assertEquals(CANONICAL_GRID_IDS, Set.copyOf(ids));
    }

    @Test
    void removedLegacyTypeIdsAreNotRegistered() {
        List<String> ids = registry.getAllNodeIds();
        assertFalse(ids.contains("pattern.grid.grid_array_geometry"));
        assertFalse(ids.contains("pattern.grid.triangle_grid"));
    }

    @Test
    void gridArrayCountMeansTotalEmittedInstances() {
        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.grid_array"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 1.0d);
        grid.setInput("input_geometry", sphere);
        grid.setNodeState(Map.of(
                "xDistance", 2.0d, "xCount", 3,
                "yDistance", 2.0d, "yCount", 5,
                "zDistance", 1.0d, "zCount", 1
        ));
        grid.processNode(null);

        assertEquals(Boolean.TRUE, grid.getOutput("output_valid"));
        assertEquals(15, grid.getOutput("output_count"));
        assertInstanceOf(CompositeGeometryData.class, grid.getOutput("output_geometry"));
    }

    @Test
    void gridArrayZeroCountProducesEmpty() {
        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.grid_array"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 1.0d);
        grid.setInput("input_geometry", sphere);
        grid.setNodeState(Map.of("xCount", 0, "yCount", 3, "zCount", 1));
        grid.processNode(null);

        assertEquals(Boolean.FALSE, grid.getOutput("output_valid"));
        assertEquals(0, grid.getOutput("output_count"));
        assertEquals(null, grid.getOutput("output_geometry"));
    }

    @Test
    void gridArraySingleCopyReturnsRawGeometry() {
        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.grid_array"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 1.0d);
        grid.setInput("input_geometry", sphere);
        grid.setNodeState(Map.of("xCount", 1, "yCount", 1, "zCount", 1));
        grid.processNode(null);

        assertEquals(Boolean.TRUE, grid.getOutput("output_valid"));
        assertEquals(1, grid.getOutput("output_count"));
        assertInstanceOf(SphereData.class, grid.getOutput("output_geometry"));
    }

    @Test
    void facadeGridUsesTypedSpatialOutputs() {
        assertPortType("pattern.grid.facade_grid", "output_center_points", false, NodeDataType.POINT_LIST);
        assertPortType("pattern.grid.facade_grid", "output_cell_boundaries", false, NodeDataType.PATH_LIST);
        assertFalse(hasPort("pattern.grid.facade_grid", "output_center_blocks", false));
    }

    @Test
    void facadeGridInvalidRowsOrColumnsFailClosed() {
        BaseNode facade = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.facade_grid"));
        BoxFaceData face = requireFace(new BoxGeometryData(
                new Vector3d(10.0d, 5.0d, 8.0d),
                new Vector3d(10.0d, 5.0d, 8.0d)
        ), "Front");
        facade.setInput("input_face", face);
        facade.setNodeState(Map.of("columns", 0, "rows", 3));
        facade.processNode(null);

        assertEquals(Boolean.FALSE, facade.getOutput("output_valid"));
        assertEquals(0, facade.getOutput("output_cell_count"));
    }

    @Test
    void facadeGridInvalidMarginFailsClosed() {
        BaseNode facade = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.facade_grid"));
        BoxFaceData face = requireFace(new BoxGeometryData(
                new Vector3d(10.0d, 5.0d, 8.0d),
                new Vector3d(10.0d, 5.0d, 8.0d)
        ), "Front");
        facade.setInput("input_face", face);
        facade.setNodeState(Map.of("columns", 3, "rows", 3, "marginX", Double.NaN));
        facade.processNode(null);

        assertEquals(Boolean.FALSE, facade.getOutput("output_valid"));
    }

    @Test
    void facadeGridGeneratesCenterPointsAndBoundaries() {
        BaseNode facade = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.facade_grid"));
        BoxFaceData face = requireFace(new BoxGeometryData(
                new Vector3d(10.0d, 5.0d, 8.0d),
                new Vector3d(10.0d, 5.0d, 8.0d)
        ), "Front");
        facade.setInput("input_face", face);
        facade.setNodeState(Map.of("columns", 3, "rows", 2));
        facade.processNode(null);

        assertEquals(Boolean.TRUE, facade.getOutput("output_valid"));
        assertEquals(6, facade.getOutput("output_cell_count"));
        @SuppressWarnings("unchecked")
        List<PointData> centers = assertInstanceOf(List.class, facade.getOutput("output_center_points"));
        @SuppressWarnings("unchecked")
        List<PolylineData> boundaries = assertInstanceOf(List.class, facade.getOutput("output_cell_boundaries"));
        assertEquals(6, centers.size());
        assertEquals(6, boundaries.size());
    }

    @Test
    void staggeredGridCountMeansTotalEmittedPoints() {
        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.staggered_grid"));
        grid.setNodeState(Map.of(
                "stepDistance", 1.0d, "rowDistance", 1.0d,
                "stepCount", 5, "rowCount", 3
        ));
        grid.processNode(null);

        assertEquals(Boolean.TRUE, grid.getOutput("output_valid"));
        assertEquals(15, grid.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, grid.getOutput("output_points"));
        assertEquals(15, points.size());
    }

    @Test
    void staggeredGridZeroCountProducesEmpty() {
        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.staggered_grid"));
        grid.setNodeState(Map.of("stepCount", 0, "rowCount", 3));
        grid.processNode(null);

        assertEquals(Boolean.FALSE, grid.getOutput("output_valid"));
        assertEquals(0, grid.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, grid.getOutput("output_points"));
        assertTrue(points.isEmpty());
    }

    @Test
    void staggeredGridOverBudgetFailsClosed() {
        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.staggered_grid"));
        grid.setNodeState(Map.of("stepCount", 2048, "rowCount", 2048));
        grid.processNode(null);

        assertEquals(Boolean.FALSE, grid.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, grid.getOutput("output_points"));
        assertTrue(points.isEmpty());
        assertTrue(points.size() < GenerationLimits.MAX_LAYOUT_INSTANCES
                || points.isEmpty());
    }

    @Test
    void hexGridCountMeansTotalEmittedPoints() {
        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.hex_grid"));
        grid.setNodeState(Map.of("radius", 1.0d, "qCount", 4, "rCount", 4));
        grid.processNode(null);

        assertEquals(Boolean.TRUE, grid.getOutput("output_valid"));
        assertEquals(16, grid.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, grid.getOutput("output_points"));
        assertEquals(16, points.size());
    }

    @Test
    void hexGridOverBudgetFailsClosed() {
        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.hex_grid"));
        grid.setNodeState(Map.of("qCount", 1024, "rCount", 1024));
        grid.processNode(null);

        assertEquals(Boolean.FALSE, grid.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, grid.getOutput("output_points"));
        assertTrue(points.isEmpty());
    }

    @Test
    void triangularGridCountMeansTotalEmittedPoints() {
        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.triangular_grid"));
        grid.setNodeState(Map.of("sideLength", 2.0d, "uCount", 8, "vCount", 8));
        grid.processNode(null);

        assertEquals(Boolean.TRUE, grid.getOutput("output_valid"));
        assertEquals(64, grid.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, grid.getOutput("output_points"));
        assertEquals(64, points.size());
    }

    @Test
    void triangularGridFlipIsBooleanListForInstancing() {
        assertPortType("pattern.grid.triangular_grid", "output_flip", false, NodeDataType.BOOLEAN_LIST);
        assertFalse(hasPort("pattern.grid.triangular_grid", "output_triangle_up", false));

        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.triangular_grid"));
        grid.setNodeState(Map.of("uCount", 2, "vCount", 2));
        grid.processNode(null);

        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, grid.getOutput("output_points"));
        @SuppressWarnings("unchecked")
        List<Boolean> flip = assertInstanceOf(List.class, grid.getOutput("output_flip"));
        assertEquals(4, points.size());
        assertEquals(4, flip.size());
    }

    @Test
    void facadeGridIsSpecializedLayoutProducer() {
        assertPortType(FACADE_GRID_ID, "output_center_points", false, NodeDataType.POINT_LIST);
        assertPortType(FACADE_GRID_ID, "output_cell_boundaries", false, NodeDataType.PATH_LIST);
        assertFalse(hasPort(FACADE_GRID_ID, "output_points", false));
    }

    @Test
    void genericLayoutProducersDoNotOutputBlockListAnchors() {
        for (String typeId : GENERIC_LAYOUT_PRODUCER_IDS) {
            INode node = registry.createNodeInstance(typeId);
            for (IPort port : node.getOutputPorts()) {
                assertFalse(port.getDataType() == NodeDataType.BLOCK_LIST,
                        typeId + "#" + port.getId() + " must not be BLOCK_LIST");
            }
        }
    }

    @Test
    void genericLayoutProducersUseOutputPoints() {
        for (String typeId : GENERIC_LAYOUT_PRODUCER_IDS) {
            assertPortType(typeId, "output_points", false, NodeDataType.POINT_LIST);
        }
    }

    private static BoxFaceData requireFace(BoxGeometryData box, String name) {
        return box.getFaces().stream()
                .filter(face -> name.equalsIgnoreCase(face.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing face " + name));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .filter(candidate -> candidate.getId().equals(portId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasPort(String typeId, String portId, boolean input) {
        INode node = registry.createNodeInstance(typeId);
        return (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .anyMatch(port -> port.getId().equals(portId));
    }
}
