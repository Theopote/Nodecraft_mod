package com.nodecraft.nodesystem.contract;

import com.nodecraft.core.exception.NodeValidationException;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes Curve/Path Node Language v1 semantics for join, trim, resample, explode, and extend.
 */
class PathLanguageContractTest {

    private static final Set<String> PATH_LANGUAGE_NODE_IDS = Set.of(
            "geometry.curves.join_paths",
            "geometry.curves.split_path",
            "geometry.curves.trim_path",
            "geometry.curves.reverse_path",
            "geometry.curves.closest_point_on_path",
            "geometry.curves.resample_path",
            "geometry.curves.explode_path",
            "geometry.curves.extend_path"
    );

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void pathLanguageNodesAvoidAnyPorts() {
        List<String> errors = new ArrayList<>();
        for (String typeId : PATH_LANGUAGE_NODE_IDS) {
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            if (node == null) {
                errors.add("missing instance: " + typeId);
                continue;
            }
            for (IPort port : node.getInputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(typeId + "." + port.getId() + " input is ANY");
                }
            }
            for (IPort port : node.getOutputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(typeId + "." + port.getId() + " output is ANY");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void joinPathsStrictConnectsMatchingEndpoints() {
        List<Vector3d> pathA = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(10, 0, 0)
        );
        List<Vector3d> pathB = List.of(
            new Vector3d(10, 0, 0),
            new Vector3d(10, 0, 10)
        );

        List<Vector3d> joined = PathUtils.joinPathsStrict(pathA, pathB, 1.0e-6d);
        assertNotNull(joined);
        assertEquals(3, joined.size());
        assertEquals(0.0d, joined.getFirst().x, 1.0e-6d);
        assertEquals(10.0d, joined.get(1).x, 1.0e-6d);
        assertEquals(10.0d, joined.getLast().z, 1.0e-6d);
    }

    @Test
    void joinPathsStrictDedupesSeamWithinTolerance() {
        List<Vector3d> pathA = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(2, 0, 0)
        );
        List<Vector3d> pathB = List.of(
            new Vector3d(2, 0, 0),
            new Vector3d(3, 0, 0),
            new Vector3d(4, 0, 0)
        );

        List<Vector3d> joined = PathUtils.joinPathsStrict(pathA, pathB, 1.0e-6d);
        assertNotNull(joined);
        assertEquals(5, joined.size());
    }

