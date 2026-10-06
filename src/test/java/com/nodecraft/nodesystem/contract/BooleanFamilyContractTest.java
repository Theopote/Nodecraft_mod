package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewGeometryNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.GeometryVoxelizationResult;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    void previewGeometryForwardsCompositeContainingDifferenceWithoutExpandingOperands() {
        PreviewGeometryNode preview = (PreviewGeometryNode) NodeRegistry.getInstance()
            .createNodeInstance("output.preview.preview_geometry");
        assertNotNull(preview);
        preview.setNodeState(Map.of("previewEnabled", false));

        BoxGeometryData body = new BoxGeometryData(new Vector3d(8.0d, 2.5d, 0.0d), new Vector3d(16.0d, 5.0d, 6.0d));
        BoxGeometryData cutter = new BoxGeometryData(new Vector3d(8.0d, 2.5d, 0.0d), new Vector3d(10.0d, 5.0d, 6.0d));
        BoxGeometryData deck = new BoxGeometryData(new Vector3d(8.0d, 5.4d, 0.0d), new Vector3d(16.0d, 0.8d, 4.0d));
        DifferenceGeometryData bridgeCut = new DifferenceGeometryData(body, cutter);
        CompositeGeometryData composite = new CompositeGeometryData(List.of(bridgeCut, deck));

        preview.setInput("input_geometry", composite);
        preview.processNode(null);

        Object forwarded = preview.getOutput("output_geometry");
        assertInstanceOf(CompositeGeometryData.class, forwarded);
        CompositeGeometryData out = (CompositeGeometryData) forwarded;
        assertEquals(2, out.size());
        assertTrue(out.geometries().stream().anyMatch(DifferenceGeometryData.class::isInstance));

        GeometryVoxelizationResult voxel = GeometryVoxelizer.voxelizeStrict(composite, true);
        assertTrue(voxel.success(), voxel.error());
        assertFalse(voxel.blocks().isEmpty());

        GeometryVoxelizationResult bodyOnly = GeometryVoxelizer.voxelizeStrict(body, true);
        assertTrue(bodyOnly.success(), bodyOnly.error());
        assertTrue(
            voxel.blocks().size() < bodyOnly.blocks().size(),
            "Composite with bridge cut must voxelize fewer blocks than uncut body alone");
    }

    @Test
    void combineProducesCompositeGeometryData() {
        BaseNode combine = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.combine.geometry");
        assertNotNull(combine);
        BoxGeometryData a = new BoxGeometryData(new Vector3d(1, 1, 1), new Vector3d(1, 1, 1));
        BoxGeometryData b = new BoxGeometryData(new Vector3d(4, 1, 1), new Vector3d(1, 1, 1));
        connectInput(combine, "input_geometry_0", NodeDataType.GEOMETRY);
        connectInput(combine, "input_geometry_1", NodeDataType.GEOMETRY);
        combine.setInput("input_geometry_0", a);
        combine.setInput("input_geometry_1", b);
        combine.processNode(null);
        assertInstanceOf(CompositeGeometryData.class, combine.getOutput("output_geometry"));
        assertEquals(2, combine.getOutput("output_count"));
        assertEquals(Boolean.TRUE, combine.getOutput("output_valid"));
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

    @Test
    void libraryCategoriesSeparateCombineBooleanSdfAndAnalysis() {
        assertEquals("geometry.combine",
            NodeRegistry.getInstance().getNodeInfo("geometry.combine.geometry").getCategoryId());
        assertEquals("geometry.boolean",
            NodeRegistry.getInstance().getNodeInfo("geometry.boolean.difference").getCategoryId());
        assertEquals("geometry.boolean",
            NodeRegistry.getInstance().getNodeInfo("geometry.boolean.intersection").getCategoryId());
        assertEquals("geometry.sdf",
            NodeRegistry.getInstance().getNodeInfo("geometry.boolean.sdf_boolean").getCategoryId());
        assertEquals("geometry.sdf",
            NodeRegistry.getInstance().getNodeInfo("geometry.boolean.sdf_sphere").getCategoryId());
        assertEquals("geometry.analysis",
            NodeRegistry.getInstance().getNodeInfo("geometry.analysis.block_bounds").getCategoryId());
        assertEquals("geometry.analysis",
            NodeRegistry.getInstance().getNodeInfo("geometry.analysis.geometry_bounds").getCategoryId());
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
