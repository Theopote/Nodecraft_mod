package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphMigrationRegistryTest {

    @Test
    void manifestLoadsWithNodeTypeAliases() {
        GraphMigrationManifest manifest = GraphMigrationManifest.loadUncheckedForTests();
        assertTrue(manifest.manifestVersion() >= 1);
        assertFalse(manifest.nodeTypeAliases().isEmpty());
        assertEquals(
                "input.context.player_position",
                manifest.migrateNodeTypeId("inputs.minecraft.player_position")
        );
    }

    @Test
    void legacyGraphIsMigratedToCurrentVersion() {
        SavedGraph legacy = new SavedGraph();
        legacy.graphName = "legacy";
        legacy.formatVersion = GraphFormatVersion.V0;
        SavedNode node = new SavedNode();
        node.nodeId = "node-1";
        node.typeId = "VISUALIZATION.PREVIEW.PREVIEW_BLOCKS";
        legacy.nodes = List.of(node);
        legacy.connections = List.of();
        legacy.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(legacy);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("output.preview.preview_blocks", migrated.nodes.getFirst().typeId);
    }

    @Test
    void legacyPortsAreMigratedUsingManifestRules() {
        SavedGraph legacy = new SavedGraph();
        legacy.formatVersion = GraphFormatVersion.V0;
        SavedNode player = new SavedNode();
        player.nodeId = "player";
        player.typeId = "inputs.minecraft.player_position";
        SavedNode box = new SavedNode();
        box.nodeId = "box";
        box.typeId = "spatial.construct.box_center_size";
        legacy.nodes = List.of(player, box);

        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = "player";
        connection.sourcePortId = "position";
        connection.targetNodeId = "box";
        connection.targetPortId = "center";
        legacy.connections = List.of(connection);
        legacy.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(legacy);
        SavedConnection migratedConnection = migrated.connections.getFirst();
        assertEquals("output_position", migratedConnection.sourcePortId);
        assertEquals("input_center", migratedConnection.targetPortId);
    }

    @Test
    void v1IntegerSliderValuePortMigratesToOutputValue() {
        SavedGraph v1 = new SavedGraph();
        v1.formatVersion = GraphFormatVersion.V1;
        SavedNode slider = new SavedNode();
        slider.nodeId = "slider";
        slider.typeId = "input.numeric.integer_slider";
        SavedNode sink = new SavedNode();
        sink.nodeId = "sink";
        sink.typeId = "math.scalar_math.addition";
        v1.nodes = List.of(slider, sink);

        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = "slider";
        connection.sourcePortId = "value";
        connection.targetNodeId = "sink";
        connection.targetPortId = "input_a";
        v1.connections = List.of(connection);
        v1.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v1);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("output_value", migrated.connections.getFirst().sourcePortId);
        assertEquals("input_a", migrated.connections.getFirst().targetPortId);
    }

    @Test
    void v2CoordinateInputTypeMigratesToBlockPosition() {
        SavedGraph v2 = new SavedGraph();
        v2.formatVersion = GraphFormatVersion.V2;
        SavedNode coordinate = new SavedNode();
        coordinate.nodeId = "coord";
        coordinate.typeId = "reference.points.point_from_coordinates";
        v2.nodes = List.of(coordinate);
        v2.connections = List.of();
        v2.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v2);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("reference.points.block_position", migrated.nodes.getFirst().typeId);
    }

    @Test
    void v3AngleSliderRadiansInsertsDegreesToRadians() {
        SavedGraph v3 = new SavedGraph();
        v3.formatVersion = GraphFormatVersion.V3;

        SavedNode angle = new SavedNode();
        angle.nodeId = "angle";
        angle.typeId = "input.numeric.angle";
        angle.state = new java.util.LinkedHashMap<>(java.util.Map.of(
                "angle", 90.0,
                "unit", "RADIANS",
                "minAngle", 0.0,
                "maxAngle", 360.0
        ));

        SavedNode sink = new SavedNode();
        sink.nodeId = "sink";
        sink.typeId = "math.scalar_math.addition";
        v3.nodes = new java.util.ArrayList<>(java.util.List.of(angle, sink));

        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = "angle";
        connection.sourcePortId = "output_angle";
        connection.targetNodeId = "sink";
        connection.targetPortId = "input_a";
        v3.connections = new java.util.ArrayList<>(java.util.List.of(connection));
        v3.nodePositions = new java.util.HashMap<>(java.util.Map.of(
                "angle", new com.nodecraft.nodesystem.io.SavedPosition(10.0f, 20.0f)
        ));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v3);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals(3, migrated.nodes.size());

        SavedNode converter = migrated.nodes.stream()
                .filter(n -> "math.trigonometry.deg_to_rad".equals(n.typeId))
                .findFirst()
                .orElse(null);
        assertNotNull(converter);
        assertNotNull(converter.nodeId);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> angleState = (java.util.Map<String, Object>) migrated.nodes.stream()
                .filter(n -> "angle".equals(n.nodeId))
                .findFirst()
                .orElseThrow()
                .state;
        assertFalse(angleState.containsKey("unit"));
        assertEquals(90.0, ((Number) angleState.get("angle")).doubleValue(), 1e-9);

        assertEquals(2, migrated.connections.size());
        boolean sliderToConverter = migrated.connections.stream().anyMatch(c ->
                "angle".equals(c.sourceNodeId)
                        && "output_angle".equals(c.sourcePortId)
                        && converter.nodeId.equals(c.targetNodeId)
                        && "input_degrees".equals(c.targetPortId));
        boolean converterToSink = migrated.connections.stream().anyMatch(c ->
                converter.nodeId.equals(c.sourceNodeId)
                        && "output_radians".equals(c.sourcePortId)
                        && "sink".equals(c.targetNodeId)
                        && "input_a".equals(c.targetPortId));
        assertTrue(sliderToConverter);
        assertTrue(converterToSink);
        assertTrue(migrated.nodePositions.containsKey(converter.nodeId));
    }

    @Test
    void v3AngleSliderDegreesDoesNotInsertConverter() {
        SavedGraph v3 = new SavedGraph();
        v3.formatVersion = GraphFormatVersion.V3;
        SavedNode angle = new SavedNode();
        angle.nodeId = "angle";
        angle.typeId = "input.numeric.angle";
        angle.state = new java.util.LinkedHashMap<>(java.util.Map.of(
                "angle", 45.0,
                "unit", "DEGREES"
        ));
        v3.nodes = java.util.List.of(angle);
        v3.connections = java.util.List.of();
        v3.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v3);
        assertEquals(1, migrated.nodes.size());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> state = (java.util.Map<String, Object>) migrated.nodes.getFirst().state;
        assertFalse(state.containsKey("unit"));
    }

    @Test
    void futureVersionsAreLeftUntouched() {
        SavedGraph future = new SavedGraph();
        future.formatVersion = GraphFormatVersion.CURRENT + 5;
        future.graphName = "future";

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(future);
        assertEquals(GraphFormatVersion.CURRENT + 5, migrated.formatVersion);
        assertEquals("future", migrated.graphName);
    }
}
