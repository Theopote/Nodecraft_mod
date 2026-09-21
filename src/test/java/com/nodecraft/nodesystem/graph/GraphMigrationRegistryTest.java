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
    void v3PathLanguageMigratesTypeIdsAndPorts() {
        SavedGraph v3 = new SavedGraph();
        v3.formatVersion = GraphFormatVersion.V3;
        SavedNode pointsToPath = new SavedNode();
        pointsToPath.nodeId = "ptp";
        pointsToPath.typeId = "geometry.curves.curve_from_points";
        SavedNode pathToPoints = new SavedNode();
        pathToPoints.nodeId = "ptp2";
        pathToPoints.typeId = "geometry.curves.divide_curve_to_points";
        SavedNode sweep = new SavedNode();
        sweep.nodeId = "sweep";
        sweep.typeId = "geometry.solids.sweep";
        SavedNode railing = new SavedNode();
        railing.nodeId = "rail";
        railing.typeId = "geometry.architectural_primitives.railing";
        SavedNode staircase = new SavedNode();
        staircase.nodeId = "stair";
        staircase.typeId = "geometry.architectural_primitives.staircase";
        SavedNode unknownLineOnly = new SavedNode();
        unknownLineOnly.nodeId = "wall";
        unknownLineOnly.typeId = "geometry.architectural_primitives.wall_from_line";
        v3.nodes = List.of(pointsToPath, pathToPoints, sweep, railing, staircase, unknownLineOnly);

        SavedConnection pathConn = new SavedConnection();
        pathConn.sourceNodeId = "ptp";
        pathConn.sourcePortId = "output_path";
        pathConn.targetNodeId = "sweep";
        pathConn.targetPortId = "input_curve";

        SavedConnection pathLineConn = new SavedConnection();
        pathLineConn.sourceNodeId = "ptp";
        pathLineConn.sourcePortId = "output_line";
        pathLineConn.targetNodeId = "ptp2";
        pathLineConn.targetPortId = "input_line";

        SavedConnection railConn = new SavedConnection();
        railConn.sourceNodeId = "ptp";
        railConn.sourcePortId = "output_line";
        railConn.targetNodeId = "rail";
        railConn.targetPortId = "input_line";

        SavedConnection stairConn = new SavedConnection();
        stairConn.sourceNodeId = "ptp";
        stairConn.sourcePortId = "output_line";
        stairConn.targetNodeId = "stair";
        stairConn.targetPortId = "input_line";

        SavedConnection unknownConn = new SavedConnection();
        unknownConn.sourceNodeId = "ptp";
        unknownConn.sourcePortId = "output_line";
        unknownConn.targetNodeId = "wall";
        unknownConn.targetPortId = "input_line";

        v3.connections = List.of(pathConn, pathLineConn, railConn, stairConn, unknownConn);
        v3.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v3);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("geometry.curves.points_to_path", migrated.nodes.get(0).typeId);
        assertEquals("geometry.curves.path_to_points", migrated.nodes.get(1).typeId);
        assertEquals("input_path", migrated.connections.get(0).targetPortId);
        assertEquals("input_path", migrated.connections.get(1).targetPortId);
        assertEquals("input_line", migrated.connections.get(2).targetPortId);
        assertEquals("input_line", migrated.connections.get(3).targetPortId);
        assertEquals("input_line", migrated.connections.get(4).targetPortId);
    }

    @Test
    void v4SolidsLanguageMigratesExtrudeAndLatticeIds() {
        SavedGraph v4 = new SavedGraph();
        v4.formatVersion = GraphFormatVersion.V4;
        SavedNode prism = new SavedNode();
        prism.nodeId = "prism";
        prism.typeId = "geometry.solids.extrude_profile";
        SavedNode lattice = new SavedNode();
        lattice.nodeId = "lat";
        lattice.typeId = "geometry.solids.surface_strip_to_geometry";
        SavedNode sink = new SavedNode();
        sink.nodeId = "sink";
        sink.typeId = "geometry.boolean.union";
        v4.nodes = List.of(prism, lattice, sink);

        SavedConnection extrusion = new SavedConnection();
        extrusion.sourceNodeId = "prism";
        extrusion.sourcePortId = "output_geometry";
        extrusion.targetNodeId = "sink";
        extrusion.targetPortId = "input_geometry_0";

        SavedConnection surface = new SavedConnection();
        surface.sourceNodeId = "prism";
        surface.sourcePortId = "output_surface_strip";
        surface.targetNodeId = "lat";
        surface.targetPortId = "input_surface_strip";

        SavedConnection direction = new SavedConnection();
        direction.sourceNodeId = "sink";
        direction.sourcePortId = "output_geometry";
        direction.targetNodeId = "prism";
        direction.targetPortId = "input_extrusion_vector";

        v4.connections = List.of(extrusion, surface, direction);
        v4.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v4);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("geometry.solids.extrude", migrated.nodes.get(0).typeId);
        assertEquals("geometry.solids.surface_strip_to_lattice", migrated.nodes.get(1).typeId);
        assertEquals("geometry.combine.geometry", migrated.nodes.get(2).typeId);
        assertEquals("output_side_surface", migrated.connections.get(1).sourcePortId);
        assertEquals("input_direction", migrated.connections.get(2).targetPortId);
    }

    @Test
    void v5CombineGeometryMigratesBooleanUnionId() {
        SavedGraph v5 = new SavedGraph();
        v5.formatVersion = GraphFormatVersion.V5;
        SavedNode combine = new SavedNode();
        combine.nodeId = "c";
        combine.typeId = "geometry.boolean.union";
        v5.nodes = List.of(combine);
        v5.connections = List.of();
        v5.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v5);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("geometry.combine.geometry", migrated.nodes.getFirst().typeId);
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
