package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.datatypes.MirroredSdfData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.TransformedSdfData;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GeometryMirror;
import com.nodecraft.nodesystem.util.GeometryTransform;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for Batch 6 Transform family language and capability contract.
 */
class TransformFamilyContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void rotateAndScaleCentersArePoints() {
        assertPortType("transform.basic_transforms.rotate_geometry_axis", "input_center", true, NodeDataType.POINT);
        assertPortType("transform.basic_transforms.rotate_geometry_axis", "input_axis", true, NodeDataType.VECTOR);
        assertPortType("transform.basic_transforms.scale_geometry_point", "input_center", true, NodeDataType.POINT);
    }

    @Test
    void rotateVectorUsesVectorPortsAndDegrees() {
        assertPortType("transform.orientation.rotate_vector", "input_vector", true, NodeDataType.VECTOR);
        assertPortType("transform.orientation.rotate_vector", "input_axis", true, NodeDataType.VECTOR);
        assertPortType("transform.orientation.rotate_vector", "input_angle", true, NodeDataType.DOUBLE);
        assertPortType("transform.orientation.rotate_vector", "output_rotated_vector", false, NodeDataType.VECTOR);

        INode created = NodeRegistry.getInstance().createNodeInstance("transform.orientation.rotate_vector");
        BaseNode node = assertInstanceOf(BaseNode.class, created);
        String desc = node.getDescription().toLowerCase();
        assertTrue(desc.contains("degrees"));
        assertFalse(desc.contains("radian"));

        node.setInput("input_vector", new Vector3d(1, 0, 0));
        node.setInput("input_axis", new Vector3d(0, 1, 0));
        node.setInput("input_angle", 90.0d);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        Vector3d rotated = assertInstanceOf(Vector3d.class, node.getOutput("output_rotated_vector"));
        assertEquals(0.0d, rotated.x, 1.0e-6d);
        assertEquals(0.0d, rotated.y, 1.0e-6d);
        assertEquals(-1.0d, rotated.z, 1.0e-6d);
    }

    @Test
    void projectPointToPlaneRequiresPoint() {
        assertPortType("transform.orientation.project_to_plane", "input_point", true, NodeDataType.POINT);
        assertPortType("transform.orientation.project_to_plane", "input_plane", true, NodeDataType.PLANE);
    }

    @Test
    void negativeScaleIsRejected() {
        BaseNode transform = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("transform.basic_transforms.transform_geometry"));
        transform.setInput("input_geometry", new SphereData(new Vector3d(), 2.0d));
        transform.setInput("input_scale", -1.0d);
        transform.processNode(null);
        assertEquals(Boolean.FALSE, transform.getOutput("output_valid"));
        String error = String.valueOf(transform.getOutput("output_error")).toLowerCase();
        assertTrue(error.contains("greater than zero") || error.contains("mirror"));

        BaseNode scale = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("transform.basic_transforms.scale_geometry_point"));
        scale.setInput("input_geometry", new SphereData(new Vector3d(10, 0, 0), 2.0d));
        scale.setInput("input_center", new PointData(new Vector3d(10, 0, 0)));
        scale.setInput("input_scale", -1.0d);
        scale.processNode(null);
        assertEquals(Boolean.FALSE, scale.getOutput("output_valid"));
        assertNull(scale.getOutput("output_geometry"));

        assertNull(GeometryTransform.transform(new SphereData(new Vector3d(), 1.0d), new Vector3d(), 0, 0, 0, -1.0d));
    }

    @Test
    void compositeAndBooleanTransformsPreserveWrappers() {
        BoxGeometryData a = new BoxGeometryData(new Vector3d(2, 2, 2), new Vector3d(1, 1, 1));
        BoxGeometryData b = new BoxGeometryData(new Vector3d(4, 2, 2), new Vector3d(1, 1, 1));
        CompositeGeometryData composite = new CompositeGeometryData(List.of(a, b));
        DifferenceGeometryData difference = new DifferenceGeometryData(a, b);
        IntersectionGeometryData intersection = new IntersectionGeometryData(a, b);

        GeometryData movedComposite = GeometryTransform.transform(composite, new Vector3d(1, 0, 0), 0, 0, 0, 1);
        assertInstanceOf(CompositeGeometryData.class, movedComposite);
        assertEquals(2, ((CompositeGeometryData) movedComposite).size());

        GeometryData movedDiff = GeometryTransform.transform(difference, new Vector3d(1, 0, 0), 0, 0, 0, 1);
        assertInstanceOf(DifferenceGeometryData.class, movedDiff);

        GeometryData movedInter = GeometryTransform.transform(intersection, new Vector3d(1, 0, 0), 0, 0, 0, 1);
        assertInstanceOf(IntersectionGeometryData.class, movedInter);
    }

    @Test
    void sdfSupportsEulerAndArbitraryAxisTransforms() {
        SignedDistanceFieldData sphereSdf = point -> point.length() - 2.0d;
        SdfGeometryData sdfGeom = new SdfGeometryData(
            sphereSdf,
            new Vector3d(-3, -3, -3),
            new Vector3d(3, 3, 3),
            0.0d
        );

        GeometryData moved = GeometryTransform.transform(sdfGeom, new Vector3d(5, 0, 0), 0, 0, 0, 1);
        assertInstanceOf(SdfGeometryData.class, moved);
        assertInstanceOf(TransformedSdfData.class, ((SdfGeometryData) moved).sdf());

        Matrix3d rotation = new Matrix3d().set(new Quaterniond(new AxisAngle4d(Math.toRadians(45), 0, 1, 0)));
        GeometryData rotated = GeometryTransform.transformAround(sdfGeom, new Vector3d(), rotation, 1.0d);
        assertInstanceOf(SdfGeometryData.class, rotated);
        assertInstanceOf(TransformedSdfData.class, ((SdfGeometryData) rotated).sdf());
    }

    @Test
    void mirrorSupportsSdfGeometry() {
        SignedDistanceFieldData sphereSdf = point -> point.length() - 2.0d;
        SdfGeometryData sdfGeom = new SdfGeometryData(
            sphereSdf,
            new Vector3d(-3, -3, -3),
            new Vector3d(3, 3, 3),
            0.0d
        );
        PlaneData yz = new PlaneData(new Vector3d(), new Vector3d(1, 0, 0));
        GeometryData mirrored = GeometryMirror.mirror(sdfGeom, yz);
        assertInstanceOf(SdfGeometryData.class, mirrored);
        assertInstanceOf(MirroredSdfData.class, ((SdfGeometryData) mirrored).sdf());
    }

    @Test
    void ellipsoidRotationPreservesOrientation() {
        EllipsoidGeometryData ellipsoid = new EllipsoidGeometryData(
            new Vector3d(0, 0, 0),
            new Vector3d(8, 3, 2)
        );
        Matrix3d rotation = new Matrix3d().set(new Quaterniond(new AxisAngle4d(Math.toRadians(45), 0, 1, 0)));
        GeometryData rotated = GeometryTransform.transformAround(ellipsoid, new Vector3d(), rotation, 1.0d);
        EllipsoidGeometryData out = assertInstanceOf(EllipsoidGeometryData.class, rotated);
        assertEquals(8.0d, out.getRadii().x, 1.0e-9d);
        assertEquals(3.0d, out.getRadii().y, 1.0e-9d);
        assertEquals(2.0d, out.getRadii().z, 1.0e-9d);
        assertFalse(out.getOrientationMatrix().equals(new Matrix3d().identity(), 1.0e-6d));
        assertTrue(out.isOriented());
    }

    @Test
    void alignOutputsTypedPlaneAndFrameLists() {
        assertPortType("transform.orientation.align_to_surface", "output_planes", false, NodeDataType.PLANE_LIST);
        assertPortType("transform.orientation.align_to_surface", "output_frames", false, NodeDataType.FRAME_LIST);
    }

    @Test
    void frameLanguageIsWiredOnReferenceNodes() {
        assertEquals(FrameData.class, NodeDataType.FRAME.getJavaClass());
        assertPortType("reference.frames.world_frame", "output_frame", false, NodeDataType.FRAME);
        assertPortType("reference.frames.construct_frame", "input_origin", true, NodeDataType.POINT);
        assertPortType("reference.frames.construct_frame", "output_frame", false, NodeDataType.FRAME);
        assertPortType("reference.frames.deconstruct_frame", "input_frame", true, NodeDataType.FRAME);
        assertPortType("reference.frames.transform_frame", "input_frame", true, NodeDataType.FRAME);
        assertPortType("reference.frames.frame_from_plane", "input_plane", true, NodeDataType.PLANE);
        assertPortType("reference.frames.frame_from_face", "output_center", false, NodeDataType.POINT);
        assertPortType("reference.frames.sphere_surface_frame", "input_point", true, NodeDataType.POINT);
        assertPortType("reference.frames.sphere_surface_frame", "input_x_hint", true, NodeDataType.VECTOR);
        assertPortType("reference.planes.deconstruct_plane", "input_plane", true, NodeDataType.PLANE);

        BaseNode world = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("reference.frames.world_frame"));
        world.processNode(null);
        assertInstanceOf(FrameData.class, world.getOutput("output_frame"));
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
