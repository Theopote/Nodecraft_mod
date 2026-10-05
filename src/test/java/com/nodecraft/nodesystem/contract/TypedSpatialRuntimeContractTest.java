package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.SphereSdfData;
import com.nodecraft.nodesystem.datatypes.TransformedSdfData;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfSamplePointNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfTransformNode;
import com.nodecraft.nodesystem.nodes.geometry.curves.ExplodePathNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.ConvexHull2DOnPlaneNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PathCanonicalizer;
import com.nodecraft.nodesystem.util.SdfExpressionLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedSpatialRuntimeContractTest {

    @BeforeAll
    static void initRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void optionalPointRejectsNonPointDataWhenConnected() {
        StubNode node = new StubNode(NodeDataType.POINT);
        connectStub(node, "input_value");
        node.setInput("input_value", new Vector3d(1, 2, 3));
        assertNull(OptionalPortDrive.resolveOptionalPoint(node, "input_value", null));
    }

    @Test
    void optionalPointAcceptsPointDataWhenConnected() {
        StubNode node = new StubNode(NodeDataType.POINT);
        connectStub(node, "input_value");
        node.setInput("input_value", new PointData(1, 2, 3));
        Vector3d resolved = OptionalPortDrive.resolveOptionalPoint(node, "input_value", null);
        assertNotNull(resolved);
        assertEquals(1.0d, resolved.x, 1e-9);
    }

    @Test
    void optionalDoubleRejectsIntegerWhenConnected() {
        StubNode node = new StubNode(NodeDataType.DOUBLE);
        connectStub(node, "input_value");
        node.setInput("input_value", 2);
        assertNull(OptionalPortDrive.resolveOptionalDouble(node, "input_value", 0.0d));
    }

    @Test
    void optionalDoubleAcceptsExactDoubleWhenConnected() {
        StubNode node = new StubNode(NodeDataType.DOUBLE);
        connectStub(node, "input_value");
        node.setInput("input_value", 2.0d);
        Double resolved = OptionalPortDrive.resolveOptionalDouble(node, "input_value", 0.0d);
        assertNotNull(resolved);
        assertEquals(2.0d, resolved, 1e-9);
    }

    @Test
    void pathCanonicalizerCollapsesAdjacentDuplicatesForOpenPolyline() {
        PolylineData withDuplicate = new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(1, 0, 0),
            new Vec3d(1, 0, 0),
            new Vec3d(2, 0, 0)
        ));
        PolylineData canonical = PathCanonicalizer.canonicalize(withDuplicate);
        assertNotNull(canonical);
        assertEquals(3, canonical.points().size());
        assertNotNull(PathData.fromPolyline(withDuplicate));
    }

    @Test
    void pathCanonicalizerRejectsClosedLoopWithTooFewUniqueVertices() {
        PolylineData closed = new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(1, 0, 0),
            new Vec3d(0, 0, 0)
        ));
        assertNull(PathCanonicalizer.canonicalize(closed));
        assertNull(PathData.fromPolyline(closed));
    }

    @Test
    void pathCanonicalizerCanonicalizesNearClosedSeam() {
        PolylineData nearClosed = new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(1, 0, 0),
            new Vec3d(1, 1, 0),
            new Vec3d(0, 0, 1.0e-7)
        ));
        PolylineData canonical = PathCanonicalizer.canonicalize(nearClosed);
        assertNotNull(canonical);
        assertEquals(4, canonical.points().size());
        assertTrue(canonical.isClosed());
        assertEquals(canonical.points().getFirst(), canonical.points().getLast());
    }

    @Test
    void sdfTransformRejectsExpressionDepthOverflow() {
        SignedDistanceFieldData sdf = new SphereSdfData(new Vector3d(), 1.0d);
        for (int i = 0; i < GenerationLimits.MAX_SDF_EXPRESSION_DEPTH; i++) {
            sdf = new TransformedSdfData(sdf, new Vector3d(), 0, 0, 0, 1.0d);
        }
        assertTrue(SdfExpressionLimits.exceedsMax(sdf));

        SdfTransformNode transform = new SdfTransformNode();
        transform.setInput("input_sdf", sdf);
        transform.processNode(null);
        assertEquals(Boolean.FALSE, transform.getOutput("output_valid"));
        assertNull(transform.getOutput("output_sdf"));
    }

    @Test
    void sdfSamplePointInvalidDistanceIsNan() {
        SdfSamplePointNode sample = new SdfSamplePointNode();
        sample.processNode(null);
        assertEquals(Boolean.FALSE, sample.getOutput("output_valid"));
        assertTrue(Double.isNaN(((Number) sample.getOutput("output_distance")).doubleValue()));
    }

    @Test
    void convexHull2DFailsClosedOnFilteredPointList() {
        ConvexHull2DOnPlaneNode hull = new ConvexHull2DOnPlaneNode();
        hull.setInput("input_points", List.of(
            new PointData(0, 0, 0),
            "bad",
            new PointData(1, 0, 0),
            new PointData(0, 1, 0)
        ));
        hull.processNode(null);
        assertEquals(Boolean.FALSE, hull.getOutput("output_valid"));
    }

    @Test
    void invalidPathListOutputIsEmptyNotNull() {
        ExplodePathNode explode = new ExplodePathNode();
        explode.processNode(null);
        assertEquals(Boolean.FALSE, explode.getOutput("output_valid"));
        Object segments = explode.getOutput("output_segments");
        assertNotNull(segments);
        assertTrue(segments instanceof List<?> list && list.isEmpty());
    }

    @Test
    void resolvePointListLegacyStillSkipsInvalidEntries() {
        List<Vector3d> resolved = SpatialValueResolver.resolvePointList(List.of(
            new PointData(1, 2, 3),
            new Vector3d(4, 5, 6)
        ));
        assertEquals(2, resolved.size());
    }

    private static void connectStub(StubNode target, String inputPortId) {
        StubNode stub = new StubNode(target.inputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class StubNode extends BaseNode {
        private final NodeDataType inputType;

        StubNode(NodeDataType inputType) {
            super(UUID.randomUUID(), "test.stub");
            this.inputType = inputType;
            addInputPort(new BasePort("input_value", "Value", "", inputType, this));
            addOutputPort(new BasePort("output_value", "Value", "", inputType, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }
}
