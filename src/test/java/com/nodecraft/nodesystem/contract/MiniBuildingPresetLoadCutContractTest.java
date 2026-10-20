package com.nodecraft.nodesystem.contract;

import com.google.gson.Gson;
import com.nodecraft.gui.preset.GraphPresetRules;
import com.nodecraft.gui.preset.GraphPresetTestResources;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.contract.support.ArchitecturalVoxelAssert;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.execution.runtime.CancellationToken;
import com.nodecraft.nodesystem.execution.ExecutionRunLimits;
import com.nodecraft.nodesystem.execution.IncrementalExecutionOptions;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads architectural.residential.mini_building_v1 from the builtin preset catalog
 * (not a hand-rebuilt probe) and verifies the end-to-end cut / frame / roof / placement path.
 */
class MiniBuildingPresetLoadCutContractTest {

    private static final String PRESET_ID = "architectural.residential.mini_building_v1";
    private static final Gson GSON = new Gson();

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void miniBuildingPresetLoadsAndCutsOpeningCentersInVoxels() throws Exception {
        GraphPresetRules.GraphPresetDefinition preset = requirePreset(PRESET_ID);
        assertEquals("composite", preset.kind);

        MaterializedGraph material = materialize(preset);
        seedPlayerPosition(material.byRef.get("player_pos"), 0.0d, 0.0d, 0.0d);

        ExecutionContext context = new ExecutionContext(null, null);
        NodeExecutor executor = new NodeExecutor(
            material.graph,
            context,
            null,
            IncrementalExecutionOptions.defaults(),
            ExecutionRunLimits.defaults(),
            CancellationToken.none(),
            true,
            null,
            true
        );
        assertTrue(executor.executeSync(), "mini_building_v1 graph must execute");

        INode walls = material.byRef.get("walls");
        INode windows = material.byRef.get("windows");
        INode cutWalls = material.byRef.get("cut_walls");
        INode placeFrames = material.byRef.get("place_frames");
        INode roof = material.byRef.get("roof");
        INode floor = material.byRef.get("floor");
        INode combine = material.byRef.get("combine");
        INode materialNode = material.byRef.get("material");
        INode previewBlocks = material.byRef.get("preview_blocks");
        INode applyChanges = material.byRef.get("apply_changes");
        INode frontFace = material.byRef.get("front_face");

        assertEquals(Boolean.TRUE, walls.getOutput("output_valid"), String.valueOf(walls.getOutput("output_error")));
        assertEquals(Boolean.TRUE, windows.getOutput("output_valid"), String.valueOf(windows.getOutput("output_error")));
        assertEquals(Boolean.TRUE, cutWalls.getOutput("output_valid"), String.valueOf(cutWalls.getOutput("output_error")));
        assertEquals(Boolean.TRUE, placeFrames.getOutput("output_valid"), String.valueOf(placeFrames.getOutput("output_error")));
        assertEquals(Boolean.TRUE, roof.getOutput("output_valid"), String.valueOf(roof.getOutput("output_error")));
        assertEquals(Boolean.TRUE, floor.getOutput("output_valid"), String.valueOf(floor.getOutput("output_error")));
        assertEquals(Boolean.TRUE, combine.getOutput("output_valid"), String.valueOf(combine.getOutput("output_error")));
        assertEquals(Boolean.TRUE, materialNode.getOutput("output_valid"),
            String.valueOf(materialNode.getOutput("output_error")));

        assertEquals(windows.getOutput("output_count"), placeFrames.getOutput("output_count"),
            "window frame instances must match opening count");

        GeometryData wallGeom = assertInstanceOf(GeometryData.class, walls.getOutput("output_geometry"));
        GeometryData cutGeom = assertInstanceOf(GeometryData.class, cutWalls.getOutput("output_geometry"));
        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) windows.getOutput("output_centers");
        assertNotNull(centers);
        assertEquals(2, centers.size());

        BlockPosList solidWall = GeometryVoxelizer.voxelize(wallGeom, true);
        BlockPosList cutBlocks = GeometryVoxelizer.voxelize(cutGeom, true);
        Set<net.minecraft.util.math.BlockPos> cutSolid = ArchitecturalVoxelAssert.toSolidSet(cutBlocks);
        Set<net.minecraft.util.math.BlockPos> fullSolid = ArchitecturalVoxelAssert.toSolidSet(solidWall);

        assertTrue(fullSolid.size() > 0, "loaded preset walls must voxelize");
        ArchitecturalVoxelAssert.assertFewerBlocksThan(cutSolid, fullSolid);
        ArchitecturalVoxelAssert.assertOpeningCentersEmpty(cutSolid, centers);

