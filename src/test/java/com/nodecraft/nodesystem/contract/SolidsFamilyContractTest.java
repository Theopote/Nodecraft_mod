package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for Batch 4 solids language: Extrude canonical, Sweep/Loft as surface,
 * Surface Strip To Lattice naming.
 */
class SolidsFamilyContractTest {

    private static final Set<String> SURFACE_STRIP_PRIMARY_IDS = Set.of(
            "geometry.solids.sweep",
            "geometry.solids.sweep_from_points",
            "geometry.solids.loft",
            "geometry.solids.loft_multi_section"
    );

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void extrudeIsCanonicalPlayerSolid() {
        assertPortType("geometry.solids.extrude", "input_profile", true, NodeDataType.POLYGON_PROFILE);
        assertPortType("geometry.solids.extrude", "input_direction", true, NodeDataType.VECTOR);
        assertPortType("geometry.solids.extrude", "output_geometry", false, NodeDataType.GEOMETRY);
        assertPortType("geometry.solids.extrude", "output_prism", false, NodeDataType.PRISM_GEOMETRY);
        assertPortType("geometry.solids.extrude", "output_side_surface", false, NodeDataType.SURFACE_STRIP);
        assertPortType("geometry.solids.extrude", "output_valid", false, NodeDataType.BOOLEAN);

        INode extrude = NodeRegistry.getInstance().createNodeInstance("geometry.solids.extrude");
        assertInstanceOf(INode.class, extrude);
        assertEquals("Extrude", extrude.getDisplayName());
    }

    @Test
    void prismByProfileVectorIsLegacyAdvanced() {
        INode prism = NodeRegistry.getInstance().createNodeInstance("geometry.solids.extrude_profile");
        assertInstanceOf(INode.class, prism);
        assertTrue(prism.getDescription().toLowerCase().contains("legacy")
                || prism.getDescription().toLowerCase().contains("prefer extrude"));
    }

    @Test
    void surfaceStripToLatticeIsHonestAboutApproximation() {
        assertPortType("geometry.solids.surface_strip_to_lattice", "input_surface_strip", true, NodeDataType.SURFACE_STRIP);
        assertPortType("geometry.solids.surface_strip_to_lattice", "output_geometry", false, NodeDataType.GEOMETRY);

        INode lattice = NodeRegistry.getInstance().createNodeInstance("geometry.solids.surface_strip_to_lattice");
        assertInstanceOf(INode.class, lattice);
        assertEquals("Surface Strip To Lattice", lattice.getDisplayName());
        assertTrue(lattice.getDescription().toLowerCase().contains("lattice")
                || lattice.getDescription().toLowerCase().contains("not a filled"));

        IPort geometryOut = lattice.getOutputPorts().stream()
                .filter(port -> port.getId().equals("output_geometry"))
                .findFirst()
                .orElseThrow();
        assertTrue(geometryOut.getDisplayName().toLowerCase().contains("lattice"));
    }

    @Test
    void legacySurfaceStripToGeometryIdIsGone() {
        assertEquals(null, NodeRegistry.getInstance().getNodeInfo("geometry.solids.surface_strip_to_geometry"));
    }

    @Test
    void sweepAndLoftExposeSurfaceStripNotGeometry() {
        List<String> errors = new ArrayList<>();
        for (String typeId : SURFACE_STRIP_PRIMARY_IDS) {
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            if (node == null) {
                errors.add("missing instance: " + typeId);
                continue;
            }
            boolean hasSurface = node.getOutputPorts().stream()
                    .anyMatch(port -> port.getDataType() == NodeDataType.SURFACE_STRIP);
            boolean hasGeometry = node.getOutputPorts().stream()
                    .anyMatch(port -> port.getDataType() == NodeDataType.GEOMETRY
                            || port.getDataType() == NodeDataType.PRISM_GEOMETRY);
            if (!hasSurface) {
                errors.add(typeId + " missing SURFACE_STRIP output");
            }
            if (hasGeometry) {
                errors.add(typeId + " must not pretend to emit solid GEOMETRY/PRISM yet");
            }
            String desc = node.getDescription() == null ? "" : node.getDescription().toLowerCase();
            if (!desc.contains("surface") && !desc.contains("surface_strip")) {
                errors.add(typeId + " description should state surface (not solid) semantics");
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void sweepUsesPathInputOnly() {
        assertPortType("geometry.solids.sweep", "input_path", true, NodeDataType.PATH);
        assertPortType("geometry.solids.sweep", "input_profile", true, NodeDataType.POLYGON_PROFILE);
        assertFalse(hasInputPort("geometry.solids.sweep", "input_curve"));
        assertFalse(hasInputPort("geometry.solids.sweep", "input_path_points"));
        assertFalse(hasInputPort("geometry.solids.sweep_from_points", "input_path_points"));
        assertFalse(hasInputPort("geometry.solids.sweep_two_rails", "input_rail_a_points"));
        assertFalse(hasInputPort("geometry.solids.sweep_two_rails", "input_rail_b_points"));
        assertPortType("geometry.solids.sweep_two_rails", "input_rail_a_path", true, NodeDataType.PATH);
        assertPortType("geometry.solids.sweep_two_rails", "input_rail_b_path", true, NodeDataType.PATH);
    }

    @Test
    void loftUsesProfileListAndAutoResampleDefaults() {
        assertPortType("geometry.solids.loft_multi_section", "input_profiles", true, NodeDataType.POLYGON_PROFILE_LIST);
        assertPortType("geometry.solids.loft_multi_section", "output_profiles", false, NodeDataType.POLYGON_PROFILE_LIST);

        INode loft = NodeRegistry.getInstance().createNodeInstance("geometry.solids.loft");
        assertInstanceOf(INode.class, loft);
        assertTrue(loft.getDescription().toLowerCase().contains("auto-resample")
                || loft.getDescription().toLowerCase().contains("auto resample")
                || loft.getDescription().toLowerCase().contains("resample"));

        INode multi = NodeRegistry.getInstance().createNodeInstance("geometry.solids.loft_multi_section");
        assertInstanceOf(com.nodecraft.nodesystem.nodes.geometry.solids.MultiSectionLoftNode.class, multi);
        assertTrue(((com.nodecraft.nodesystem.nodes.geometry.solids.MultiSectionLoftNode) multi).isAutoResample());
        assertTrue(((com.nodecraft.nodesystem.nodes.geometry.solids.LoftProfilesNode) loft).isAutoResample());
    }

    @Test
    void sweepSectionProfilesUseProfileList() {
        assertPortType("geometry.solids.sweep", "output_section_profiles", false, NodeDataType.POLYGON_PROFILE_LIST);
    }

    private static boolean hasInputPort(String typeId, String portId) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        assertInstanceOf(INode.class, node);
        return node.getInputPorts().stream().anyMatch(port -> port.getId().equals(portId));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        assertInstanceOf(INode.class, node);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .filter(candidate -> candidate.getId().equals(portId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }
}
