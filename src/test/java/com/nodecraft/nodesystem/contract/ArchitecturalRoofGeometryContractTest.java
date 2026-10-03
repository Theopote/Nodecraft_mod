package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.contract.support.ArchitecturalGeometryAssert;
import com.nodecraft.nodesystem.contract.support.ArchitecturalVoxelAssert;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofBaseNode;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roof Base geometry semantics: local extents and slope elevation (not just topology).
 */
class ArchitecturalRoofGeometryContractTest {

    private static final double TOL = 0.35d;

    @Test
    void flatRoofThicknessAlongNormal() {
        RoofBaseProbe roof = new RoofBaseProbe();
        roof.connectInput("input_roof_type", NodeDataType.STRING);
        roof.setInput("input_face", sampleFace(10.0d, 8.0d));
        roof.setInput("input_roof_type", "flat");
        roof.setInput("input_thickness", 0.25d);
        roof.processNode(null);

        assertEquals(Boolean.TRUE, roof.getOutput("output_valid"));
        BoxGeometryData box = assertInstanceOf(BoxGeometryData.class, roof.getOutput("output_geometry"));
        ArchitecturalGeometryAssert.assertLocalHalfExtents(box, 5.0d, 4.0d, 0.125d);
    }

    @Test
    void shedRoofHasSlopedElevation() {
        RoofBaseProbe roof = new RoofBaseProbe();
        roof.connectInput("input_roof_type", NodeDataType.STRING);
        roof.setInput("input_face", sampleFace(10.0d, 8.0d));
        roof.setInput("input_roof_type", "shed");
        roof.setInput("input_height", 2.0d);
        roof.processNode(null);

        assertEquals(Boolean.TRUE, roof.getOutput("output_valid"));
        PrismGeometryData prism = assertInstanceOf(PrismGeometryData.class, roof.getOutput("output_geometry"));
        List<Vector3d> base = prism.baseVertices();

        double lowY = base.stream().mapToDouble(v -> v.y).min().orElseThrow();
        double highY = base.stream().mapToDouble(v -> v.y).max().orElseThrow();
        double lowZ = base.stream().filter(v -> Math.abs(v.y - lowY) < TOL).mapToDouble(v -> v.z).max().orElseThrow();
        double highZ = base.stream().filter(v -> Math.abs(v.y - highY) < TOL).mapToDouble(v -> v.z).max().orElseThrow();
        assertTrue(highZ > lowZ + 0.5d, "high eave Z should exceed low eave Z: low=" + lowZ + " high=" + highZ);

        BlockPosList voxels = GeometryVoxelizer.voxelize(prism, true);
        Set<net.minecraft.util.math.BlockPos> solid = ArchitecturalVoxelAssert.toSolidSet(voxels);
        assertTrue(solid.size() > 0, "shed roof should voxelize");
        int lowDepthMaxZ = solid.stream()
            .filter(pos -> pos.getY() <= 1)
            .mapToInt(net.minecraft.util.math.BlockPos::getZ)
            .max().orElse(Integer.MIN_VALUE);
        int highDepthMinZ = solid.stream()
            .filter(pos -> pos.getY() >= 6)
            .mapToInt(net.minecraft.util.math.BlockPos::getZ)
            .min().orElse(Integer.MAX_VALUE);
        assertTrue(highDepthMinZ > lowDepthMaxZ,
            "voxel Z should rise along depth: lowMaxZ=" + lowDepthMaxZ + " highMinZ=" + highDepthMinZ);
    }

    @Test
    void gableRidgeAboveEaves() {
        RoofBaseProbe roof = new RoofBaseProbe();
        roof.connectInput("input_roof_type", NodeDataType.STRING);
        roof.setInput("input_face", sampleFace(10.0d, 8.0d));
        roof.setInput("input_roof_type", "gable");
        roof.setInput("input_height", 2.0d);
        roof.processNode(null);

        assertEquals(Boolean.TRUE, roof.getOutput("output_valid"));
        PathData ridge = assertInstanceOf(PathData.class, roof.getOutput("output_ridge_path"));
        PathData eave = assertInstanceOf(PathData.class, roof.getOutput("output_eave_path"));
        assertNotNull(ridge.getLine());
        assertNotNull(eave.getLine());
        ArchitecturalGeometryAssert.assertPathHigherThan(ridge, eave, 1.5d);
    }

