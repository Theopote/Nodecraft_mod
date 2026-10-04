package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.geometry.solids.ExtractSurfaceStripRangeNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.ExtrudeProfileNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.ExtrudeRegionNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.LoftProfilesNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.MatchSeamMode;
import com.nodecraft.nodesystem.nodes.geometry.solids.MatchSectionsMode;
import com.nodecraft.nodesystem.nodes.geometry.solids.MorphBetweenProfilesNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.MultiSectionLoftNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.PushPullBoxFaceNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.SweepProfileAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.ThickenSurfaceNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.PathFrameUtils;
import com.nodecraft.nodesystem.util.SurfaceShellBuilder;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
 * Language fence for Geometry Solids / Surface Modeling.
 * Historical Graph V72/V91 residue; {@code GraphFormatVersion.CURRENT} is stamp-only 1.
 */
class GeometrySolidsLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsStampOnlyOne() {
        assertEquals(1, GraphFormatVersion.CURRENT);
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void v72CanonicalTwentyTwoSolidNodesRemainAtOrdersZeroToTwentyOne() {
        // Historical Graph V91 residue adds extrude_region at order 22; CURRENT is stamp-only 1.
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("geometry.solids."))
            .sorted()
            .toList();
        assertTrue(ids.size() >= 22);
        assertFalse(ids.contains("geometry.solids.extrude_profile"));
        assertFalse(ids.contains("geometry.solids.shell"));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("geometry.solids", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 22; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allSolidNodesHaveValidAndErrorWithoutRawListOrPolylinePorts() {
        List<String> errors = new ArrayList<>();
        for (String id : registry.getAllNodeIds().stream()
            .filter(s -> s.startsWith("geometry.solids."))
            .toList()) {
            INode node = registry.createNodeInstance(id);
            if (!hasPort(node, "output_valid")) {
                errors.add(id + " missing output_valid");
            }
            if (!hasPort(node, "output_error")) {
                errors.add(id + " missing output_error");
            }
            for (IPort port : node.getInputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(id + "." + port.getId() + " input is ANY");
                }
                if (port.getDataType() == NodeDataType.LIST) {
                    errors.add(id + "." + port.getId() + " uses raw LIST");
                }
                if (port.getDataType() == NodeDataType.POLYLINE) {
                    errors.add(id + "." + port.getId() + " uses graph POLYLINE");
                }
            }
            for (IPort port : node.getOutputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(id + "." + port.getId() + " output is ANY");
                }
                if (port.getDataType() == NodeDataType.LIST) {
                    errors.add(id + "." + port.getId() + " uses raw LIST");
                }
                if (port.getDataType() == NodeDataType.POLYLINE) {
                    errors.add(id + "." + port.getId() + " uses graph POLYLINE");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void sectionCutAndContourHaveNoLegacyGeometryMirrorInputs() {
        INode section = registry.createNodeInstance("geometry.solids.section_cut");
        INode contour = registry.createNodeInstance("geometry.solids.contour");
        for (INode node : List.of(section, contour)) {
            assertFalse(hasPort(node, "input_box_geometry"));
            assertFalse(hasPort(node, "input_cylinder_geometry"));
            assertFalse(hasPort(node, "input_sphere_geometry"));
            assertFalse(hasPort(node, "input_torus_geometry"));
            assertTrue(hasPort(node, "input_geometry"));
        }
    }

    @Test
    void thickenHasNoLatticeGeometryOutput() {
        ThickenSurfaceNode thicken = new ThickenSurfaceNode();
        assertFalse(hasPort(thicken, "output_geometry"));
        assertPortType("geometry.solids.thicken_surface", "output_side_caps", false, NodeDataType.SURFACE_STRIP_LIST);
        IPort endCaps = thicken.getOutputPorts().stream()
            .filter(port -> "output_side_caps".equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals("End Caps", endCaps.getDisplayName());
        assertPortType("geometry.solids.offset_surface_strip", "output_surface_strip", false, NodeDataType.SURFACE_STRIP);
        assertFalse(hasPort(registry.createNodeInstance("geometry.solids.offset_surface_strip"), "output_geometry"));
    }

    @Test
    void thickenThicknessMustBePositiveAndUnknownOffsetModeDoesNotSilentCenter() {
        ThickenSurfaceNode thicken = new ThickenSurfaceNode();
        thicken.setOffsetMode(SurfaceShellBuilder.OffsetMode.OUTSIDE);
        thicken.setOffsetModeString("not-a-mode");
        assertEquals(SurfaceShellBuilder.OffsetMode.OUTSIDE, thicken.getOffsetMode());
        thicken.setOffsetMode(null);
        assertEquals(SurfaceShellBuilder.OffsetMode.OUTSIDE, thicken.getOffsetMode());

        SurfaceStripData strip = threeSectionStrip();
        thicken.setInput("input_surface_strip", strip);
        connectInput(thicken, "input_thickness", NodeDataType.DOUBLE);
        thicken.setInput("input_thickness", 0.0d);
        thicken.processNode(null);
        assertEquals(Boolean.FALSE, thicken.getOutput("output_valid"));
        assertTrue(String.valueOf(thicken.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("thickness"));
    }

    @Test
    void extractNormalizedUUsesFloorCeilCover() {
        ExtractSurfaceStripRangeNode extract = new ExtractSurfaceStripRangeNode();
        extract.setInput("input_surface_strip", threeSectionStrip());
        connectInput(extract, "input_start", NodeDataType.DOUBLE);
        connectInput(extract, "input_end", NodeDataType.DOUBLE);
        extract.setInput("input_start", 0.4d);
        extract.setInput("input_end", 0.6d);
        extract.processNode(null);
        assertEquals(Boolean.TRUE, extract.getOutput("output_valid"),
            String.valueOf(extract.getOutput("output_error")));
        assertEquals(3, extract.getOutput("output_section_count"));
        assertEquals(0, extract.getOutput("output_start_section"));
        assertEquals(2, extract.getOutput("output_end_section"));
    }

    @Test
    void solidsCategoryDisplayNameIsSolidsAndSurfaces() {
        assertEquals("Solids & Surfaces", registry.getCategory("geometry.solids").getDisplayName());
        assertEquals("geometry.solids", registry.getCategory("geometry.solids").getId());
    }

    @Test
    void loftDefaultsToStrictMatchSectionsAndIndexSeam() {
        LoftProfilesNode loft = new LoftProfilesNode();
        assertEquals(MatchSectionsMode.STRICT, loft.getMatchSectionsMode());
        assertEquals(MatchSeamMode.INDEX, loft.getMatchSeamMode());
        MultiSectionLoftNode multi = new MultiSectionLoftNode();
        assertEquals(MatchSectionsMode.STRICT, multi.getMatchSectionsMode());
        assertEquals(MatchSeamMode.INDEX, multi.getMatchSeamMode());
        MorphBetweenProfilesNode morph = new MorphBetweenProfilesNode();
        assertEquals(MatchSeamMode.INDEX, morph.getMatchSeamMode());
    }

    @Test
    void extrudeInvalidDirectionFailsClosed() {
        ExtrudeProfileNode extrude = new ExtrudeProfileNode();
        connectInput(extrude, "input_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(extrude, "input_direction", NodeDataType.VECTOR);
        extrude.setInput("input_profile", unitSquareProfile());
        extrude.setInput("input_direction", new com.nodecraft.nodesystem.datatypes.VectorData(0, 0, 0));
        extrude.processNode(null);
        assertEquals(Boolean.FALSE, extrude.getOutput("output_valid"));
        assertNotNull(extrude.getOutput("output_error"));
        assertFalse(String.valueOf(extrude.getOutput("output_error")).isBlank());
        assertTrue(Double.isNaN((Double) extrude.getOutput("output_height")));
    }

    @Test
    void extrudeHugeDirectionFailsClosed() {
        ExtrudeProfileNode extrude = new ExtrudeProfileNode();
        connectInput(extrude, "input_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(extrude, "input_direction", NodeDataType.VECTOR);
        extrude.setInput("input_profile", unitSquareProfile());
        extrude.setInput("input_direction", new com.nodecraft.nodesystem.datatypes.VectorData(1e308d, 1e308d, 1e308d));
        extrude.processNode(null);
        assertEquals(Boolean.FALSE, extrude.getOutput("output_valid"));
        assertNull(extrude.getOutput("output_geometry"));
        assertTrue(Double.isNaN((Double) extrude.getOutput("output_height")));
    }

    @Test
    void extrudeRawVector3dOnVectorPortFailsClosed() {
        ExtrudeProfileNode extrude = new ExtrudeProfileNode();
        connectInput(extrude, "input_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(extrude, "input_direction", NodeDataType.VECTOR);
        extrude.setInput("input_profile", unitSquareProfile());
        extrude.setInput("input_direction", new Vector3d(0, 1, 0));
        extrude.processNode(null);
        assertEquals(Boolean.FALSE, extrude.getOutput("output_valid"));
        assertTrue(Double.isNaN((Double) extrude.getOutput("output_height")));
    }

    @Test
    void extrudeValidHeightIsFiniteIffValid() {
        ExtrudeProfileNode extrude = new ExtrudeProfileNode();
        connectInput(extrude, "input_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(extrude, "input_direction", NodeDataType.VECTOR);
        extrude.setInput("input_profile", unitSquareProfile());
        extrude.setInput("input_direction", new com.nodecraft.nodesystem.datatypes.VectorData(0, 2, 0));
        extrude.processNode(null);
        assertEquals(Boolean.TRUE, extrude.getOutput("output_valid"), String.valueOf(extrude.getOutput("output_error")));
        double height = (Double) extrude.getOutput("output_height");
        assertTrue(Double.isFinite(height));
        assertEquals(2.0d, height, 1.0e-9d);
    }

    @Test
    void morphRejectsOutOfRangeT() {
        MorphBetweenProfilesNode morph = new MorphBetweenProfilesNode();
        connectInput(morph, "input_source_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(morph, "input_target_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(morph, "input_t", NodeDataType.DOUBLE);
        morph.setInput("input_source_profile", unitSquareProfile());
        morph.setInput("input_target_profile", unitSquareProfile());
        morph.setInput("input_t", 1.5d);
        morph.processNode(null);
        assertEquals(Boolean.FALSE, morph.getOutput("output_valid"));
        assertNull(morph.getOutput("output_profile"));
    }

    @Test
    void multiLoftRejectsInvalidProfileListMember() {
        MultiSectionLoftNode multi = new MultiSectionLoftNode();
        connectInput(multi, "input_profiles", NodeDataType.POLYGON_PROFILE_LIST);
        List<Object> bad = new ArrayList<>();
        bad.add(unitSquareProfile());
        bad.add("garbage");
        bad.add(unitSquareProfile());
        multi.setInput("input_profiles", bad);
        multi.processNode(null);
        assertEquals(Boolean.FALSE, multi.getOutput("output_valid"));
    }

    @Test
    void pushPullRejectsCollapsingBox() {
        // Box full size Y=10 (halfExtent=5); face 1 = +Y.
        // distance=-10 → newHalf=0 → collapse; distance=-9 → newHalf=0.5 → valid.
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(5, 5, 5));

        PushPullBoxFaceNode collapse = new PushPullBoxFaceNode();
        connectInput(collapse, "input_distance", NodeDataType.DOUBLE);
        collapse.setInput("input_box_geometry", box);
        collapse.setInput("input_face_index", 1);
        collapse.setInput("input_distance", -10.0d);
        collapse.processNode(null);
        assertEquals(Boolean.FALSE, collapse.getOutput("output_valid"));
        assertNull(collapse.getOutput("output_box_geometry"));
        assertNull(collapse.getOutput("output_geometry"));
        assertNotNull(collapse.getOutput("output_error"));
        assertFalse(String.valueOf(collapse.getOutput("output_error")).isBlank());
        assertTrue(String.valueOf(collapse.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("collapse") || String.valueOf(collapse.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("invert"));

        PushPullBoxFaceNode keep = new PushPullBoxFaceNode();
        connectInput(keep, "input_distance", NodeDataType.DOUBLE);
        keep.setInput("input_box_geometry", box);
        keep.setInput("input_face_index", 1);
        keep.setInput("input_distance", -9.0d);
        keep.processNode(null);
        assertEquals(Boolean.TRUE, keep.getOutput("output_valid"));
        assertInstanceOf(BoxGeometryData.class, keep.getOutput("output_box_geometry"));
        BoxGeometryData result = (BoxGeometryData) keep.getOutput("output_box_geometry");
        assertEquals(0.5d, result.getHalfExtents().y, 1.0e-9d);
    }

    @Test
    void surfaceStripListTypeExists() {
        assertEquals(NodeDataType.SURFACE_STRIP_LIST, NodeDataType.forListElementKind(
            com.nodecraft.nodesystem.api.ListElementKind.SURFACE_STRIP));
    }

    @Test
    void sweepRejectsNonPositiveScalePropertyAndConnectedList() {
        SweepProfileAlongPathNode sweep = new SweepProfileAlongPathNode();
        sweep.setStartScale(-1.0d);
        assertEquals(1.0d, sweep.getStartScale(), 1.0e-12d);
        sweep.setStartScale(Double.NaN);
        assertEquals(1.0d, sweep.getStartScale(), 1.0e-12d);
        sweep.setStartRotationDegrees(Double.POSITIVE_INFINITY);
        assertEquals(0.0d, sweep.getStartRotationDegrees(), 1.0e-12d);

        connectInput(sweep, "input_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(sweep, "input_path", NodeDataType.PATH);
        connectInput(sweep, "input_scale_values", NodeDataType.DOUBLE_LIST);
        sweep.setInput("input_profile", unitSquareProfile());
        sweep.setInput("input_path", openLinePath());
        sweep.setInput("input_scale_values", List.of(-1.0d, 2.0d));
        sweep.processNode(null);
        assertEquals(Boolean.FALSE, sweep.getOutput("output_valid"));
        assertNull(sweep.getOutput("output_surface_strip"));
    }

    @Test
    void closedSweepFramesAreHolonomyCorrected() {
        List<Vector3d> closedSquare = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(4, 0, 0),
            new Vector3d(4, 4, 0),
            new Vector3d(0, 4, 0),
            new Vector3d(0, 0, 0)
        );
        List<PathFrameUtils.Frame> frames = PathFrameUtils.framesAlongSpine(closedSquare, null);
        assertNotNull(frames);
        assertEquals(5, frames.size());
        PathFrameUtils.Frame first = frames.getFirst();
        PathFrameUtils.Frame lastUnique = frames.get(3);
        PathFrameUtils.Frame probe = PathFrameUtils.transport(lastUnique, first.origin(), first.zAxis());
        assertEquals(1.0d, first.xAxis().dot(probe.xAxis()), 1.0e-5d);

        SweepProfileAlongPathNode sweep = new SweepProfileAlongPathNode();
        connectInput(sweep, "input_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(sweep, "input_path", NodeDataType.PATH);
        sweep.setInput("input_profile", unitSquareProfile());
        sweep.setInput("input_path", closedSquarePath());
        sweep.processNode(null);
        assertEquals(Boolean.TRUE, sweep.getOutput("output_valid"), String.valueOf(sweep.getOutput("output_error")));
        assertInstanceOf(SurfaceStripData.class, sweep.getOutput("output_surface_strip"));
    }

    @Test
    void autoSeamIsOptInVersusDefaultIndex() {
        PolygonProfileData source = unitSquareProfile();
        PolygonProfileData target = shiftedSquareProfile();

        LoftProfilesNode index = new LoftProfilesNode();
        index.setInput("input_source_profile", source);
        index.setInput("input_target_profile", target);
        index.processNode(null);
        assertEquals(Boolean.TRUE, index.getOutput("output_valid"), String.valueOf(index.getOutput("output_error")));
        double indexLength = railLength(index.getOutput("output_rail_segments"));

        LoftProfilesNode auto = new LoftProfilesNode();
        auto.setMatchSeamMode(MatchSeamMode.AUTO_SEAM);
        auto.setInput("input_source_profile", source);
        auto.setInput("input_target_profile", target);
        auto.processNode(null);
        assertEquals(Boolean.TRUE, auto.getOutput("output_valid"), String.valueOf(auto.getOutput("output_error")));
        double autoLength = railLength(auto.getOutput("output_rail_segments"));
        assertTrue(autoLength + 1.0e-6d < indexLength);
    }

    @Test
    void extrudeRegionEmitsTopAndSideSurfaces() {
        ExtrudeRegionNode extrude = new ExtrudeRegionNode();
        connectInput(extrude, "input_region", NodeDataType.PLANAR_REGION);
        connectInput(extrude, "input_direction", NodeDataType.VECTOR);
        extrude.setInput("input_region", PlanarRegionData.of(unitSquareProfile()));
        extrude.setInput("input_direction", new com.nodecraft.nodesystem.datatypes.VectorData(0, 2, 0));
        extrude.processNode(null);
        assertEquals(Boolean.TRUE, extrude.getOutput("output_valid"), String.valueOf(extrude.getOutput("output_error")));
        assertInstanceOf(PlanarRegionData.class, extrude.getOutput("output_top_region"));
        assertInstanceOf(SurfaceStripData.class, extrude.getOutput("output_outer_side_surface"));
        assertInstanceOf(List.class, extrude.getOutput("output_hole_side_surfaces"));
        assertTrue(((List<?>) extrude.getOutput("output_hole_side_surfaces")).isEmpty());
        assertEquals(2.0d, (Double) extrude.getOutput("output_height"), 1.0e-9d);
    }

    @Test
    void surfaceStripConstructorRejectsZeroLengthEdges() {
        List<Vector3d> degenerate = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(0, 0, 0),
            new Vector3d(1, 0, 0)
        );
        List<Vector3d> ok = List.of(
            new Vector3d(0, 1, 0),
            new Vector3d(1, 1, 0),
            new Vector3d(1, 1, 1)
        );
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new SurfaceStripData(List.of(degenerate, ok), List.of(false, false)));
    }

    private static SurfaceStripData threeSectionStrip() {
        List<List<Vector3d>> sections = List.of(
            List.of(new Vector3d(0, 0, 0), new Vector3d(2, 0, 0)),
            List.of(new Vector3d(0, 1, 0), new Vector3d(2, 1, 0)),
            List.of(new Vector3d(0, 2, 0), new Vector3d(2, 2, 0))
        );
        return new SurfaceStripData(sections, List.of(false, false, false));
    }

    private static PolygonProfileData unitSquareProfile() {
        List<Vector3d> closed = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(4, 0, 0),
            new Vector3d(4, 0, 4),
            new Vector3d(0, 0, 4),
            new Vector3d(0, 0, 0)
        );
        return new PolygonProfileData(closed, PlaneData.XZ_PLANE);
    }

    private static PolygonProfileData shiftedSquareProfile() {
        List<Vector3d> closed = List.of(
            new Vector3d(4, 2, 0),
            new Vector3d(4, 2, 4),
            new Vector3d(0, 2, 4),
            new Vector3d(0, 2, 0),
            new Vector3d(4, 2, 0)
        );
        return new PolygonProfileData(closed, new PlaneData(new Vector3d(0, 2, 0), new Vector3d(0, 1, 0)));
    }

    private static PathData openLinePath() {
        return PathData.fromPolyline(new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(0, 4, 0)
        )));
    }

    private static PathData closedSquarePath() {
        return PathData.fromPolyline(new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(4, 0, 0),
            new Vec3d(4, 4, 0),
            new Vec3d(0, 4, 0),
            new Vec3d(0, 0, 0)
        )));
    }

    @SuppressWarnings("unchecked")
    private static double railLength(Object railsObj) {
        List<LineData> rails = (List<LineData>) railsObj;
        double total = 0.0d;
        for (LineData rail : rails) {
            total += rail.getLength();
        }
        return total;
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
        target.getInput(inputPortId);
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        IPort port = findPort(node, portId, input);
        assertNotNull(port, typeId + " missing " + portId);
        assertEquals(expected, port.getDataType());
    }

    private static boolean hasPort(INode node, String portId) {
        return findPort(node, portId, true) != null || findPort(node, portId, false) != null;
    }

    private static IPort findPort(INode node, String portId, boolean input) {
        List<IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
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
