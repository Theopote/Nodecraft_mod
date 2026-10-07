package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.BeamGridNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.BeamAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.ColumnGridNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.ColumnNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.DoorArrayNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.FacadePanelArrayNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.FloorSlabNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RailingNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofBaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofGeneratorNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.StaircaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallSlabNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowFrameNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 13 freeze: Architectural Components language (Wall / Floor / Roof / Stair / Window Array).
 */
class ArchitecturalFamilyContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void coreFiveUseTypedFootprintAndPathPorts() {
        assertPortType(new WallWithOpeningsNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new FloorSlabNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new BeamGridNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new RoofBaseNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new RoofGeneratorNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new WindowArrayNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new StaircaseNode(), "input_path", NodeDataType.PATH);
        assertPortType(new RailingNode(), "input_path", NodeDataType.PATH);
    }

    @Test
    void coreFiveEmitGeometryAndValid() {
        assertPortType(new WallWithOpeningsNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new WallWithOpeningsNode(), "output_openings", NodeDataType.GEOMETRY);
        assertPortType(new WallWithOpeningsNode(), "output_top_edge", NodeDataType.PATH);
        assertPortType(new FloorSlabNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new FloorSlabNode(), "output_top_face", NodeDataType.BOX_FACE);
        assertPortType(new BeamGridNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new RoofBaseNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new RoofBaseNode(), "output_eave_path", NodeDataType.PATH);
        assertPortType(new RoofGeneratorNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new RoofGeneratorNode(), "output_eave_path", NodeDataType.PATH);
        assertPortType(new WindowArrayNode(), "output_openings", NodeDataType.GEOMETRY);
        assertPortType(new DoorArrayNode(), "output_openings", NodeDataType.GEOMETRY);
        assertPortType(new StaircaseNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new WallWithOpeningsNode(), "output_valid", NodeDataType.BOOLEAN);
        assertPortType(new FloorSlabNode(), "output_valid", NodeDataType.BOOLEAN);
        assertPortType(new BeamGridNode(), "output_valid", NodeDataType.BOOLEAN);
        assertPortType(new RoofBaseNode(), "output_valid", NodeDataType.BOOLEAN);
        assertPortType(new RoofGeneratorNode(), "output_valid", NodeDataType.BOOLEAN);
        assertPortType(new WindowArrayNode(), "output_valid", NodeDataType.BOOLEAN);
        assertPortType(new StaircaseNode(), "output_valid", NodeDataType.BOOLEAN);
    }

    @Test
    void floorAndRoofSplitNodesAreComposable() {
        assertNotNull(registry.createNodeInstance("geometry.architectural_primitives.floor_slab"));
        assertNotNull(registry.createNodeInstance("geometry.architectural_primitives.beam_grid"));
        assertNotNull(registry.createNodeInstance("geometry.architectural_primitives.roof_base"));
        assertPortType(new BeamGridNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new BeamGridNode(), "output_frames", NodeDataType.FRAME_LIST);
        assertPortType(new BeamGridNode(), "output_center_lines", NodeDataType.PATH_LIST);
        assertPortType(new RoofBaseNode(), "output_ridge_path", NodeDataType.PATH);
        assertPortType(new RoofGeneratorNode(), "output_ridge_path", NodeDataType.PATH);
    }

    @Test
    void wallSlabIsFaceHostWithoutOpenings() {
        assertNotNull(registry.createNodeInstance("geometry.architectural_primitives.wall_slab"));
        assertPortType(new WallSlabNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new WallSlabNode(), "input_wall_thickness", NodeDataType.DOUBLE);
        assertPortType(new WallSlabNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new WallSlabNode(), "output_exterior_face", NodeDataType.BOX_FACE);
        assertPortType(new WallSlabNode(), "output_interior_face", NodeDataType.BOX_FACE);
        WallSlabNode slab = new WallSlabNode();
        assertTrue(allPorts(slab).stream().noneMatch(p -> "output_openings".equals(p.getId())));
        assertTrue(allPorts(slab).stream().noneMatch(p -> "input_columns".equals(p.getId())));
    }

    @Test
    void pathElementsAndColumnAreRegistered() {
        assertNotNull(registry.createNodeInstance("geometry.architectural_primitives.wall_along_path"));
        assertNotNull(registry.createNodeInstance("geometry.architectural_primitives.beam_along_path"));
        assertNotNull(registry.createNodeInstance("geometry.architectural_primitives.column"));
        assertPortType(new WallAlongPathNode(), "input_path", NodeDataType.PATH);
        assertPortType(new BeamAlongPathNode(), "input_path", NodeDataType.PATH);
        assertPortType(new ColumnNode(), "input_frame", NodeDataType.FRAME);
        assertPortType(new ColumnNode(), "input_base", NodeDataType.POINT);
        assertPortType(new WallAlongPathNode(), "output_frames", NodeDataType.FRAME_LIST);
        assertPortType(new BeamAlongPathNode(), "output_frames", NodeDataType.FRAME_LIST);
    }

    @Test
    void windowFrameIsRegisteredWithGeometryOutput() {
        assertNotNull(registry.createNodeInstance("geometry.architectural_primitives.window_frame"));
        assertPortType(new WindowFrameNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new WindowFrameNode(), "output_valid", NodeDataType.BOOLEAN);
    }

    @Test
    void faceArrayFamilyEmitsFramesCentersAndOneBasedGridIndices() {
        assertPortType(new WindowArrayNode(), "output_frames", NodeDataType.FRAME_LIST);
        assertPortType(new WindowArrayNode(), "output_centers", NodeDataType.POINT_LIST);
        assertPortType(new WindowArrayNode(), "output_row_indices", NodeDataType.INTEGER_LIST);
        assertPortType(new WindowArrayNode(), "output_column_indices", NodeDataType.INTEGER_LIST);
        assertPortType(new DoorArrayNode(), "output_frames", NodeDataType.FRAME_LIST);
        assertPortType(new DoorArrayNode(), "output_centers", NodeDataType.POINT_LIST);
        assertPortType(new DoorArrayNode(), "output_row_indices", NodeDataType.INTEGER_LIST);
        assertPortType(new DoorArrayNode(), "output_column_indices", NodeDataType.INTEGER_LIST);
        assertPortType(new FacadePanelArrayNode(), "output_frames", NodeDataType.FRAME_LIST);
        assertPortType(new FacadePanelArrayNode(), "output_centers", NodeDataType.POINT_LIST);
        assertPortType(new ColumnGridNode(), "output_frames", NodeDataType.FRAME_LIST);
        assertPortType(new ColumnGridNode(), "output_base_points", NodeDataType.POINT_LIST);
        assertPortType(new ColumnGridNode(), "output_top_points", NodeDataType.POINT_LIST);
        assertPortType(new ColumnGridNode(), "output_row_indices", NodeDataType.INTEGER_LIST);
        assertPortType(new ColumnGridNode(), "output_column_indices", NodeDataType.INTEGER_LIST);
    }

    @Test
    void staircaseEmitsWalkPathAndLandingPorts() {
        assertPortType(new StaircaseNode(), "output_step_frames", NodeDataType.FRAME_LIST);
        assertPortType(new StaircaseNode(), "output_walk_path", NodeDataType.PATH);
        assertPortType(new StaircaseNode(), "output_total_rise", NodeDataType.DOUBLE);
        assertPortType(new StaircaseNode(), "output_total_run", NodeDataType.DOUBLE);
        assertPortType(new StaircaseNode(), "output_landing_geometry", NodeDataType.GEOMETRY);
        assertPortType(new StaircaseNode(), "output_landing_frames", NodeDataType.FRAME_LIST);
    }

    @Test
    void roofNodesExposeFacesAndSlopeDirections() {
        assertPortType(new RoofBaseNode(), "output_faces", NodeDataType.PLANAR_REGION_LIST);
        assertPortType(new RoofBaseNode(), "output_slope_directions", NodeDataType.VECTOR_LIST);
        assertPortType(new RoofGeneratorNode(), "output_faces", NodeDataType.PLANAR_REGION_LIST);
        assertPortType(new RoofGeneratorNode(), "output_slope_directions", NodeDataType.VECTOR_LIST);
    }

    @Test
    void staircaseSpiralStartAngleIsDegreesDouble() {
        IPort angle = findPort(new StaircaseNode(), "input_spiral_start_angle");
        assertEquals(NodeDataType.DOUBLE, angle.getDataType());
        assertTrue(angle.getDescription().toLowerCase(Locale.ROOT).contains("degrees"));
    }

    @Test
    void architecturalFamilyForbidsAnyAndLegacyLinePorts() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("geometry.architectural_primitives.")) {
                continue;
            }
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            for (IPort port : allPorts(instance)) {
                if (port.getDataType() == NodeDataType.ANY) {
                    violations.add(nodeId + "#" + port.getId() + "=ANY");
                }
                String id = port.getId().toLowerCase(Locale.ROOT);
                if ("input_line".equals(id) || port.getDataType() == NodeDataType.LINE) {
                    violations.add(nodeId + "#" + port.getId() + "=LINE");
                }
            }
        }
        assertTrue(violations.isEmpty(), "Architectural language violations: " + violations);
    }

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        assertEquals(expected, findPort(node, portId).getDataType(), node.getTypeId() + "#" + portId);
    }

    private static List<IPort> allPorts(INode node) {
        List<IPort> ports = new ArrayList<>();
        ports.addAll(node.getInputPorts());
        ports.addAll(node.getOutputPorts());
        return ports;
    }

    private static IPort findPort(INode node, String portId) {
        for (IPort port : allPorts(node)) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        throw new AssertionError("missing port " + portId + " on " + node.getTypeId());
    }

    private static INode tryCreate(String nodeId) {
        try {
            return registry.createNodeInstance(nodeId);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }
}