    @Test
    void shedRoofWithThicknessUsesQuadrilateralProfile() {
        RoofBaseProbe roof = new RoofBaseProbe();
        roof.connectInput("input_roof_type", NodeDataType.STRING);
        roof.setInput("input_face", sampleFace(10.0d, 8.0d));
        roof.setInput("input_roof_type", "shed");
        roof.setInput("input_height", 2.0d);
        roof.setInput("input_thickness", 0.25d);
        roof.processNode(null);

        assertEquals(Boolean.TRUE, roof.getOutput("output_valid"));
        PrismGeometryData prism = assertInstanceOf(PrismGeometryData.class, roof.getOutput("output_geometry"));
        assertEquals(4, prism.baseVertices().size(), "thin shed roof uses outer+inner slope quadrilateral");

        List<Vector3d> base = prism.baseVertices();
        double lowOuterZ = base.get(0).z;
        double highOuterZ = base.get(1).z;
        double highInnerZ = base.get(2).z;
        double lowInnerZ = base.get(3).z;
        assertTrue(highOuterZ > lowOuterZ + 0.5d, "outer slope rises along depth");
        assertTrue(highInnerZ < highOuterZ - 0.1d, "inner high point sits below outer ridge");
        assertTrue(lowInnerZ < lowOuterZ - 0.1d, "inner low eave drops by thickness");
    }

    @Test
    void shedRoofTopologyUsesFourRealEdges() {
        RoofBaseProbe roof = new RoofBaseProbe();
        roof.connectInput("input_roof_type", NodeDataType.STRING);
        roof.setInput("input_face", sampleFace(10.0d, 8.0d));
        roof.setInput("input_roof_type", "shed");
        roof.setInput("input_height", 2.0d);
        roof.processNode(null);

        assertEquals(Boolean.TRUE, roof.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PathData> eaves = (List<PathData>) roof.getOutput("output_eaves");
        assertEquals(4, eaves.size(), "shed: low eave + high eave + left/right rakes");

        double highMidZ = eaves.stream()
            .mapToDouble(ArchitecturalRoofGeometryContractTest::pathMidZ)
            .max().orElseThrow();
        double lowMidZ = eaves.stream()
            .mapToDouble(ArchitecturalRoofGeometryContractTest::pathMidZ)
            .min().orElseThrow();
        assertTrue(highMidZ > 1.5d, "high eave should sit near roof height; midZ=" + highMidZ);
        assertTrue(lowMidZ < 0.25d, "low eave should sit near footprint; midZ=" + lowMidZ);

        // No false footprint edge at the high-depth side with both ends near z=0.
        boolean falseHighFootprint = eaves.stream().anyMatch(path -> {
            var line = path.getLine();
            if (line == null) {
                return false;
            }
            double midY = (line.start().y + line.end().y) * 0.5d;
            double maxZ = Math.max(line.start().z, line.end().z);
            return midY > 6.0d && maxZ < 0.25d;
        });
        assertFalse(falseHighFootprint, "shed must not emit footprint high-side edge at z≈0");
    }

    private static double pathMidZ(PathData path) {
        var line = path.getLine();
        assertNotNull(line);
        return (line.start().z + line.end().z) * 0.5d;
    }

    private static BoxFaceData sampleFace(double width, double height) {
        double halfW = width / 2.0d;
        List<Vector3d> corners = List.of(
            new Vector3d(-halfW, 0.0d, 0.0d),
            new Vector3d(halfW, 0.0d, 0.0d),
            new Vector3d(halfW, height, 0.0d),
            new Vector3d(-halfW, height, 0.0d)
        );
        return new BoxFaceData(0, "front", List.of(0, 1, 2, 3), corners,
            new Vector3d(0.0d, height / 2.0d, 0.0d), new Vector3d(0.0d, 0.0d, 1.0d));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
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

    private static final class RoofBaseProbe extends RoofBaseNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalRoofGeometryContractTest.connectInput(this, portId, outputType);
        }
    }
}
