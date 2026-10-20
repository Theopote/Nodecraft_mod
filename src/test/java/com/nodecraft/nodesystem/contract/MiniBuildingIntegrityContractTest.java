package com.nodecraft.nodesystem.contract;

import com.google.gson.Gson;
import com.nodecraft.gui.preset.GraphPresetRules;
import com.nodecraft.gui.preset.GraphPresetTestResources;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.contract.support.MiniBuildingIntegrityAssert;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionRunLimits;
import com.nodecraft.nodesystem.execution.IncrementalExecutionOptions;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.execution.runtime.CancellationToken;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockStateResolver;
import com.nodecraft.nodesystem.util.PlacementSubmitPlan;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Assumptions;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6D: Mini Building spatial integrity beyond non-empty / Y-band presence checks.
 */
class MiniBuildingIntegrityContractTest {

    private static final String PRESET_ID = "architectural.residential.mini_building_v1";
    private static final Gson GSON = new Gson();
    private static final Vector3d PLAYER_ORIGIN = new Vector3d(0.5d, 0.0d, 0.5d);

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void miniBuildingPlacementSetHasFloorRoofWallsOpeningsAndFrames() throws Exception {
        Assumptions.assumeTrue(BlockStateResolver.isRegistryReady(), "Live BLOCK registry required");

        GraphPresetRules.GraphPresetDefinition preset = requirePreset(PRESET_ID);
        MaterializedGraph material = materialize(preset);
        seedPlayerPosition(material.byRef.get("player_pos"), PLAYER_ORIGIN.x, PLAYER_ORIGIN.y, PLAYER_ORIGIN.z);

        ExecutionContext context = new ExecutionContext(null, null);
        NodeExecutor executor = new NodeExecutor(
                material.graph, context, null,
                IncrementalExecutionOptions.defaults(), ExecutionRunLimits.defaults(),
                CancellationToken.none(), true, null, true);
        assertTrue(executor.executeSync(), "mini_building_v1 must execute");

        Object placementsObj = material.byRef.get("material").getOutput("output_placements");
        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                placementsObj, null, null, null, null, null, null, null, "minecraft:stone_bricks", true);
        assertTrue(plan.valid(), plan.error());

        Map<BlockPos, BlockState> solidMap = plan.toPositionMap();
        Set<BlockPos> solid = solidMap.keySet();
        MiniBuildingIntegrityAssert.Bounds bounds = MiniBuildingIntegrityAssert.boundsOf(solid);

        MiniBuildingIntegrityAssert.assertFloorContinuous(solid, bounds);
        MiniBuildingIntegrityAssert.assertRoofCoverage(solid, bounds);
        MiniBuildingIntegrityAssert.assertWallsAndCorners(solid, bounds);
        MiniBuildingIntegrityAssert.assertDominantMaterial(solidMap, Blocks.STONE_BRICKS, 0.85d);

        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) material.byRef.get("windows").getOutput("output_centers");
        Vector3d faceNormal = ((BoxFaceData) material.byRef.get("front_face").getOutput("output_face")).getNormal();
        MiniBuildingIntegrityAssert.assertOpeningsAndPiers(solid, centers, PLAYER_ORIGIN, faceNormal);

        Object frameCountObj = material.byRef.get("place_frames").getOutput("output_count");
        int frameCount = frameCountObj instanceof Number n ? n.intValue() : -1;
        MiniBuildingIntegrityAssert.assertFramesAroundOpenings(solid, centers, PLAYER_ORIGIN, frameCount);
    }

    private static GraphPresetRules.GraphPresetDefinition requirePreset(String presetId) {
        try (InputStream stream = MiniBuildingIntegrityContractTest.class
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
            INode node = registry.createNodeInstance(presetNode.typeId);
            assertNotNull(node, "unknown type " + presetNode.typeId);
            if (presetNode.state != null) {
                node.setNodeState(presetNode.state);
            }
            assertTrue(graph.addNode(node));
            byRef.put(presetNode.ref, node);
            idByRef.put(presetNode.ref, node.getId());
        }
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            assertTrue(graph.connect(
                    idByRef.get(connection.fromRef), connection.fromPort,
                    idByRef.get(connection.toRef), connection.toPort));
        }
        return new MaterializedGraph(graph, byRef);
    }

    private static void seedPlayerPosition(INode playerPos, double x, double y, double z) throws Exception {
        Method setter = playerPos.getClass().getDeclaredMethod(
                "setCachedPosition", double.class, double.class, double.class);
        setter.setAccessible(true);
        setter.invoke(playerPos, x, y, z);
    }

    private record MaterializedGraph(NodeGraph graph, Map<String, INode> byRef) {
    }
}