        Object faceObj = frontFace.getOutput("output_face");
        assertNotNull(faceObj, "front_face must resolve from preset volume");
        Vector3d faceNormal = ((com.nodecraft.nodesystem.datatypes.BoxFaceData) faceObj).getNormal();
        ArchitecturalVoxelAssert.assertPierBetweenWindowsHasBlock(
            cutSolid, centers.get(0), centers.get(1), faceNormal);
        ArchitecturalVoxelAssert.assertOpeningCutsThroughThickness(
            cutSolid, centers.get(0).position(), faceNormal, 0.6d);

        Object placements = materialNode.getOutput("output_placements");
        assertNotNull(placements, "Assign Block Type must emit placements");
        assertTrue(placements instanceof List<?> list && !list.isEmpty(),
            "material placements must be non-empty");

        // Preview/Apply share the same material placements wire in the loaded preset.
        assertTrue(preset.connections.stream().anyMatch(c ->
            "material".equals(c.fromRef)
                && "output_placements".equals(c.fromPort)
                && "preview_blocks".equals(c.toRef)
                && "input_block_placements".equals(c.toPort)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "material".equals(c.fromRef)
                && "output_placements".equals(c.fromPort)
                && "apply_changes".equals(c.toRef)
                && "input_block_placements".equals(c.toPort)));
        assertTrue(material.graph.getConnectedOutputNodeId(
                previewBlocks.getId(), "input_block_placements").equals(materialNode.getId()));
        assertTrue(material.graph.getConnectedOutputNodeId(
                applyChanges.getId(), "input_block_placements").equals(materialNode.getId()));

        assertTrue(preset.connections.stream().anyMatch(c ->
            "wall_thickness".equals(c.fromRef) && "walls".equals(c.toRef) && "input_thickness".equals(c.toPort)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "opening_depth".equals(c.fromRef) && "windows".equals(c.toRef) && "input_depth".equals(c.toPort)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "walls".equals(c.fromRef) && "cut_walls".equals(c.toRef) && "input_base".equals(c.toPort)));
        assertTrue(preset.connections.stream().anyMatch(c ->
            "windows".equals(c.fromRef) && "cut_walls".equals(c.toRef) && "input_cutter".equals(c.toPort)));
    }

    private static GraphPresetRules.GraphPresetDefinition requirePreset(String presetId) {
        try (InputStream stream = MiniBuildingPresetLoadCutContractTest.class
            .getResourceAsStream(GraphPresetTestResources.BUILTIN_GRAPH_PRESETS)) {
            assertNotNull(stream, "Missing " + GraphPresetTestResources.BUILTIN_GRAPH_PRESETS);
            GraphPresetRules rules = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8), GraphPresetRules.class);
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
            throw new AssertionError("Missing preset " + presetId);
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Failed to load preset catalog", e);
        }
    }

    private static MaterializedGraph materialize(GraphPresetRules.GraphPresetDefinition preset) {
        NodeGraph graph = new NodeGraph(Objects.requireNonNullElse(preset.displayName, preset.id));
        Map<String, INode> byRef = new HashMap<>();
        Map<String, UUID> idByRef = new HashMap<>();

        for (GraphPresetRules.PresetNode presetNode : preset.nodes) {
            assertNotNull(presetNode);
            assertNotNull(presetNode.ref);
            assertNotNull(presetNode.typeId);
            INode node = registry.createNodeInstance(presetNode.typeId);
            assertNotNull(node, "unknown type " + presetNode.typeId);
            if (presetNode.state != null) {
                node.setNodeState(presetNode.state);
            }
            assertTrue(graph.addNode(node), "failed to add " + presetNode.ref);
            byRef.put(presetNode.ref, node);
            idByRef.put(presetNode.ref, node.getId());
        }

        int connected = 0;
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            assertNotNull(connection);
            UUID fromId = idByRef.get(connection.fromRef);
            UUID toId = idByRef.get(connection.toRef);
            assertNotNull(fromId, "missing fromRef " + connection.fromRef);
            assertNotNull(toId, "missing toRef " + connection.toRef);
            assertTrue(
                graph.connect(fromId, connection.fromPort, toId, connection.toPort),
                "FAILED: " + connection.fromRef + "." + connection.fromPort
                    + " -> " + connection.toRef + "." + connection.toPort);
            connected++;
        }
        assertEquals(preset.connections.size(), connected, "all preset connections must materialize");
        return new MaterializedGraph(graph, byRef);
    }

    private static void seedPlayerPosition(INode playerPos, double x, double y, double z) throws Exception {
        Method setter = playerPos.getClass().getDeclaredMethod("setCachedPosition", double.class, double.class, double.class);
        setter.setAccessible(true);
        setter.invoke(playerPos, x, y, z);
    }

    private record MaterializedGraph(NodeGraph graph, Map<String, INode> byRef) {
    }
}
