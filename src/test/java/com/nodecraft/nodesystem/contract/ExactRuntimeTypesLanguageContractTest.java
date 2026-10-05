package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.nodes.geometry.curves.PathToPointsNode;
import com.nodecraft.nodesystem.nodes.input.numeric.DoubleToIntegerNode;
import com.nodecraft.nodesystem.nodes.input.numeric.IntegerToDoubleNode;
import com.nodecraft.nodesystem.util.PathInputUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactRuntimeTypesLanguageContractTest {

    @Test
    void pointDataRejectsNonFiniteComponents() {
        assertThrows(IllegalArgumentException.class, () -> new PointData(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PointData(0, Double.POSITIVE_INFINITY, 0));
        assertNull(PointData.canonical(new Vector3d(Double.NaN, 0, 0)));
        assertNotNull(PointData.canonical(new Vector3d(1, 2, 3)));
    }

    @Test
    void nodeDataTypeDoubleRejectsNonBoxedNumbers() {
        assertFalse(NodeDataType.DOUBLE.isCompatible(2));
        assertFalse(NodeDataType.DOUBLE.isCompatible(2.0f));
        assertTrue(NodeDataType.DOUBLE.isCompatible(2.0d));
        assertFalse(NodeDataType.DOUBLE.isCompatible(Double.NaN));
    }

    @Test
    void nodeDataTypePathAcceptsCanonicalPathDataOnly() {
        LineData line = new LineData(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0));
        assertFalse(NodeDataType.PATH.isCompatible(line));
        assertTrue(NodeDataType.PATH.isCompatible(PathData.fromLine(line)));
        assertFalse(NodeDataType.PATH_LIST.isCompatible(List.of(line)));
        assertTrue(NodeDataType.PATH_LIST.isCompatible(List.of(PathData.fromLine(line))));
    }

    @Test
    void pathInputUtilsWrapsLinePolylineAndCurveSources() {
        LineData line = new LineData(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0));
        PathData wrapped = PathInputUtils.resolvePath(line);
        assertNotNull(wrapped);
        assertEquals(PathData.Kind.LINE, wrapped.getKind());
    }

    @Test
    void strictDoubleListRejectsIntegerElements() {
        assertNull(StrictDoubleUtils.resolveStrictDoubleList(List.of(1, 2.0d)));
        assertEquals(List.of(1.0d, 2.0d), StrictDoubleUtils.resolveStrictDoubleList(List.of(1.0d, 2.0d)));
    }

    @Test
    void integerToDoubleNodeProducesExactDouble() {
        IntegerToDoubleNode node = new IntegerToDoubleNode();
        node.setInput("input_value", 7);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(7.0d, node.getOutput("output_value"));
    }

    @Test
    void doubleToIntegerNodeRejectsNonIntegralValues() {
        DoubleToIntegerNode node = new DoubleToIntegerNode();
        node.setInput("input_value", 3.5d);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNull(node.getOutput("output_value"));
    }

    @Test
    void resolvePointRejectsNonPointData() {
        assertNull(SpatialValueResolver.resolvePoint(new Vector3d(1, 2, 3)));
        assertNotNull(SpatialValueResolver.resolvePoint(new PointData(1, 2, 3)));
    }

    @Test
    void pathPortSetInputWrapsLineIntoPathData() {
        PathToPointsNode node = new PathToPointsNode();
        LineData line = new LineData(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0));
        node.setInput("input_path", line);
        assertTrue(node.getInput("input_path") instanceof PathData);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
    }

    @Test
    void lineToPathRemainsImplicitButNotCanonicalRuntimeValue() {
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.LINE, NodeDataType.PATH));
        PolylineData polyline = new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0)));
        assertFalse(NodeDataType.PATH.isCompatible(polyline));
    }

}
