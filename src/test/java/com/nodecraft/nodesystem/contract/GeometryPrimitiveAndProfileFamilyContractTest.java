package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Family freeze fence for {@code geometry.primitives.*} and {@code geometry.profiles.*}.
 * Guards against regressions to spatial ANY, location-as-VECTOR, VECTOR_LIST points, and XY defaults.
 */
class GeometryPrimitiveAndProfileFamilyContractTest {

    private static final Set<String> LOCATION_INPUT_HINTS = Set.of(
            "center", "start", "end", "apex", "corner", "point_a", "point_b", "base_center", "top_center"
    );
    private static final Set<String> LOCATION_OUTPUT_HINTS = Set.of(
            "center", "start", "end", "apex", "base_center", "top_center", "base_points",
            "top_points", "points", "corners", "vertices", "outer_points", "inner_points"
    );
    private static final Set<String> DIRECTION_HINTS = Set.of(
            "axis", "direction", "normal", "x_axis", "y_axis", "z_axis", "half_extents",
            "radii", "diameters", "extrusion", "translation"
    );
    private static final Set<String> ALLOWED_ANY_PORT_IDS = Set.of(
            "input_orientation"
    );

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void geometryPrimitiveAndProfileNodesAvoidSpatialAnyExceptOrientation() {
        List<String> errors = new ArrayList<>();
        for (String typeId : geometryFamilyIds()) {
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            if (node == null) {
                errors.add("missing instance: " + typeId);
                continue;
            }
            for (IPort port : node.getInputPorts()) {
                if (port.getDataType() != NodeDataType.ANY) {
                    continue;
                }
                if (ALLOWED_ANY_PORT_IDS.contains(port.getId())) {
                    continue;
                }
                errors.add(typeId + "." + port.getId() + " is ANY");
            }
            for (IPort port : node.getOutputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(typeId + "." + port.getId() + " output is ANY");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void locationPortsUsePointLanguageNotVector() {
        List<String> errors = new ArrayList<>();
        for (String typeId : geometryFamilyIds()) {
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            if (node == null) {
                continue;
            }
            for (IPort port : node.getInputPorts()) {
                if (isLocationPort(port.getId()) && port.getDataType() == NodeDataType.VECTOR) {
                    errors.add(typeId + "." + port.getId() + " location input typed VECTOR");
                }
                if (isLocationPort(port.getId()) && port.getDataType() == NodeDataType.ANY) {
                    errors.add(typeId + "." + port.getId() + " location input typed ANY");
                }
                if (isLocationListPort(port.getId()) && port.getDataType() == NodeDataType.VECTOR_LIST) {
                    errors.add(typeId + "." + port.getId() + " location list input typed VECTOR_LIST");
                }
            }
            for (IPort port : node.getOutputPorts()) {
                if (isLocationPort(port.getId()) && port.getDataType() == NodeDataType.VECTOR
                        && !isDirectionPort(port.getId())) {
                    errors.add(typeId + "." + port.getId() + " location output typed VECTOR");
                }
                if (isLocationListPort(port.getId()) && port.getDataType() == NodeDataType.VECTOR_LIST) {
                    errors.add(typeId + "." + port.getId() + " location list output typed VECTOR_LIST");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void resolvePointListAcceptsPointDataAndVector3d() {
        List<Object> mixed = List.of(
                new PointData(1.0d, 2.0d, 3.0d),
                new Vector3d(4.0d, 5.0d, 6.0d)
        );
        List<Vector3d> resolved = SpatialValueResolver.resolvePointList(mixed);
        assertEquals(2, resolved.size());
        assertEquals(1.0d, resolved.get(0).x, 1e-9);
        assertEquals(4.0d, resolved.get(1).x, 1e-9);

        List<PointData> roundTrip = SpatialValueResolver.toPointDataList(resolved);
        assertEquals(2, roundTrip.size());
        assertEquals(2.0d, roundTrip.get(0).getY(), 1e-9);
    }

    @Test
    void defaultPlaneConstantIsHorizontalXz() {
        Vector3d normal = PlaneData.XZ_PLANE.getNormal();
        assertTrue(Math.abs(normal.y) > 0.9d);
        assertFalse(Math.abs(PlaneData.XY_PLANE.getNormal().y) > 0.9d);
    }

    private static List<String> geometryFamilyIds() {
        List<String> ids = new ArrayList<>();
        for (String id : NodeRegistry.getInstance().getAllNodeIds()) {
            if (id.startsWith("geometry.primitives.") || id.startsWith("geometry.profiles.")) {
                ids.add(id);
            }
        }
        assertFalse(ids.isEmpty(), "expected registered geometry.primitives / geometry.profiles nodes");
        return ids;
    }

    private static boolean isLocationPort(String portId) {
        String id = normalize(portId);
        for (String hint : LOCATION_INPUT_HINTS) {
            if (id.contains(hint) && !isDirectionPort(portId)) {
                return true;
            }
        }
        for (String hint : LOCATION_OUTPUT_HINTS) {
            if (id.equals("output_" + hint) || id.endsWith("_" + hint) || id.contains(hint)) {
                if (!isDirectionPort(portId) && !id.contains("list") && !id.endsWith("points")
                        && !id.contains("vertices") && !id.contains("corners")) {
                    return id.contains("center") || id.contains("start") || id.contains("end")
                            || id.contains("apex") || id.contains("corner");
                }
            }
        }
        return id.contains("center") && !id.contains("axis")
                || id.endsWith("_start") || id.endsWith("_end")
                || id.contains("apex")
                || (id.contains("corner") && !id.contains("count"));
    }

    private static boolean isLocationListPort(String portId) {
        String id = normalize(portId);
        return id.contains("points") || id.contains("vertices") || id.contains("corners")
                || id.contains("base_points") || id.contains("top_points");
    }

    private static boolean isDirectionPort(String portId) {
        String id = normalize(portId);
        for (String hint : DIRECTION_HINTS) {
            if (id.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String portId) {
        return portId == null ? "" : portId.toLowerCase(Locale.ROOT);
    }
}
