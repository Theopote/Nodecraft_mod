package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.io.SavedPosition;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.ColumnNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofBaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofGeneratorNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.StaircaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architectural Primitives v1 language fence (Graph V68).
 */
class ArchitecturalPrimitivesLanguageContractTest {

    private static final List<String> CANONICAL_ORDERED_IDS = List.of(
            "geometry.architectural_primitives.window_array",
            "geometry.architectural_primitives.door_array",
            "geometry.architectural_primitives.column_grid",
            "geometry.architectural_primitives.railing",
            "geometry.architectural_primitives.roof_base",
            "geometry.architectural_primitives.staircase",
            "geometry.architectural_primitives.roof_generator",
            "geometry.architectural_primitives.facade_panel_array",
            "geometry.architectural_primitives.arch_opening",
            "geometry.architectural_primitives.wall_with_openings",
            "geometry.architectural_primitives.pilaster_cornice",
            "geometry.architectural_primitives.array_along_curve",
            "geometry.architectural_primitives.floor_slab",
            "geometry.architectural_primitives.beam_grid",
            "geometry.architectural_primitives.molding_profile",
            "geometry.architectural_primitives.wall_along_path",
            "geometry.architectural_primitives.beam_along_path",
            "geometry.architectural_primitives.column"
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV68() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void exactlyEighteenCanonicalNodesWithOrdersZeroThroughSeventeen() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("geometry.architectural_primitives."))
                .sorted()
                .toList();
        assertEquals(18, ids.size(), ids.toString());
        assertEquals(Set.copyOf(CANONICAL_ORDERED_IDS), Set.copyOf(ids));
        assertFalse(ids.contains("geometry.architectural_primitives.floor_slab_with_beams"));
        assertFalse(ids.contains("geometry.architectural_primitives.deconstruct_opening"));

