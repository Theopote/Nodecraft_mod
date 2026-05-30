
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds clipped 2D Voronoi cells in a plane from 3D sites projected into that plane (JTS).
 */
@NodeInfo(
    id = "geometry.profiles.voronoi_cells_plane",
    displayName = "Voronoi Cells 2D On Plane",
    description = "Projects sites into a plane, builds a clipped planar Voronoi diagram (JTS), and outputs each cell as a polygon profile on the plane",
    category = "geometry.profiles",
    order = 6
)
public class VoronoiCells2DOnPlaneNode extends BaseNode {

    private static final double DEDUPE_GRID = 1.0e-6d;

    @NodeProperty(displayName = "Clip Margin", category = "Voronoi", order = 1,
        description = "Extra padding (plane UV units) applied around projected sites for the clip rectangle")
    private double clipMargin = 2.0d;

    @NodeProperty(displayName = "Max Sites", category = "Voronoi", order = 2,
        description = "Maximum number of unique projected sites processed (safety cap)")
    private int maxSites = 512;

    private static final String INPUT_SITES_ID = "input_sites";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_CELLS_ID = "output_cells";
    private static final String OUTPUT_CELL_COUNT_ID = "output_cell_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VoronoiCells2DOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.voronoi_cells_plane");

        addInputPort(new BasePort(INPUT_SITES_ID, "Sites",
            "Site positions (list or single Point / Vector / BlockPos)",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Plane used for projection and polygon embedding",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_CELLS_ID, "Cells",
            "List of polygon profiles, one per Voronoi cell (clipped)",
            NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CELL_COUNT_ID, "Cell Count",
            "Number of polygon cells emitted",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a diagram was built with at least one cell",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Voronoi Cells 2D On Plane";
    }

    @Override
    public String getDescription() {
        return "Projects sites into a plane, builds a clipped planar Voronoi diagram (JTS), and outputs each cell as a polygon profile on the plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane)) {
            writeInvalid();
            return;
        }

        List<Vector3d> world = collectPoints(inputValues.get(INPUT_SITES_ID));
        if (world.isEmpty()) {
            writeInvalid();
            return;
        }

        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        List<Vector2d> uvSites = new ArrayList<>();
        for (Vector3d p : world) {
            Vector3d proj = plane.projectPoint(p);
            uvSites.add(axes.to2d(proj));
        }

        List<Vector2d> uniqueUv = dedupeUv(uvSites);
        if (uniqueUv.isEmpty()) {
            writeInvalid();
            return;
        }
        if (uniqueUv.size() > Math.max(1, maxSites)) {
            writeInvalid();
            return;
        }

        double minU = uniqueUv.stream().mapToDouble(p -> p.x).min().orElse(0.0d);
        double maxU = uniqueUv.stream().mapToDouble(p -> p.x).max().orElse(0.0d);
        double minV = uniqueUv.stream().mapToDouble(p -> p.y).min().orElse(0.0d);
        double maxV = uniqueUv.stream().mapToDouble(p -> p.y).max().orElse(0.0d);
        double m = Math.max(0.0d, clipMargin);
        if (minU == maxU) {
            minU -= m;
            maxU += m;
        } else {
            minU -= m;
            maxU += m;
        }
        if (minV == maxV) {
            minV -= m;
            maxV += m;
        } else {
            minV -= m;
            maxV += m;
        }

        List<Coordinate> coords = new ArrayList<>(uniqueUv.size());
        for (Vector2d uv : uniqueUv) {
            coords.add(new Coordinate(uv.x, uv.y));
        }

        VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
        builder.setSites(coords);
        builder.setClipEnvelope(new Envelope(minU, maxU, minV, maxV));

        GeometryFactory gf = new GeometryFactory();
        Geometry diagram = builder.getDiagram(gf);
        List<PolygonProfileData> cells = new ArrayList<>();

        appendPolygons(diagram, axes, plane, cells);

        if (cells.isEmpty()) {
            writeInvalid();
            return;
        }

        List<Object> asObjects = new ArrayList<>(cells.size());
        asObjects.addAll(cells);
        outputValues.put(OUTPUT_CELLS_ID, asObjects);
        outputValues.put(OUTPUT_CELL_COUNT_ID, cells.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static void appendPolygons(Geometry geometry,
                                       PlaneProjectionUtils.PlaneAxes axes,
                                       PlaneData plane,
                                       List<PolygonProfileData> out) {
        if (geometry == null || geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon polygon) {
            PolygonProfileData cell = toProfile(polygon, axes, plane);
            if (cell != null) {
                out.add(cell);
            }
            return;
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                appendPolygons(collection.getGeometryN(i), axes, plane, out);
            }
        }
    }

    private static @Nullable PolygonProfileData toProfile(Polygon polygon,
                                                          PlaneProjectionUtils.PlaneAxes axes,
                                                          PlaneData plane) {
        LineString ring = polygon.getExteriorRing();
        if (ring == null || ring.isEmpty()) {
            return null;
        }
        Coordinate[] coords = ring.getCoordinates();
        if (coords.length < 4) {
            return null;
        }

        List<Vector3d> closed = new ArrayList<>(coords.length);
        for (Coordinate c : coords) {
            closed.add(axes.from2d(new Vector2d(c.x, c.y)));
        }
        Vector3d first = closed.get(0);
        Vector3d last = closed.get(closed.size() - 1);
        if (first.distanceSquared(last) > 1.0e-8d) {
            closed.add(new Vector3d(first));
        } else if (closed.size() < 4) {
            return null;
        }

        try {
            return new PolygonProfileData(closed, plane);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_CELLS_ID, List.of());
        outputValues.put(OUTPUT_CELL_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static List<Vector3d> collectPoints(Object value) {
        List<Vector3d> out = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object entry : collection) {
                Vector3d p = resolvePoint(entry);
                if (p != null) {
                    out.add(p);
                }
            }
        } else {
            Vector3d p = resolvePoint(value);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    private static Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pd) {
            return new Vector3d(pd.getPosition());
        }
        if (value instanceof Vector3d v) {
            return new Vector3d(v);
        }
        if (value instanceof BlockPos bp) {
            return new Vector3d(bp.getX(), bp.getY(), bp.getZ());
        }
        return null;
    }

    private static List<Vector2d> dedupeUv(List<Vector2d> input) {
        Set<String> seen = new LinkedHashSet<>();
        List<Vector2d> out = new ArrayList<>();
        for (Vector2d p : input) {
            String key = quant(p.x) + ":" + quant(p.y);
            if (seen.add(key)) {
                out.add(new Vector2d(p));
            }
        }
        out.sort(Comparator.comparingDouble((Vector2d p) -> p.x).thenComparingDouble(p -> p.y));
        return out;
    }

    private static String quant(double v) {
        long q = Math.round(v / DEDUPE_GRID);
        return Long.toString(q);
    }

    public double getClipMargin() {
        return clipMargin;
    }

    public void setClipMargin(double clipMargin) {
        if (Double.isFinite(clipMargin) && this.clipMargin != clipMargin) {
            this.clipMargin = clipMargin;
            markDirty();
        }
    }

    public int getMaxSites() {
        return maxSites;
    }

    public void setMaxSites(int maxSites) {
        int v = Math.max(1, maxSites);
        if (this.maxSites != v) {
            this.maxSites = v;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "clipMargin", clipMargin,
            "maxSites", maxSites
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("clipMargin") instanceof Number n) {
            setClipMargin(n.doubleValue());
        }
        if (map.get("maxSites") instanceof Number n) {
            setMaxSites(n.intValue());
        }
    }
}
