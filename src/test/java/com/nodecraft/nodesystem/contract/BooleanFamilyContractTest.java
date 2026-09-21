package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewGeometryNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for Batch 5 geometry ops language:
 * Structural Combine / Deferred Voxel Boolean / SDF Boolean (separate).
 */
class BooleanFamilyContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void combineGeometryIsStructuralNotBooleanUnion() {
        assertEquals(null, NodeRegistry.getInstance().getNodeInfo("geometry.boolean.union"));

        INode combine = NodeRegistry.getInstance().createNodeInstance("geometry.combine.geometry");
        assertInstanceOf(INode.class, combine);
        assertEquals("Combine Geometry", combine.getDisplayName());
        String desc = combine.getDescription().toLowerCase();
        assertTrue(desc.contains("structural") || desc.contains("composite") || desc.contains("grouping"));
        assertTrue(desc.contains("not"));
        assertTrue(desc.contains("analytic") || desc.contains("brep") || desc.contains("sdf"));
    }

    @Test
    void differenceAndIntersectionAreDeferredVoxelBoolean() {
        INode difference = NodeRegistry.getInstance().createNodeInstance("geometry.boolean.difference");
        INode intersection = NodeRegistry.getInstance().createNodeInstance("geometry.boolean.intersection");
        assertInstanceOf(INode.class, difference);
        assertInstanceOf(INode.class, intersection);

        String diffDesc = difference.getDescription().toLowerCase();
        String interDesc = intersection.getDescription().toLowerCase();
        assertTrue(diffDesc.contains("voxel") || diffDesc.contains("block grid") || diffDesc.contains("voxelized"));
        assertTrue(interDesc.contains("voxel") || interDesc.contains("block grid") || interDesc.contains("voxelized"));
        assertTrue(diffDesc.contains("subtract") || diffDesc.contains("cutter"));
        assertTrue(interDesc.contains("overlap") || interDesc.contains("intersect"));
    }

    @Test
    void sdfBooleanStaysOnSdfPorts() {
        assertPortType("geometry.boolean.sdf_boolean", "input_a", true, NodeDataType.SDF);
        assertPortType("geometry.boolean.sdf_boolean", "input_b", true, NodeDataType.SDF);
        assertPortType("geometry.boolean.sdf_boolean", "output_sdf", false, NodeDataType.SDF);

        assertEquals(
            TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
            TypeConversionRegistry.classify(NodeDataType.GEOMETRY, NodeDataType.SDF)
        );
        assertEquals(
            TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
            TypeConversionRegistry.classify(NodeDataType.SDF, NodeDataType.GEOMETRY)
        );
    }

    @Test
    void previewGeometryForwardsDifferenceWithoutExpandingOperands() {
        PreviewGeometryNode preview = (PreviewGeometryNode) NodeRegistry.getInstance()
            .createNodeInstance("output.preview.preview_geometry");
        assertNotNull(preview);
        preview.setNodeState(Map.of("previewEnabled", false));

        BoxGeometryData box = new BoxGeometryData(new Vector3d(2, 2, 2), new Vector3d(2, 2, 2));
        SphereData sphere = new SphereData(new Vector3d(2, 2, 2), 2.0d);
        DifferenceGeometryData difference = new DifferenceGeometryData(box, sphere);

        preview.setInput("input_geometry", difference);
        preview.processNode(null);

        Object forwarded = preview.getOutput("output_geometry");
        assertInstanceOf(DifferenceGeometryData.class, forwarded);
        assertFalse(forwarded instanceof CompositeGeometryData);
        DifferenceGeometryData out = (DifferenceGeometryData) forwarded;
        assertEquals(box, out.getMinuend());
        assertEquals(sphere, out.getSubtrahend());
    }

    @Test
    void previewGeometryForwardsIntersectionWithoutExpandingOperands() {
        PreviewGeometryNode preview = (PreviewGeometryNode) NodeRegistry.getInstance()
            .createNodeInstance("output.preview.preview_geometry");
        assertNotNull(preview);
        preview.setNodeState(Map.of("previewEnabled", false));

        BoxGeometryData left = new BoxGeometryData(new Vector3d(2, 2, 2), new Vector3d(2, 2, 2));
        BoxGeometryData right = new BoxGeometryData(new Vector3d(3, 3, 3), new Vector3d(2, 2, 2));
        IntersectionGeometryData intersection = new IntersectionGeometryData(left, right);

        preview.setInput("input_geometry", intersection);
        preview.processNode(null);

        Object forwarded = preview.getOutput("output_geometry");
        assertInstanceOf(IntersectionGeometryData.class, forwarded);
    }

    @Test
    void combineProducesCompositeGeometryData() {
        BaseNode combine = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.combine.geometry");
        assertNotNull(combine);
        BoxGeometryData a = new BoxGeometryData(new Vector3d(1, 1, 1), new Vector3d(1, 1, 1));
        BoxGeometryData b = new BoxGeometryData(new Vector3d(4, 1, 1), new Vector3d(1, 1, 1));
        combine.setInput("input_geometry_0", a);
        combine.setInput("input_geometry_1", b);
        combine.processNode(null);
        assertInstanceOf(CompositeGeometryData.class, combine.getOutput("output_geometry"));
        assertEquals(2, combine.getOutput("output_count"));
        assertEquals(Boolean.TRUE, combine.getOutput("output_valid"));
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
