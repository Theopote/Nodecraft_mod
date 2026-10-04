package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.geometry.combine.CombineGeometryNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GeometryBoundsResolver;
import com.nodecraft.nodesystem.util.GeometryVoxelizationResult;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.VoxelizationStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Geometry Combine v1 (current graph format).
 */
class GeometryCombineLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsCurrent() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void exactlyOneCanonicalNodeWithOrderZero() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("geometry.combine."))
            .sorted()
            .toList();
        assertEquals(1, ids.size());
        assertEquals("geometry.combine.geometry", ids.getFirst());
        assertNull(registry.getNodeInfo("geometry.boolean.union"));

        NodeInfo info = registry.getNodeInfo("geometry.combine.geometry");
        assertNotNull(info);
        assertEquals("geometry.combine", info.getCategoryId());
        assertEquals(0, info.getOrder());
        INode node = registry.createNodeInstance("geometry.combine.geometry");
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), "geometry.combine.geometry"));
    }

    @Test
    void exposesGeometryCountValidAndErrorPorts() {
        CombineGeometryNode node = new CombineGeometryNode();
        assertPortType(node, "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(node, "output_count", NodeDataType.INTEGER);
        assertPortType(node, "output_valid", NodeDataType.BOOLEAN);
        assertPortType(node, "output_error", NodeDataType.STRING);
        assertFalse(hasPort(node, "output_blocks"));
        assertFalse(hasPort(node, "output_region"));
    }

    @Test
    void zeroConnectedInputsIsInvalid() {
        CombineGeometryNode node = new CombineGeometryNode();
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNull(node.getOutput("output_geometry"));
        assertEquals(0, node.getOutput("output_count"));
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
    }

    @Test
    void oneConnectedInputReturnsRawGeometry() {
        CombineGeometryNode node = new CombineGeometryNode();
        BoxGeometryData box = unitBox(0, 0, 0);
        connectInput(node, "input_geometry_0", NodeDataType.GEOMETRY);
        node.setInput("input_geometry_0", box);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(box, node.getOutput("output_geometry"));
        assertFalse(node.getOutput("output_geometry") instanceof CompositeGeometryData);
        assertEquals(1, node.getOutput("output_count"));
    }

    @Test
    void twoConnectedInputsReturnComposite() {
        CombineGeometryNode node = new CombineGeometryNode();
        BoxGeometryData a = unitBox(0, 0, 0);
        BoxGeometryData b = unitBox(4, 0, 0);
        connectInput(node, "input_geometry_0", NodeDataType.GEOMETRY);
        connectInput(node, "input_geometry_1", NodeDataType.GEOMETRY);
        node.setInput("input_geometry_0", a);
        node.setInput("input_geometry_1", b);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertInstanceOf(CompositeGeometryData.class, node.getOutput("output_geometry"));
        assertEquals(2, node.getOutput("output_count"));
    }

    @Test
    void nestedCompositeIsFlattenedAndCountIsLeafTotal() {
        CombineGeometryNode node = new CombineGeometryNode();
        BoxGeometryData a = unitBox(0, 0, 0);
        BoxGeometryData b = unitBox(2, 0, 0);
        BoxGeometryData c = unitBox(4, 0, 0);
        CompositeGeometryData nested = new CompositeGeometryData(List.of(a, b));
        connectInput(node, "input_geometry_0", NodeDataType.GEOMETRY);
        connectInput(node, "input_geometry_1", NodeDataType.GEOMETRY);
        node.setInput("input_geometry_0", nested);
        node.setInput("input_geometry_1", c);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        CompositeGeometryData out = assertInstanceOf(CompositeGeometryData.class, node.getOutput("output_geometry"));
        assertEquals(3, out.size());
        assertEquals(3, node.getOutput("output_count"));
    }

    @Test
    void flattenedLeafBudgetFailsClosed() {
        List<GeometryData> leaves = new ArrayList<>(GenerationLimits.MAX_COMPOSITE_GEOMETRY_LEAVES);
        for (int i = 0; i < GenerationLimits.MAX_COMPOSITE_GEOMETRY_LEAVES; i++) {
            leaves.add(unitBox(i, 0, 0));
        }
        CompositeGeometryData atCap = new CompositeGeometryData(leaves);
        assertEquals(GenerationLimits.MAX_COMPOSITE_GEOMETRY_LEAVES, atCap.size());
        assertThrows(IllegalArgumentException.class, () ->
            new CompositeGeometryData(List.of(atCap, unitBox(-1, 0, 0))));

        CombineGeometryNode node = new CombineGeometryNode();
        connectInput(node, "input_geometry_0", NodeDataType.GEOMETRY);
        connectInput(node, "input_geometry_1", NodeDataType.GEOMETRY);
        node.setInput("input_geometry_0", atCap);
        node.setInput("input_geometry_1", unitBox(-1, 0, 0));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNull(node.getOutput("output_geometry"));
        assertEquals(0, node.getOutput("output_count"));
        assertTrue(String.valueOf(node.getOutput("output_error")).contains(CompositeGeometryData.LEAF_BUDGET_EXCEEDED));
    }

    @Test
    void connectedNullInputFailsWholeNode() {
        CombineGeometryNode node = new CombineGeometryNode();
        connectInput(node, "input_geometry_0", NodeDataType.GEOMETRY);
        connectInput(node, "input_geometry_1", NodeDataType.GEOMETRY);
        node.setInput("input_geometry_0", unitBox(0, 0, 0));
        node.setInput("input_geometry_1", null);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNull(node.getOutput("output_geometry"));
        assertEquals(0, node.getOutput("output_count"));
    }

    @Test
    void connectedWrongPayloadFailsWholeNode() {
        CombineGeometryNode node = new CombineGeometryNode();
        connectInput(node, "input_geometry_0", NodeDataType.GEOMETRY);
        node.setInput("input_geometry_0", "not geometry");
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNull(node.getOutput("output_geometry"));
    }

    @Test
    void unconnectedSlotsAreIgnored() {
        CombineGeometryNode node = new CombineGeometryNode();
        connectInput(node, "input_geometry_0", NodeDataType.GEOMETRY);
        node.setInput("input_geometry_0", unitBox(0, 0, 0));
        node.setInput("input_geometry_2", unitBox(10, 0, 0));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(1, node.getOutput("output_count"));
    }

    @Test
    void compositeGeometryDataRejectsNullMembers() {
        List<GeometryData> withNull = new ArrayList<>();
        withNull.add(unitBox(0, 0, 0));
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new CompositeGeometryData(withNull));
    }

    @Test
    void compositeBoundsAreUnionOfChildren() {
        BoxGeometryData a = unitBox(0, 0, 0);
        BoxGeometryData b = unitBox(10, 0, 0);
        BoundingBoxData union = GeometryBoundsResolver.resolve(new CompositeGeometryData(List.of(a, b)));
        assertNotNull(union);
        assertNotNull(GeometryBoundsResolver.resolve(a));
        assertNotNull(GeometryBoundsResolver.resolve(b));
    }

    @Test
    void strictVoxelizationOfCombinedResultRemainsTransactional() {
        BoxGeometryData valid = unitBox(0, 0, 0);
        GeometryData unsupported = new GeometryData() {
        };
        CompositeGeometryData composite = new CompositeGeometryData(List.of(valid, unsupported));
        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(composite, true);
        assertFalse(result.success());
        assertEquals(VoxelizationStatus.CHILD_FAILURE, result.status());
    }

    private static BoxGeometryData unitBox(double cx, double cy, double cz) {
        return new BoxGeometryData(new Vector3d(cx + 0.5d, cy + 0.5d, cz + 0.5d), new Vector3d(0.5d, 0.5d, 0.5d));
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

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        IPort port = findPort(node, portId);
        assertNotNull(port, node.getTypeId() + " missing " + portId);
        assertEquals(expected, port.getDataType());
    }

    private static boolean hasPort(INode node, String portId) {
        return findPort(node, portId) != null;
    }

    private static @Nullable IPort findPort(INode node, String portId) {
        List<IPort> ports = new ArrayList<>();
        ports.addAll(node.getInputPorts());
        ports.addAll(node.getOutputPorts());
        return ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElse(null);
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
}