        Map<Integer, String> byOrder = new LinkedHashMap<>();
        for (String id : CANONICAL_ORDERED_IDS) {
            int order = orderOf(id);
            assertNull(byOrder.put(order, id), "duplicate order " + order + ": " + byOrder.get(order) + " vs " + id);
        }
        for (int i = 0; i < 18; i++) {
            assertEquals(CANONICAL_ORDERED_IDS.get(i), byOrder.get(i), "order " + i);
        }
    }

    @Test
    void allPureWithValidAndError() {
        for (String id : CANONICAL_ORDERED_IDS) {
            INode node = registry.createNodeInstance(id);
            assertNotNull(node, id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id), id);
            assertPortType(node, "output_valid", NodeDataType.BOOLEAN);
            assertPortType(node, "output_error", NodeDataType.STRING);
        }
    }

    @Test
    void architecturalBudgetsAreDefined() {
        assertEquals(GenerationLimits.MAX_GEOMETRY_INSTANCES, GenerationLimits.MAX_ARCHITECTURAL_INSTANCES);
        assertTrue(GenerationLimits.MAX_ARCHITECTURAL_PATH_SEGMENTS > 0);
        assertTrue(GenerationLimits.MAX_ARCHITECTURAL_PROFILE_SEGMENTS > 0);
        assertFalse(GeometryOutputUtils.fitsArchitecturalInstanceBudget(
                GenerationLimits.MAX_ARCHITECTURAL_INSTANCES, 2));
        assertTrue(GeometryOutputUtils.fitsArchitecturalInstanceBudget(2, 3));
    }

    @Test
    void packageHasNoPermissiveResolversOrGraphFacingIntValue() throws Exception {
        Path dir = Path.of("src/main/java/com/nodecraft/nodesystem/nodes/geometry/architectural_primitives");
        assertTrue(Files.isDirectory(dir), dir.toString());
        try (Stream<Path> stream = Files.walk(dir)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
            assertFalse(javaFiles.isEmpty());
            for (Path file : javaFiles) {
                String source = Files.readString(file);
                assertFalse(source.contains("resolvePositiveInt("), file + " still has resolvePositiveInt");
                assertFalse(source.contains("resolvePositiveDouble("), file + " still has resolvePositiveDouble");
                assertFalse(source.contains("resolveNonNegativeDouble("), file + " still has resolveNonNegativeDouble");
                if (file.getFileName().toString().endsWith("Node.java")) {
                    assertFalse(source.contains(".intValue()"), file + " still uses intValue");
                }
            }
        }
        for (Method method : ArchitecturalInputUtils.class.getDeclaredMethods()) {
            assertFalse(method.getName().startsWith("resolvePositive"));
        }
    }

    @Test
    void windowArrayRejectsNonExactColumnsAndOverBudget() {
        WindowArrayProbe badColumns = new WindowArrayProbe();
        BoxFaceData face = sampleFace(20, 10);
        badColumns.connectInput("input_columns", NodeDataType.INTEGER);
        badColumns.setInput("input_face", face);
        badColumns.setInput("input_columns", 3.9d);
        badColumns.setInput("input_rows", 2);
        badColumns.setInput("input_window_width", 1.0d);
        badColumns.setInput("input_window_height", 1.0d);
        badColumns.setInput("input_margin", 0.0d);
        badColumns.setInput("input_depth", 1.0d);
        badColumns.processNode(null);
        assertEquals(Boolean.FALSE, badColumns.getOutput("output_valid"));
        assertTrue(String.valueOf(badColumns.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("column"));

        WindowArrayProbe probe = new WindowArrayProbe();
        probe.connectInput("input_columns", NodeDataType.INTEGER);
        probe.connectInput("input_rows", NodeDataType.INTEGER);
        probe.setInput("input_face", face);
        probe.setInput("input_columns", GenerationLimits.MAX_ARCHITECTURAL_INSTANCES);
        probe.setInput("input_rows", 2);
        probe.setInput("input_window_width", 0.01d);
        probe.setInput("input_window_height", 0.01d);
        probe.setInput("input_margin", 0.0d);
        probe.setInput("input_depth", 1.0d);
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("limit"));
    }

    @Test
    void columnFrameXorBaseAndUnknownShapeFailClosed() {
        ColumnProbe both = new ColumnProbe();
        both.connectInput("input_frame", NodeDataType.FRAME);
        both.connectInput("input_base", NodeDataType.POINT);
        both.setInput("input_frame", new FrameData(new Vector3d(), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)));
        both.setInput("input_base", new PointData(0, 0, 0));
        both.setInput("input_height", 3.0d);
        both.setInput("input_radius", 0.5d);
        both.processNode(null);
        assertEquals(Boolean.FALSE, both.getOutput("output_valid"));
        assertTrue(String.valueOf(both.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("exactly one"));

        ColumnProbe shape = new ColumnProbe();
        shape.connectInput("input_base", NodeDataType.POINT);
        shape.connectInput("input_shape", NodeDataType.STRING);
        shape.setInput("input_base", new PointData(0, 0, 0));
        shape.setInput("input_height", 3.0d);
        shape.setInput("input_radius", 0.5d);
        shape.setInput("input_shape", "foobar");
        shape.processNode(null);
        assertEquals(Boolean.FALSE, shape.getOutput("output_valid"));
        assertTrue(String.valueOf(shape.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("shape"));
    }

    @Test
    void roofBaseAndGeneratorTypeSplit() {
        RoofBaseProbe base = new RoofBaseProbe();
        base.connectInput("input_roof_type", NodeDataType.STRING);
        base.setInput("input_face", sampleFace(8, 8));
        base.setInput("input_roof_type", "hip");
        base.setInput("input_height", 2.0d);
        base.setInput("input_thickness", 0.5d);
        base.processNode(null);
        assertEquals(Boolean.FALSE, base.getOutput("output_valid"));

        RoofGeneratorProbe generator = new RoofGeneratorProbe();
        generator.connectInput("input_roof_type", NodeDataType.STRING);
        generator.setInput("input_face", sampleFace(8, 8));
        generator.setInput("input_roof_type", "gable");
        generator.setInput("input_height", 2.0d);
        generator.setInput("input_thickness", 0.5d);
        generator.processNode(null);
        assertEquals(Boolean.FALSE, generator.getOutput("output_valid"));
        assertTrue(String.valueOf(generator.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("roof base"));
    }

    @Test
    void staircaseFirstFlightDoesNotClamp() {
        StaircaseProbe node = new StaircaseProbe();
        node.connectInput("input_step_count", NodeDataType.INTEGER);
        node.connectInput("input_first_flight_steps", NodeDataType.INTEGER);
        node.connectInput("input_layout", NodeDataType.STRING);
        node.setInput("input_path", pathLine(0, 0, 0, 10, 0, 0));
        node.setInput("input_step_count", 10);
        node.setInput("input_first_flight_steps", 10);
        node.setInput("input_layout", "u");
        node.setInput("input_width", 1.0d);
        node.setInput("input_step_rise", 0.2d);
        node.setInput("input_step_run", 0.3d);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
    }

    @Test
    void wallWithOpeningsKeepsSeparateOpeningsPort() {
        WallWithOpeningsNode node = new WallWithOpeningsNode();
        assertPortType(node, "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(node, "output_openings", NodeDataType.GEOMETRY);
        assertFalse(hasPort(node, "output_difference"));
    }

    @Test
    void packGeometryZeroOneN() {
        assertNull(GeometryOutputUtils.packGeometry(List.of()));
        BoxGeometryData one = new BoxGeometryData(new Vector3d(), new Vector3d(1, 1, 1));
        assertEquals(one, GeometryOutputUtils.packGeometry(List.of(one)));
        assertTrue(GeometryOutputUtils.packGeometry(List.of(one, one)) instanceof com.nodecraft.nodesystem.datatypes.CompositeGeometryData);
    }

    @Test
    void packGeometryRejectsNullMembers() {
        BoxGeometryData one = new BoxGeometryData(new Vector3d(), new Vector3d(1, 1, 1));
        List<GeometryData> withNull = new ArrayList<>();
        withNull.add(one);
        withNull.add(null);
        withNull.add(one);
        assertNull(GeometryOutputUtils.packGeometry(withNull));
        assertNull(GeometryOutputUtils.packGeometry(java.util.Collections.singletonList(null)));
    }


    private static BoxFaceData sampleFace(double width, double height) {
        double halfW = width / 2.0d;
        double halfH = height / 2.0d;
        List<Vector3d> corners = List.of(
                new Vector3d(-halfW, 0, 0),
                new Vector3d(halfW, 0, 0),
                new Vector3d(halfW, height, 0),
                new Vector3d(-halfW, height, 0)
        );
        return new BoxFaceData(0, "front", List.of(0, 1, 2, 3), corners,
                new Vector3d(0, halfH, 0), new Vector3d(0, 0, 1));
    }

    private static Object pathLine(double x0, double y0, double z0, double x1, double y1, double z1) {
        return PathData.fromLine(new LineData(
                new net.minecraft.util.math.Vec3d(x0, y0, z0),
                new net.minecraft.util.math.Vec3d(x1, y1, z1)));
    }

    private static int orderOf(String typeId) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        return node.getClass().getAnnotation(NodeInfo.class).order();
    }

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        IPort port = findPort(node, portId);
        assertNotNull(port, node.getTypeId() + "#" + portId);
        assertEquals(expected, port.getDataType(), node.getTypeId() + "#" + portId);
    }

    private static boolean hasPort(INode node, String portId) {
        return findPort(node, portId) != null;
    }

    private static IPort findPort(INode node, String portId) {
        for (IPort port : node.getInputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
        target.getInput(inputPortId);
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        node.state = new HashMap<>();
        return node;
    }

    private static SavedConnection wire(String sourceNode, String sourcePort, String targetNode, String targetPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = sourceNode;
        connection.sourcePortId = sourcePort;
        connection.targetNodeId = targetNode;
        connection.targetPortId = targetPort;
        return connection;
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }

    private static final class WindowArrayProbe extends WindowArrayNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalPrimitivesLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class ColumnProbe extends ColumnNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalPrimitivesLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RoofBaseProbe extends RoofBaseNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalPrimitivesLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RoofGeneratorProbe extends RoofGeneratorNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalPrimitivesLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class StaircaseProbe extends StaircaseNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalPrimitivesLanguageContractTest.connectInput(this, portId, outputType);
        }
    }
}
