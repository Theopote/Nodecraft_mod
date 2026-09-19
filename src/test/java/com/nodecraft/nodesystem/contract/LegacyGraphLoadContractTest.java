package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.graph.GraphLoadResult;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for V0 legacy {@code .nodecraft} payloads.
 */
class LegacyGraphLoadContractTest {

    @BeforeAll
    static void ensureRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "legacy/v0/basic_box_v0.nodecraft",
            "legacy/v0/math_scalar_v0.nodecraft",
            "legacy/v0/world_write_v0.nodecraft"
    })
    void legacyFixturesLoadWithoutSkippingKnownNodes(String resourcePath) throws IOException {
        SavedGraph savedGraph = loadFixture(resourcePath);
        assertEquals(GraphFormatVersion.V0, savedGraph.formatVersion);

        GraphLoadResult result = GraphSerializer.loadFromSavedGraph(savedGraph);
        assertNotNull(result.graph());
        assertEquals(GraphFormatVersion.CURRENT, savedGraph.formatVersion);
        assertTrue(result.skippedUnknownNodeTypes() == 0, "expected zero skipped nodes for " + resourcePath);
        assertEquals(savedGraph.nodes.size(), result.graph().getNodes().size());
    }

    private static SavedGraph loadFixture(String resourcePath) throws IOException {
        try (InputStream stream = LegacyGraphLoadContractTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "missing fixture: " + resourcePath);
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            SavedGraph savedGraph = GraphSerializer.fromJson(json);
            assertNotNull(savedGraph);
            return savedGraph;
        }
    }
}
