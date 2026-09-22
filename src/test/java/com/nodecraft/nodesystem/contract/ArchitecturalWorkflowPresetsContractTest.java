package com.nodecraft.nodesystem.contract;

import com.google.gson.Gson;
import com.nodecraft.gui.preset.GraphPresetRules;
import com.nodecraft.nodesystem.core.BaseNode;
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
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        for (String resourcePath : List.of(
            "/nodecraft/graph_presets.json",
            "/nodecraft/graph_presets_updated.json"
        )) {
            GraphPresetRules rules = loadRules(resourcePath);
            for (String workflowId : WORKFLOW_IDS) {
                GraphPresetRules.GraphPresetDefinition preset = findPreset(rules, workflowId);
                assertNotNull(preset, resourcePath + " missing " + workflowId);
                assertEquals("composite", preset.kind);
                assertTrue(preset.nodes.size() >= 4, workflowId + " should be a multi-node workflow");
                assertTrue(preset.connections.size() >= 3, workflowId + " should wire the chain");
            }
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

        BeamGridNode beams = new BeamGridNode();
        beams.setInput("input_face", face);
        beams.setInput("input_columns", 2);
        beams.setInput("input_rows", 2);
        beams.setInput("input_beam_width", 0.2d);
        beams.setInput("input_beam_depth", 0.25d);
        beams.setInput("input_slab_thickness", 0.3d);
        beams.processNode(null);
        assertEquals(Boolean.TRUE, beams.getOutput("output_valid"));
        assertInstanceOf(List.class, beams.getOutput("output_center_lines"));

        BaseNode combine = (BaseNode) registry.createNodeInstance("geometry.combine.geometry");
        combine.setInput("input_geometry_0", slab.getOutput("output_geometry"));
        combine.setInput("input_geometry_1", beams.getOutput("output_geometry"));
        combine.processNode(null);
        assertEquals(Boolean.TRUE, combine.getOutput("output_valid"));
        assertInstanceOf(GeometryData.class, combine.getOutput("output_geometry"));
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
        assertNotNull(windows.getOutput("output_frames"));
    }

    @Test
    void workflowPresetsAvoidConvenienceGodNodes() {
        GraphPresetRules rules = loadRules("/nodecraft/graph_presets.json");
        for (String workflowId : WORKFLOW_IDS) {
            GraphPresetRules.GraphPresetDefinition preset = findPreset(rules, workflowId);
            assertNotNull(preset);
            for (GraphPresetRules.PresetNode node : preset.nodes) {
                assertTrue(
                    !"geometry.architectural_primitives.floor_slab_with_beams".equals(node.typeId),
                    workflowId + " should prefer Floor Slab + Beam Grid"
                );
                assertTrue(
                    !"geometry.architectural_primitives.roof_generator".equals(node.typeId),
                    workflowId + " should prefer Roof Base"
                );
            }
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
