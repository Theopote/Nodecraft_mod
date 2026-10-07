package com.nodecraft.nodesystem.contract;

import com.google.gson.Gson;
import com.nodecraft.gui.preset.GraphPresetTestResources;
import com.nodecraft.gui.preset.GraphPresetRules;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.BeamGridNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.FloorSlabNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RailingNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofBaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowFrameNode;
import com.nodecraft.nodesystem.nodes.transform.placement.PlaceGeometryOnFramesNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Batch 13.2: architectural mini-workflow presets teach composable host/placement/reference chains.
 */
class ArchitecturalWorkflowPresetsContractTest {

    private static final Gson GSON = new Gson();
    private static final Set<String> WORKFLOW_IDS = Set.of(
        "architectural.workflow.wall_with_windows",
        "architectural.workflow.floor_with_beam_grid",
        "architectural.workflow.roof_with_eave"
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void architectureWorkflowPresetsExistInBuiltinCatalog() {
        GraphPresetRules rules = loadRules(GraphPresetTestResources.BUILTIN_GRAPH_PRESETS);
        for (String workflowId : WORKFLOW_IDS) {
            GraphPresetRules.GraphPresetDefinition preset = findPreset(rules, workflowId);
            assertNotNull(preset, GraphPresetTestResources.BUILTIN_GRAPH_PRESETS + " missing " + workflowId);
            assertEquals("composite", preset.kind);
            assertTrue(preset.nodes.size() >= 4, workflowId + " should be a multi-node workflow");
            assertTrue(preset.connections.size() >= 3, workflowId + " should wire the chain");
        }
    }

    @Test
    void floorWithBeamGridChainRunsPure() {
        BoxGeometryData box = new BoxGeometryData(
            new Vector3d(5.0d, 0.2d, 4.0d),
            new Vector3d(5.0d, 0.2d, 4.0d)
        );
        BoxFaceData face = requireFace(box, "Top");

        FloorSlabNode slab = new FloorSlabNode();
        slab.setInput("input_face", face);
        slab.setInput("input_thickness", 0.3d);
        slab.processNode(null);
        assertEquals(Boolean.TRUE, slab.getOutput("output_valid"));

        BoxFaceData slabBottom = assertInstanceOf(BoxFaceData.class, slab.getOutput("output_bottom_face"));
        double beamDrop = 0.1d;
        double beamDepth = 0.25d;

        BeamGridNode beams = new BeamGridNode();
        beams.setInput("input_face", slabBottom);
        connectInput(beams, "input_columns", NodeDataType.INTEGER);
        connectInput(beams, "input_rows", NodeDataType.INTEGER);
        connectInput(beams, "input_beam_width", NodeDataType.DOUBLE);
        connectInput(beams, "input_beam_depth", NodeDataType.DOUBLE);
        connectInput(beams, "input_beam_drop", NodeDataType.DOUBLE);
        beams.setInput("input_columns", 2);
        beams.setInput("input_rows", 2);
        beams.setInput("input_beam_width", 0.2d);
        beams.setInput("input_beam_depth", beamDepth);
        beams.setInput("input_beam_drop", beamDrop);
        beams.processNode(null);
        assertEquals(Boolean.TRUE, beams.getOutput("output_valid"));
        assertInstanceOf(List.class, beams.getOutput("output_center_lines"));

        @SuppressWarnings("unchecked")
        List<com.nodecraft.nodesystem.datatypes.PointData> centers =
            (List<com.nodecraft.nodesystem.datatypes.PointData>) beams.getOutput("output_centers");
        Vector3d outward = slabBottom.getNormal();
        Vector3d beamCenter = centers.getFirst().position();
        Vector3d beamTop = new Vector3d(beamCenter).fma(-beamDepth / 2.0d, outward);
        double dropAlongOutward = new Vector3d(beamTop).sub(slabBottom.getCenter()).dot(outward);
        assertEquals(beamDrop, dropAlongOutward, 0.05d,
            "beam top should sit beamDrop below slab bottom face along outward normal");

        BaseNode combine = (BaseNode) registry.createNodeInstance("geometry.combine.geometry");
        connectInput(combine, "input_geometry_0", NodeDataType.GEOMETRY);
        connectInput(combine, "input_geometry_1", NodeDataType.GEOMETRY);
        combine.setInput("input_geometry_0", slab.getOutput("output_geometry"));
        combine.setInput("input_geometry_1", beams.getOutput("output_geometry"));
        combine.processNode(null);
        assertEquals(Boolean.TRUE, combine.getOutput("output_valid"));
        assertInstanceOf(GeometryData.class, combine.getOutput("output_geometry"));
    }

    @Test
    void beamGridBudgetUsesColumnsPlusRowsNotProduct() {
        int columns = GenerationLimits.MAX_ARCHITECTURAL_INSTANCES / 2 + 1;
        int rows = 2;
        assertTrue((long) columns * rows > GenerationLimits.MAX_ARCHITECTURAL_INSTANCES);
        assertTrue((long) columns + rows <= GenerationLimits.MAX_ARCHITECTURAL_INSTANCES);

        double beamWidth = 0.001d;
        double faceSize = Math.max(columns, rows) * beamWidth + 2.0d;
        List<Vector3d> corners = List.of(
            new Vector3d(0.0d, 0.0d, 0.0d),
            new Vector3d(faceSize, 0.0d, 0.0d),
            new Vector3d(faceSize, faceSize, 0.0d),
            new Vector3d(0.0d, faceSize, 0.0d)
        );
        BoxFaceData face = new BoxFaceData(0, "top", List.of(0, 1, 2, 3), corners,
            new Vector3d(faceSize / 2.0d, faceSize / 2.0d, 0.0d), new Vector3d(0.0d, 0.0d, 1.0d));

        BeamGridNode beams = new BeamGridNode();
        connectInput(beams, "input_columns", NodeDataType.INTEGER);
        connectInput(beams, "input_rows", NodeDataType.INTEGER);
        connectInput(beams, "input_beam_width", NodeDataType.DOUBLE);
        beams.setInput("input_face", face);
        beams.setInput("input_columns", columns);
        beams.setInput("input_rows", rows);
        beams.setInput("input_beam_width", beamWidth);
        beams.setInput("input_beam_depth", 0.2d);
        beams.setInput("input_beam_drop", 0.0d);
        beams.setInput("input_margin", 0.0d);
        beams.processNode(null);

        assertEquals(Boolean.TRUE, beams.getOutput("output_valid"),
            String.valueOf(beams.getOutput("output_error")));
        assertEquals(columns + rows, beams.getOutput("output_count"));
        assertTrue(beams.getInputPorts().stream().noneMatch(p -> "input_slab_thickness".equals(p.getId())),
            "Slab Thickness port must be removed");
    }

    @Test
    void roofEaveDrivesRailingPure() {
        BoxGeometryData box = new BoxGeometryData(
            new Vector3d(5.0d, 0.2d, 4.0d),
            new Vector3d(5.0d, 0.2d, 4.0d)
        );
        BoxFaceData face = requireFace(box, "Top");

        RoofBaseNode roof = new RoofBaseNode();
        roof.setInput("input_face", face);
        roof.setInput("input_roof_type", "gable");
        roof.setInput("input_height", 2.0d);
        roof.setInput("input_overhang", 0.4d);
        roof.processNode(null);
        assertEquals(Boolean.TRUE, roof.getOutput("output_valid"));
        PathData eave = assertInstanceOf(PathData.class, roof.getOutput("output_eave_path"));

        RailingNode railing = new RailingNode();
        railing.setInput("input_path", eave);
        railing.processNode(null);
        assertEquals(Boolean.TRUE, railing.getOutput("output_valid"));
        assertInstanceOf(GeometryData.class, railing.getOutput("output_geometry"));
    }

    @Test
    void wallOpeningsAndWindowsShareFacePure() {
        BoxGeometryData box = new BoxGeometryData(
            new Vector3d(4.0d, 1.5d, 0.25d),
            new Vector3d(4.0d, 1.5d, 0.25d)
        );
        BoxFaceData face = requireFace(box, "Front");

        WallWithOpeningsNode wall = new WallWithOpeningsNode();
        wall.setInput("input_face", face);
        wall.setInput("input_columns", 2);
        wall.setInput("input_rows", 1);
        wall.setInput("input_wall_thickness", 0.4d);
        wall.setInput("input_opening_width", 1.2d);
        wall.setInput("input_opening_height", 1.4d);
        wall.setInput("input_margin", 0.4d);
        wall.processNode(null);
        assertEquals(Boolean.TRUE, wall.getOutput("output_valid"));
        assertInstanceOf(GeometryData.class, wall.getOutput("output_geometry"));
        assertInstanceOf(GeometryData.class, wall.getOutput("output_openings"));

        WindowArrayNode windows = new WindowArrayNode();
        windows.setInput("input_face", face);
        windows.setInput("input_columns", 2);
        windows.setInput("input_rows", 1);
        windows.setInput("input_window_width", 1.2d);
        windows.setInput("input_window_height", 1.4d);
        windows.setInput("input_margin", 0.4d);
        windows.setInput("input_depth", 0.3d);
        windows.processNode(null);
        assertEquals(Boolean.TRUE, windows.getOutput("output_valid"));
        assertNotNull(windows.getOutput("output_openings"));
        assertNotNull(windows.getOutput("output_frames"));

        BaseNode difference = (BaseNode) registry.createNodeInstance("geometry.boolean.difference");
        difference.setInput("input_base", wall.getOutput("output_geometry"));
        difference.setInput("input_cutter", windows.getOutput("output_openings"));
        difference.processNode(null);
        assertEquals(Boolean.TRUE, difference.getOutput("output_valid"));

        WindowFrameNode frame = new WindowFrameNode();
        connectInput(frame, "input_frame_width", NodeDataType.DOUBLE);
        connectInput(frame, "input_frame_height", NodeDataType.DOUBLE);
        frame.setInput("input_frame_width", 1.2d);
        frame.setInput("input_frame_height", 1.4d);
        frame.processNode(null);

        PlaceGeometryOnFramesNode place = new PlaceGeometryOnFramesNode();
        connectInput(place, "input_frames", NodeDataType.FRAME_LIST);
        place.setInput("input_geometry", frame.getOutput("output_geometry"));
        place.setInput("input_frames", windows.getOutput("output_frames"));
        place.processNode(null);
        assertEquals(Boolean.TRUE, place.getOutput("output_valid"));
    }

    @Test
    void builtinPresetsForbidOpeningPortsWiredDirectlyToCombine() {
        GraphPresetRules rules = loadRules(GraphPresetTestResources.BUILTIN_GRAPH_PRESETS);
        Set<String> openingPorts = Set.of("output_openings");
        Set<String> openingNodeTypes = Set.of(
            "geometry.architectural_primitives.window_array",
            "geometry.architectural_primitives.door_array"
        );

        List<String> violations = new ArrayList<>();
        for (GraphPresetRules.PresetCategory category : rules.categories) {
            if (category == null || category.presets == null) {
                continue;
            }
            for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                if (preset == null || preset.nodes == null || preset.connections == null) {
                    continue;
                }
                Map<String, GraphPresetRules.PresetNode> nodesByRef = preset.nodes.stream()
                    .filter(node -> node != null && node.ref != null)
                    .collect(Collectors.toMap(node -> node.ref, Function.identity(), (a, b) -> a));
                for (GraphPresetRules.PresetConnection connection : preset.connections) {
                    if (connection == null) {
                        continue;
                    }
                    GraphPresetRules.PresetNode from = nodesByRef.get(connection.fromRef);
                    if (from == null || !openingNodeTypes.contains(from.typeId)) {
                        continue;
                    }
                    if (!openingPorts.contains(connection.fromPort)) {
                        continue;
                    }
                    GraphPresetRules.PresetNode to = nodesByRef.get(connection.toRef);
                    if (to != null
                        && "geometry.combine.geometry".equals(to.typeId)
                        && !"combine_openings".equals(connection.toRef)
                        && connection.toPort != null
                        && connection.toPort.startsWith("input_geometry_")) {
                        violations.add(preset.id + ": "
                            + connection.fromRef + "." + connection.fromPort
                            + " → " + connection.toRef + "." + connection.toPort);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
            "Opening geometry must be cut with Difference, not combined: " + violations);
    }

    @Test
    void wallWithWindowsPresetUsesDifferenceAndPlaceOnFrames() {
        GraphPresetRules rules = loadRules(GraphPresetTestResources.BUILTIN_GRAPH_PRESETS);
        GraphPresetRules.GraphPresetDefinition preset = findPreset(rules, "architectural.workflow.wall_with_windows");
        assertNotNull(preset);

        assertTrue(preset.nodes.stream().anyMatch(n ->
            "geometry.architectural_primitives.wall_slab".equals(n.typeId) && "wall".equals(n.ref)));
        assertFalse(preset.nodes.stream().anyMatch(n ->
            "geometry.architectural_primitives.wall_with_openings".equals(n.typeId)));
        assertTrue(preset.nodes.stream().anyMatch(n -> "geometry.boolean.difference".equals(n.typeId)));
        assertTrue(preset.nodes.stream().anyMatch(n -> "geometry.architectural_primitives.window_frame".equals(n.typeId)));
        assertTrue(preset.nodes.stream().anyMatch(n ->
            "transform.placement.place_geometry_on_frames".equals(n.typeId)));
        assertTrue(preset.nodes.stream().anyMatch(n ->
            "math.scalar_math.multiplication".equals(n.typeId) && "opening_depth".equals(n.ref)));

        assertTrue(preset.connections.stream().anyMatch(c ->
            "windows".equals(c.fromRef) && "output_openings".equals(c.fromPort) && "cut".equals(c.toRef)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "place_frames".equals(c.fromRef) && "combine".equals(c.toRef)));
        assertFalse(preset.connections.stream().anyMatch(c ->
            "wall".equals(c.fromRef) && "output_openings".equals(c.fromPort)));
        assertFalse(preset.connections.stream().anyMatch(c ->
            "windows".equals(c.fromRef)
                && "output_openings".equals(c.fromPort)
                && "combine".equals(c.toRef)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "wall_thickness".equals(c.fromRef)
                && "opening_depth".equals(c.toRef)
                && "input_a".equals(c.toPort)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "depth_factor".equals(c.fromRef)
                && "opening_depth".equals(c.toRef)
                && "input_b".equals(c.toPort)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "opening_depth".equals(c.fromRef)
                && "output_product".equals(c.fromPort)
                && "windows".equals(c.toRef)
                && "input_depth".equals(c.toPort)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "win_width".equals(c.fromRef) && "windows".equals(c.toRef)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "win_width".equals(c.fromRef) && "window_frame".equals(c.toRef)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "win_height".equals(c.fromRef) && "window_frame".equals(c.toRef)));
    }

    @Test
    void workflowPresetsAvoidConvenienceGodNodes() {
        GraphPresetRules rules = loadRules(GraphPresetTestResources.BUILTIN_GRAPH_PRESETS);
        for (String workflowId : WORKFLOW_IDS) {
            GraphPresetRules.GraphPresetDefinition preset = findPreset(rules, workflowId);
            assertNotNull(preset);
            for (GraphPresetRules.PresetNode node : preset.nodes) {
                assertNotEquals("geometry.architectural_primitives.floor_slab_with_beams", node.typeId, workflowId + " should prefer Floor Slab + Beam Grid");
                assertNotEquals("geometry.architectural_primitives.roof_generator", node.typeId, workflowId + " should prefer Roof Base");
            }
        }
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
        target.getInput(inputPortId);
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

    private static BoxFaceData requireFace(BoxGeometryData box, String name) {
        return box.getFaces().stream()
            .filter(face -> name.equalsIgnoreCase(face.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing face " + name));
    }

    private static GraphPresetRules.GraphPresetDefinition findPreset(GraphPresetRules rules, String presetId) {
        for (GraphPresetRules.PresetCategory category : rules.categories) {
            if (category == null || category.presets == null) {
                continue;
            }
            for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                if (preset != null && presetId.equals(preset.id)) {
                    return preset;
                }
            }
        }
        return null;
    }

    private static GraphPresetRules loadRules(String resourcePath) {
        try (InputStream stream = ArchitecturalWorkflowPresetsContractTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Missing " + resourcePath);
            return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), GraphPresetRules.class);
        } catch (Exception e) {
            throw new AssertionError("Failed to load " + resourcePath, e);
        }
    }
}