    @Test
    void joinPathsStrictRejectsDisconnectedPaths() {
        List<Vector3d> pathA = List.of(new Vector3d(0, 0, 0), new Vector3d(10, 0, 0));
        List<Vector3d> pathB = List.of(new Vector3d(100, 0, 0), new Vector3d(110, 0, 0));

        assertEquals(null, PathUtils.joinPathsStrict(pathA, pathB, 1.0e-6d));

        BaseNode join = node("geometry.curves.join_paths");
        join.setInput("input_path_a", new LineData(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0)));
        join.setInput("input_path_b", new LineData(new Vec3d(100, 0, 0), new Vec3d(110, 0, 0)));
        join.processNode(null);
        assertEquals(Boolean.FALSE, join.getOutput("output_valid"));
        assertEquals(0, join.getOutput("output_count"));
    }

    @Test
    void joinPathsStrictDoesNotAutoReverse() {
        List<Vector3d> pathA = List.of(new Vector3d(0, 0, 0), new Vector3d(10, 0, 0));
        List<Vector3d> pathB = List.of(new Vector3d(20, 0, 0), new Vector3d(10, 0, 0));

        assertEquals(null, PathUtils.joinPathsStrict(pathA, pathB, 1.0e-6d));
    }

    @Test
    void trimOpenPathPreservesDirection() {
        List<Vector3d> open = List.of(new Vector3d(0, 0, 0), new Vector3d(10, 0, 0));

        assertNotNull(PathUtils.trimPathByParameter(open, 0.2d, 0.8d));

        BaseNode trim = node("geometry.curves.trim_path");
        trim.setInput("input_path", new LineData(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0)));
        trim.setInput("input_start", 0.2d);
        trim.setInput("input_end", 0.8d);
        trim.processNode(null);
        assertEquals(Boolean.TRUE, trim.getOutput("output_valid"));

        trim.setInput("input_start", 0.8d);
        trim.setInput("input_end", 0.2d);
        trim.processNode(null);
        assertEquals(Boolean.FALSE, trim.getOutput("output_valid"));
    }

    @Test
    void trimClosedPathSupportsSeamWrap() {
        PolylineData closed = new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0),
            new Vec3d(10, 0, 10),
            new Vec3d(0, 0, 0)
        ));

        BaseNode trim = node("geometry.curves.trim_path");
        trim.setInput("input_path", closed);
        trim.setInput("input_start", 0.2d);
        trim.setInput("input_end", 0.8d);
        trim.processNode(null);
        assertEquals(Boolean.TRUE, trim.getOutput("output_valid"));

        trim.setInput("input_start", 0.8d);
        trim.setInput("input_end", 0.2d);
        trim.processNode(null);
        assertEquals(Boolean.TRUE, trim.getOutput("output_valid"));
    }

    @Test
    void trimRejectsEqualParameters() {
        List<Vector3d> open = List.of(new Vector3d(0, 0, 0), new Vector3d(10, 0, 0));
        assertEquals(null, PathUtils.trimPathByParameter(open, 0.5d, 0.5d));

        BaseNode trim = node("geometry.curves.trim_path");
        trim.setInput("input_path", new LineData(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0)));
        trim.setInput("input_start", 0.5d);
        trim.setInput("input_end", 0.5d);
        trim.processNode(null);
        assertEquals(Boolean.FALSE, trim.getOutput("output_valid"));
    }

    @Test
    void pathParameterAtPointMigratesToClosestPointOnPath() {
        SavedGraph v16 = new SavedGraph();
        v16.formatVersion = GraphFormatVersion.V16;
        v16.graphName = "path-parameter-migration";

        SavedNode legacy = new SavedNode();
        legacy.nodeId = "legacy-param";
        legacy.typeId = "geometry.curves.path_parameter_at_point";

        SavedNode sink = new SavedNode();
        sink.nodeId = "sink";
        sink.typeId = "input.numeric.number";

        v16.nodes = List.of(legacy, sink);
        v16.nodePositions = java.util.Map.of();

        SavedConnection parameterWire = new SavedConnection();
        parameterWire.sourceNodeId = "legacy-param";
        parameterWire.sourcePortId = "output_parameter";
        parameterWire.targetNodeId = "sink";
        parameterWire.targetPortId = "input_value";

        SavedConnection distanceWire = new SavedConnection();
        distanceWire.sourceNodeId = "legacy-param";
        distanceWire.sourcePortId = "output_distance";
        distanceWire.targetNodeId = "sink";
        distanceWire.targetPortId = "input_value";

        SavedConnection validWire = new SavedConnection();
        validWire.sourceNodeId = "legacy-param";
        validWire.sourcePortId = "output_valid";
        validWire.targetNodeId = "sink";
        validWire.targetPortId = "input_value";

        v16.connections = new ArrayList<>(List.of(parameterWire, distanceWire, validWire));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v16);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("geometry.curves.closest_point_on_path", migrated.nodes.getFirst().typeId);
        assertEquals(3, migrated.connections.size());
        assertEquals("output_parameter", migrated.connections.get(0).sourcePortId);
        assertEquals("output_distance", migrated.connections.get(1).sourcePortId);
        assertEquals("output_valid", migrated.connections.get(2).sourcePortId);
    }

    @Test
    void pathParameterAtPointNodeIsRetired() {
        assertThrows(NodeValidationException.class,
            () -> NodeRegistry.getInstance().createNodeInstance("geometry.curves.path_parameter_at_point"));
    }

    @Test
    void resamplePathRejectsOriginalMode() {
        LineData line = new LineData(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0));

        BaseNode resample = node("geometry.curves.resample_path");
        resample.setInput("input_path", line);
        resample.setInput("input_mode", "COUNT");
        resample.setInput("input_count", 5);
        resample.processNode(null);
        assertEquals(Boolean.TRUE, resample.getOutput("output_valid"));

        resample.setInput("input_mode", "SPACING");
        resample.setInput("input_spacing", 2.0d);
        resample.processNode(null);
        assertEquals(Boolean.TRUE, resample.getOutput("output_valid"));

        resample.setInput("input_mode", "ORIGINAL");
        resample.processNode(null);
        assertEquals(Boolean.FALSE, resample.getOutput("output_valid"));

        resample.setInput("input_mode", "NOT_A_MODE");
        resample.processNode(null);
        assertEquals(Boolean.FALSE, resample.getOutput("output_valid"));
    }

    @Test
    void explodePathOutputsPathList() {
        PolylineData open = new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0),
            new Vec3d(10, 0, 10),
            new Vec3d(0, 0, 10)
        ));

        BaseNode explode = node("geometry.curves.explode_path");
        explode.setInput("input_path", open);
        explode.processNode(null);
        assertEquals(Boolean.TRUE, explode.getOutput("output_valid"));
        assertEquals(3, explode.getOutput("output_count"));

        List<?> segments = (List<?>) explode.getOutput("output_segments");
        assertEquals(3, segments.size());
        for (Object segment : segments) {
            assertInstanceOf(PathData.class, segment);
        }

        assertPortType("geometry.curves.explode_path", "output_segments", false, NodeDataType.PATH_LIST);
    }

    @Test
    void explodeClosedPathProducesThreeSegmentsWithoutDegenerateClosure() {
        PolylineData closed = new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0),
            new Vec3d(10, 0, 10),
            new Vec3d(0, 0, 0)
        ));

        BaseNode explode = node("geometry.curves.explode_path");
        explode.setInput("input_path", closed);
        explode.processNode(null);
        assertEquals(Boolean.TRUE, explode.getOutput("output_valid"));
        assertEquals(3, explode.getOutput("output_count"));
    }

    @Test
    void explodePathSupportsEdgeBasedArchitecturalWorkflow() {
        PolylineData footprint = new PolylineData(List.of(
            new Vec3d(0, 64, 0),
            new Vec3d(20, 64, 0),
            new Vec3d(20, 64, 20),
            new Vec3d(0, 64, 20),
            new Vec3d(0, 64, 0)
        ));

        BaseNode explode = node("geometry.curves.explode_path");
        explode.setInput("input_path", footprint);
        explode.processNode(null);
        assertEquals(Boolean.TRUE, explode.getOutput("output_valid"));
        assertEquals(4, explode.getOutput("output_count"));

        List<?> segments = (List<?>) explode.getOutput("output_segments");
        assertFalse(segments.isEmpty());
        for (Object segment : segments) {
            assertInstanceOf(PathData.class, segment);
        }
    }

    @Test
    void extendPathLinearlyExtendsOpenPath() {
        LineData line = new LineData(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0));

        BaseNode extend = node("geometry.curves.extend_path");
        extend.setInput("input_path", line);
        extend.setInput("input_start_length", 2.0d);
        extend.setInput("input_end_length", 3.0d);
        extend.processNode(null);
        assertEquals(Boolean.TRUE, extend.getOutput("output_valid"));
        assertInstanceOf(PathData.class, extend.getOutput("output_path"));
    }

    @Test
    void extendPathRejectsNegativeLengths() {
        LineData line = new LineData(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0));

        BaseNode extend = node("geometry.curves.extend_path");
        extend.setInput("input_path", line);
        extend.setInput("input_start_length", -1.0d);
        extend.setInput("input_end_length", 1.0d);
        extend.processNode(null);
        assertEquals(Boolean.FALSE, extend.getOutput("output_valid"));
    }

    @Test
    void extendPathRejectsClosedPath() {
        PolylineData closed = new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0),
            new Vec3d(10, 0, 10),
            new Vec3d(0, 0, 0)
        ));

        BaseNode extend = node("geometry.curves.extend_path");
        extend.setInput("input_path", closed);
        extend.setInput("input_start_length", 1.0d);
        extend.setInput("input_end_length", 1.0d);
        extend.processNode(null);
        assertEquals(Boolean.FALSE, extend.getOutput("output_valid"));
    }

    @Test
    void extendPathSkipsDuplicateVerticesForTangent() {
        PolylineData polyline = new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0),
            new Vec3d(10, 0, 0)
        ));

        BaseNode extend = node("geometry.curves.extend_path");
        extend.setInput("input_path", polyline);
        extend.setInput("input_start_length", 1.0d);
        extend.setInput("input_end_length", 1.0d);
        extend.processNode(null);
        assertEquals(Boolean.TRUE, extend.getOutput("output_valid"));
    }

    @Test
    void frozenPathLanguagePortContracts() {
        assertPortType("geometry.curves.join_paths", "input_path_a", true, NodeDataType.PATH);
        assertPortType("geometry.curves.join_paths", "output_path", false, NodeDataType.PATH);
        assertPortType("geometry.curves.split_path", "input_parameter", true, NodeDataType.DOUBLE);
        assertPortType("geometry.curves.split_path", "output_path_a", false, NodeDataType.PATH);
        assertPortType("geometry.curves.split_path", "output_path_b", false, NodeDataType.PATH);
        assertPortType("geometry.curves.trim_path", "output_path", false, NodeDataType.PATH);
        assertPortType("geometry.curves.reverse_path", "output_path", false, NodeDataType.PATH);
        assertPortType("geometry.curves.closest_point_on_path", "input_point", true, NodeDataType.POINT);
        assertPortType("geometry.curves.closest_point_on_path", "output_parameter", false, NodeDataType.DOUBLE);
        assertPortType("geometry.curves.resample_path", "output_points", false, NodeDataType.POINT_LIST);
        assertPortType("geometry.curves.explode_path", "output_segments", false, NodeDataType.PATH_LIST);
        assertPortType("geometry.curves.extend_path", "output_path", false, NodeDataType.PATH);
    }

    private static BaseNode node(String typeId) {
        BaseNode node = (BaseNode) NodeRegistry.getInstance().createNodeInstance(typeId);
        assertNotNull(node, typeId);
        return node;
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        assertNotNull(node);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
            .filter(candidate -> candidate.getId().equals(portId))
            .findFirst()
            .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }
}
