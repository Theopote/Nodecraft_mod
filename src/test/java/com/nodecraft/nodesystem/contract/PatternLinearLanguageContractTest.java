package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pattern Linear v1 language fence (Graph V41).
 */
class PatternLinearLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> CANONICAL_LINEAR_IDS = Set.of(
            "pattern.linear.linear_array_geometry",
            "pattern.linear.path_instances",
            "pattern.linear.instance_on_points",
            "pattern.linear.curve_array_geometry"
    );

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV41() {
        assertEquals(41, GraphFormatVersion.V41);
        assertEquals(GraphFormatVersion.V41, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyFourCanonicalLinearNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("pattern.linear."))
                .sorted()
                .toList();
        assertEquals(4, ids.size(), "Expected 4 pattern.linear nodes: " + ids);
        assertEquals(CANONICAL_LINEAR_IDS, Set.copyOf(ids));
    }

    @Test
    void legacyBlockArrayNodesAreNotRegistered() {
        assertFalse(registry.getAllNodeIds().contains("pattern.linear.linear_array"));
        assertFalse(registry.getAllNodeIds().contains("pattern.linear.along_path"));
        assertFalse(registry.getAllNodeIds().contains("pattern.linear.staggered_array"));
    }

    @Test
    void linearArrayCountMeansTotalEmittedInstances() {
        BaseNode linear = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.linear_array_geometry"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 1.0d);
        linear.setInput("input_geometry", sphere);
        linear.setInput("input_direction", new Vector3d(1, 0, 0));
        linear.setInput("input_distance", 2.0d);
        linear.setInput("input_count", 4);
        linear.processNode(null);

        assertEquals(Boolean.TRUE, linear.getOutput("output_valid"));
        assertEquals(4, linear.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<Object> copies = assertInstanceOf(List.class, linear.getOutput("output_geometries"));
        assertEquals(4, copies.size());
        assertInstanceOf(CompositeGeometryData.class, linear.getOutput("output_geometry"));
    }

    @Test
    void linearArraySingleCopyReturnsRawGeometry() {
        BaseNode linear = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.linear_array_geometry"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 1.0d);
        linear.setInput("input_geometry", sphere);
        linear.setInput("input_direction", new Vector3d(1, 0, 0));
        linear.setInput("input_distance", 2.0d);
        linear.setInput("input_count", 1);
        linear.processNode(null);

        assertEquals(Boolean.TRUE, linear.getOutput("output_valid"));
        assertEquals(1, linear.getOutput("output_count"));
        assertInstanceOf(SphereData.class, linear.getOutput("output_geometry"));
    }

    @Test
    void staggeredGridCountMeansTotalEmittedPositions() {
        BaseNode grid = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.grid.staggered_grid"));
        grid.setInput("input_coordinates", new BlockPosList(List.of(new BlockPos(0, 64, 0))));
        grid.setInput("input_step_direction", new Vector3d(1, 0, 0));
        grid.setInput("input_row_direction", new Vector3d(0, 0, 1));
        grid.setInput("input_step_distance", 1.0d);
        grid.setInput("input_row_distance", 1.0d);
        grid.setInput("input_step_count", 5);
        grid.setInput("input_row_count", 3);
        grid.processNode(null);

        BlockPosList result = assertInstanceOf(BlockPosList.class, grid.getOutput("output_array_coordinates"));
        assertEquals(15, result.size());
    }

    @Test
    void closedPathFramesDoNotDuplicateSeam() {
        BaseNode pathFrames = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.path_instances"));
        PolylineData path = new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(10, 0, 0),
                new Vec3d(10, 0, 10),
                new Vec3d(0, 0, 0)
        ));
        pathFrames.setInput("input_path", path);
        pathFrames.setInput("input_up_vector", new Vector3d(0, 1, 0));
        pathFrames.processNode(null);

        assertEquals(Boolean.TRUE, pathFrames.getOutput("output_valid"));
        assertEquals(3, pathFrames.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, pathFrames.getOutput("output_points"));
        assertEquals(3, points.size());
        @SuppressWarnings("unchecked")
        List<FrameData> frames = assertInstanceOf(List.class, pathFrames.getOutput("output_frames"));
        assertEquals(3, frames.size());
    }

    @Test
    void closedCurveArrayDoesNotDuplicateSeam() {
        BaseNode curve = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.curve_array_geometry"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 0.5d);
        PolylineData path = new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(10, 0, 0),
                new Vec3d(10, 0, 10),
                new Vec3d(0, 0, 0)
        ));
        curve.setInput("input_geometry", sphere);
        curve.setInput("input_pivot", new PointData(0, 0, 0));
        curve.setInput("input_path", path);
        curve.setInput("input_count", 4);
        curve.setInput("input_up_vector", new Vector3d(0, 1, 0));
        curve.processNode(null);

        assertEquals(Boolean.TRUE, curve.getOutput("output_valid"));
        assertEquals(4, curve.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<FrameData> frames = assertInstanceOf(List.class, curve.getOutput("output_frames"));
        @SuppressWarnings("unchecked")
        List<Object> copies = assertInstanceOf(List.class, curve.getOutput("output_geometries"));
        @SuppressWarnings("unchecked")
        List<PointData> origins = assertInstanceOf(List.class, curve.getOutput("output_origins"));
        assertEquals(4, frames.size());
        assertEquals(4, copies.size());
        assertEquals(4, origins.size());
        FrameData first = frames.getFirst();
        FrameData last = frames.get(3);
        assertEquals(0.0d, first.getOrigin().x, 1.0e-6d);
        assertEquals(0.0d, first.getOrigin().z, 1.0e-6d);
        assertTrue(first.getOrigin().distanceSquared(last.getOrigin()) > 1.0e-6d,
                "Closed curve array must not duplicate seam at start/end");
    }

    @Test
    void curveArrayCountOneIsLegal() {
        BaseNode curve = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.curve_array_geometry"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 0.5d);
        PolylineData path = new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(10, 0, 0)
        ));
        curve.setInput("input_geometry", sphere);
        curve.setInput("input_pivot", new PointData(0, 0, 0));
        curve.setInput("input_path", path);
        curve.setInput("input_count", 1);
        curve.processNode(null);

        assertEquals(Boolean.TRUE, curve.getOutput("output_valid"));
        assertEquals(1, curve.getOutput("output_count"));
        assertInstanceOf(SphereData.class, curve.getOutput("output_geometry"));
    }

    @Test
    void instanceOnPointsUsesPointListAndNoHiddenStone() {
        assertPortType("pattern.linear.instance_on_points", "input_points", true, NodeDataType.POINT_LIST);
        assertFalse(hasPort("pattern.linear.instance_on_points", "input_template_coordinates", true));
        assertFalse(hasPort("pattern.linear.instance_on_points", "input_block_info", true));
        assertFalse(hasPort("pattern.linear.instance_on_points", "output_positions", false));
        assertFalse(hasPort("pattern.linear.instance_on_points", "output_block_ids", false));

        BaseNode node = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.instance_on_points"));
        node.setInput("input_points", List.of(new PointData(10, 64, 10)));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));

        node.setInput("input_template_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_fence")
        ));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(1, node.getOutput("output_instance_count"));
        assertEquals(1, node.getOutput("output_placement_count"));
    }

    @Test
    void v40ToV41DropsInstanceOnPointsDeconstructAndMaterialPorts() {
        SavedGraph v40 = new SavedGraph();
        v40.formatVersion = GraphFormatVersion.V40;

        SavedNode iop = savedNode("iop", "pattern.linear.instance_on_points");
        SavedNode preview = savedNode("preview", "output.preview.preview_blocks");

        v40.nodes = new ArrayList<>(List.of(iop, preview));
        v40.connections = new ArrayList<>(List.of(
                wire("iop", "output_placements", "preview", "input_block_placements"),
                wire("iop", "output_positions", "preview", "input_block_placements"),
                wire("iop", "output_block_ids", "preview", "input_block_placements")
        ));
        v40.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v40);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertTrue(hasWire(migrated, "iop", "output_placements", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "iop", "output_positions", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "iop", "output_block_ids", "preview", "input_block_placements"));
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

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String sourceId, String sourcePort, String targetId, String targetPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = sourceId;
        connection.sourcePortId = sourcePort;
        connection.targetNodeId = targetId;
        connection.targetPortId = targetPort;
        return connection;
    }

    private static boolean hasWire(SavedGraph graph, String sourceId, String sourcePort,
                                   String targetId, String targetPort) {
        if (graph.connections == null) {
            return false;
        }
        return graph.connections.stream().anyMatch(connection ->
                connection != null
                        && sourceId.equals(connection.sourceNodeId)
                        && sourcePort.equals(connection.sourcePortId)
                        && targetId.equals(connection.targetNodeId)
                        && targetPort.equals(connection.targetPortId));
    }
}
