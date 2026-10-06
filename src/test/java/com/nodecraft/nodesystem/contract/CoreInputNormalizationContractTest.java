package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameDataTestAccess;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreInputNormalizationContractTest {

    @Test
    void integerDoubleFloatAreNotImplicitlyConnectable() {
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.INTEGER, NodeDataType.DOUBLE));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.DOUBLE, NodeDataType.INTEGER));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.FLOAT, NodeDataType.DOUBLE));
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.DOUBLE, NodeDataType.ANY));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.ANY, NodeDataType.DOUBLE));
    }

    @Test
    void pointRejectsVector3dOnAllIngressPaths() {
        assertRejectedOnAllPaths(NodeDataType.POINT, new Vector3d(1, 2, 3));
    }

    @Test
    void doubleRejectsIntegerOnAllIngressPaths() {
        assertRejectedOnAllPaths(NodeDataType.DOUBLE, 4);
    }

    @Test
    void frameRejectsMalformedOnAllIngressPaths() {
        assertRejectedOnAllPaths(NodeDataType.FRAME, FrameDataTestAccess.unchecked(
            new Vector3d(0, 0, 0),
            new Vector3d(Double.NaN, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 1)
        ));
    }

    @Test
    void pathRejectsNonFinitePolylineOnAllIngressPaths() {
        PolylineData invalid = new PolylineData(List.of(
            new Vec3d(Double.NaN, 0, 0),
            new Vec3d(1, 0, 0)
        ));
        assertRejectedOnAllPaths(NodeDataType.PATH, invalid);
    }

    @Test
    void pathListRejectsNullMemberWithoutCollapsing() {
        PathData good = PathData.fromLine(new LineData(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0)));
        List<Object> mixed = java.util.Arrays.asList(good, null, good);
        StubNode node = new StubNode(NodeDataType.PATH_LIST);

        node.setInput("input_value", mixed);
        assertNull(node.getInput("input_value"));

        BasePort port = (BasePort) node.getInputPorts().getFirst();
        port.setValue(mixed);
        assertNull(port.getValue());

        node.compute(Map.of("input_value", mixed));
        assertNull(node.getInput("input_value"));
    }

    @Test
    void computeIgnoresUnknownInputIds() {
        StubNode node = new StubNode(NodeDataType.DOUBLE);
        node.compute(Map.of("not_a_port", 1.0d));
        assertNull(node.getInput("not_a_port"));
        assertFalse(node.isInputPresent("not_a_port"));
    }

    private static void assertRejectedOnAllPaths(NodeDataType type, Object badValue) {
        StubNode setInputNode = new StubNode(type);
        setInputNode.setInput("input_value", badValue);
        assertNull(setInputNode.getInput("input_value"));

        StubNode portNode = new StubNode(type);
        BasePort port = (BasePort) portNode.getInputPorts().getFirst();
        Object before = port.getValue();
        port.setValue(badValue);
        assertFalse(badValue.equals(port.getValue()));
        if (before == null) {
            assertNull(port.getValue());
        }

        StubNode computeNode = new StubNode(type);
        computeNode.compute(Map.of("input_value", badValue));
        assertNull(computeNode.getInput("input_value"));
    }

    private static final class StubNode extends BaseNode {
        StubNode(NodeDataType inputType) {
            super(UUID.randomUUID(), "test.input_normalizer_stub");
            addInputPort(new BasePort("input_value", "Value", "", inputType, this));
            addOutputPort(new BasePort("output_value", "Value", "", inputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }
}
