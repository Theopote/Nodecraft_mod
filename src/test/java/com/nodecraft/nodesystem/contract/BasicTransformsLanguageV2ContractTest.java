package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.transform.basic_transforms.MirrorPointListAboutPlaneNode;
import com.nodecraft.nodesystem.nodes.transform.basic_transforms.OffsetBoxFaceNode;
import com.nodecraft.nodesystem.nodes.transform.basic_transforms.ScaleGeometryAroundPointNode;
import com.nodecraft.nodesystem.nodes.transform.basic_transforms.TransformPointsByFramesNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BoxFaceValidator;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryMirror;
import com.nodecraft.nodesystem.util.GeometryTransform;
import com.nodecraft.nodesystem.util.PointUtils;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Basic Transforms Language v2 (Graph V75).
 */
class BasicTransformsLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "transform.basic_transforms.move_geometry",
        "transform.basic_transforms.rotate_geometry_axis",
        "transform.basic_transforms.scale_geometry_point",
        "transform.basic_transforms.transform_geometry",
        "transform.basic_transforms.mirror_geometry_plane",
        "transform.basic_transforms.mirror_point_list_plane",
        "transform.basic_transforms.transform_by_frames",
        "transform.basic_transforms.offset_face",
        "transform.basic_transforms.inset_face"
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV75() {
        assertEquals(75, GraphFormatVersion.V75);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V75);
    }

    @Test
    void exactlyNineNodesWithUniqueOrdersZeroToEight() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("transform.basic_transforms."))
            .sorted()
            .toList();
        assertEquals(9, ids.size());
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("transform.basic_transforms", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 9; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allNodesHaveValidAndErrorWithoutBannedPortTypes() {
        List<String> errors = new ArrayList<>();
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            if (!hasPort(node, "output_valid")) {
                errors.add(id + " missing output_valid");
            }
            if (!hasPort(node, "output_error")) {
                errors.add(id + " missing output_error");
            }
            for (IPort port : node.getInputPorts()) {
                banPortType(errors, id, port);
            }
            for (IPort port : node.getOutputPorts()) {
                banPortType(errors, id, port);
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void scaleRejectsNonPositiveWithError() {
        ScaleGeometryAroundPointNode scale = new ScaleGeometryAroundPointNode();
        connectInput(scale, "input_scale", NodeDataType.DOUBLE);
        scale.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        scale.setInput("input_scale", 0.0d);
        scale.processNode(null);
        assertEquals(Boolean.FALSE, scale.getOutput("output_valid"));
        assertTrue(String.valueOf(scale.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("scale"));
    }

    @Test
    void moveConnectedInvalidTranslationFailsClosed() {
        BaseNode move = (BaseNode) registry.createNodeInstance("transform.basic_transforms.move_geometry");
        connectInput(move, "input_translation", NodeDataType.VECTOR);
        move.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        move.setInput("input_translation", "not-a-vector");
        move.processNode(null);
        assertEquals(Boolean.FALSE, move.getOutput("output_valid"));
        assertNotNull(move.getOutput("output_error"));
        assertFalse(String.valueOf(move.getOutput("output_error")).isBlank());
    }

    @Test
    void transformByFramesFailsClosedOnCartesianWorkload() {
        TransformPointsByFramesNode node = new TransformPointsByFramesNode();
        int points = 1024;
        int frames = GenerationLimits.MAX_LIST_ELEMENTS / points + 1;
        assertTrue((long) frames * points > GenerationLimits.MAX_LIST_ELEMENTS);

        List<PointData> local = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            local.add(new PointData(i, 0, 0));
        }
        List<FrameData> frameList = new ArrayList<>(frames);
        for (int i = 0; i < frames; i++) {
            frameList.add(FrameData.orthonormal(
                new Vector3d(i, 0, 0),
                new Vector3d(1, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)
            ));
        }

        node.setInput("input_local_points", local);
        node.setInput("input_frames", frameList);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("limit"));
        assertEquals(0, node.getOutput("output_count"));
        assertEquals(List.of(), node.getOutput("output_points"));
    }

    @Test
    void mirrorPointListFailsClosedOnOversizeList() {
        MirrorPointListAboutPlaneNode mirror = new MirrorPointListAboutPlaneNode();
        mirror.setInput("input_plane", PlaneData.XZ_PLANE);
        mirror.setInput("input_points", oversizedPointList());
        mirror.processNode(null);
        assertEquals(Boolean.FALSE, mirror.getOutput("output_valid"));
        assertTrue(String.valueOf(mirror.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("limit"));
        assertEquals(0, mirror.getOutput("output_count"));
    }

    @Test
    void pointUtilsBoundedRejectsOversizeWithoutTruncation() {
        assertNull(PointUtils.resolveStrictPointListBounded(oversizedPointList(), GenerationLimits.MAX_LIST_ELEMENTS));
    }

    @Test
    void offsetRejectsNonQuadFace() {
        OffsetBoxFaceNode offset = new OffsetBoxFaceNode();
        offset.setInput("input_distance", 1.0d);
        BoxFaceData triangle = new BoxFaceData(
            0, "tri", List.of(0, 1, 2),
            List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)),
            new Vector3d(0.33, 0.33, 0), new Vector3d(0, 0, 1)
        );
        offset.setInput("input_face", triangle);
        offset.processNode(null);
        assertEquals(Boolean.FALSE, offset.getOutput("output_valid"));
        assertTrue(String.valueOf(offset.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("4"));
        assertNull(offset.getOutput("output_face"));
    }

    @Test
    void boxFaceValidatorRejectsMalformedFaces() {
        assertNotNull(BoxFaceValidator.validate(null));
        assertNotNull(BoxFaceValidator.validateConstruction(
            "x", List.of(0, 1, 2), List.of(new Vector3d(), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)),
            new Vector3d(), new Vector3d(0, 0, 1)
        ));
        List<Vector3d> corners = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(2, 0, 0),
            new Vector3d(2, 2, 0),
            new Vector3d(0, 2, 0)
        );
        String nanCenterError = BoxFaceValidator.validateConstruction(
            "front", List.of(0, 1, 2, 3), corners,
            new Vector3d(Double.NaN, 1, 0), new Vector3d(0, 0, 1)
        );
        assertNotNull(nanCenterError);
        assertTrue(nanCenterError.toLowerCase(Locale.ROOT).contains("center"));
        assertNull(BoxFaceValidator.validate(unitFace()));
    }

    @Test
    void trsOrderIsScaleThenRotateXyzThenTranslate() {
        SphereData sphere = new SphereData(new Vector3d(1, 0, 0), 1.0d);
        GeometryData transformed = GeometryTransform.transform(
            sphere,
            new Vector3d(10, 0, 0),
            0.0d, 0.0d, 90.0d,
            2.0d
        );
        SphereData out = assertInstanceOf(SphereData.class, transformed);
        // scale(1,0,0)->(2,0,0); rotZ90->(0,2,0); +T(10,0,0)->(10,2,0)
        assertEquals(10.0d, out.center().x, 1.0e-9d);
        assertEquals(2.0d, out.center().y, 1.0e-9d);
        assertEquals(0.0d, out.center().z, 1.0e-9d);
        assertEquals(2.0d, out.radius(), 1.0e-9d);
    }

    @Test
    void v74PrimitivesAreTransformableAndMirrorable() {
        PlaneData plane = new PlaneData(new Vector3d(), new Vector3d(0, 1, 0));
        Vector3d t = new Vector3d(1, 0, 0);
        for (GeometryData geometry : v74SampleGeometries()) {
            GeometryData transformed = GeometryTransform.transform(geometry, t, 0, 15, 0, 1.25d);
            assertNotNull(transformed, () -> "transform failed for " + geometry.getClass().getSimpleName());
            GeometryData mirrored = GeometryMirror.mirror(geometry, plane);
            assertNotNull(mirrored, () -> "mirror failed for " + geometry.getClass().getSimpleName());
        }
    }

    private static List<GeometryData> v74SampleGeometries() {
        Vector3d o = new Vector3d();
        Vector3d y = new Vector3d(0, 1, 0);
        CylinderGeometryData cylinder = new CylinderGeometryData(o, new Vector3d(0, 2, 0), 1.0d);
        HemisphereGeometryData startCap = new HemisphereGeometryData(o, new Vector3d(0, -1, 0), 1.0d);
        HemisphereGeometryData endCap = new HemisphereGeometryData(new Vector3d(0, 2, 0), y, 1.0d);
        return List.of(
            new SphereData(o, 1.0d),
            new BoxGeometryData(o, new Vector3d(1, 1, 1)),
            cylinder,
            new ConeGeometryData(o, new Vector3d(0, 2, 0), 1.0d),
            new FrustumConeGeometryData(o, new Vector3d(0, 2, 0), 2.0d, 1.0d),
            new TorusGeometryData(o, y, 3.0d, 1.0d),
            new EllipsoidGeometryData(o, new Vector3d(1, 2, 3)),
            new HemisphereGeometryData(o, y, 1.0d),
            new SquarePyramidGeometryData(
                o, new Vector3d(1, 0, 0), new Vector3d(0, 0, 1), y, 2.0d, 3.0d),
            new TetrahedronGeometryData(o, 2.0d),
            new OctahedronGeometryData(o, 2.0d),
            new IcosahedronGeometryData(o, 2.0d),
            new DodecahedronGeometryData(o, 2.0d),
            new PrismGeometryData(
                List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0.5, 0, 1)),
                new Vector3d(0, 2, 0)
            ),
            new CompositeGeometryData(List.of(cylinder, startCap, endCap))
        );
    }

    private static BoxFaceData unitFace() {
        List<Vector3d> corners = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(2, 0, 0),
            new Vector3d(2, 2, 0),
            new Vector3d(0, 2, 0)
        );
        return new BoxFaceData(0, "front", List.of(0, 1, 2, 3), corners, new Vector3d(1, 1, 0), new Vector3d(0, 0, 1));
    }

    private static List<PointData> oversizedPointList() {
        return new AbstractList<>() {
            @Override
            public PointData get(int index) {
                return new PointData(0, 0, 0);
            }

            @Override
            public int size() {
                return GenerationLimits.MAX_LIST_ELEMENTS + 1;
            }
        };
    }

    private static void banPortType(List<String> errors, String id, IPort port) {
        if (port.getDataType() == NodeDataType.ANY) {
            errors.add(id + "." + port.getId() + " is ANY");
        }
        if (port.getDataType() == NodeDataType.LIST) {
            errors.add(id + "." + port.getId() + " uses raw LIST");
        }
        if (port.getDataType() == NodeDataType.LINE) {
            errors.add(id + "." + port.getId() + " uses graph LINE");
        }
        if (port.getDataType() == NodeDataType.POLYLINE) {
            errors.add(id + "." + port.getId() + " uses graph POLYLINE");
        }
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getInputPorts().stream().anyMatch(p -> portId.equals(p.getId()))
            || node.getOutputPorts().stream().anyMatch(p -> portId.equals(p.getId()));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }
}
