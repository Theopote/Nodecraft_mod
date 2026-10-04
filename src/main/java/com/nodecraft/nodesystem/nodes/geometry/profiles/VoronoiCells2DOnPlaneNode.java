package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.voronoi_cells_plane",
    displayName = "Voronoi Cells 2D On Plane",
    description = "Projects sites into a plane, builds a clipped planar Voronoi diagram (JTS), and outputs each cell as a polygon profile on the plane",
    category = "geometry.profiles",
    order = 19
)
public class VoronoiCells2DOnPlaneNode extends AbstractProfileNode {

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

    public VoronoiCells2DOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.voronoi_cells_plane");

        addInputPort(new BasePort(INPUT_SITES_ID, "Sites",
            "Site positions for Voronoi diagram",
            NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Plane used for projection and polygon embedding. Defaults to XZ (horizontal)",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_CELLS_ID, "Cells",
            "Polygon profiles, one per Voronoi cell (clipped)",
            NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CELL_COUNT_ID, "Cell Count",
            "Number of polygon cells emitted",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a diagram was built with at least one cell",
            NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
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
        PlaneData plane = resolveConstructionPlane(INPUT_PLANE_ID);
        if (plane == null) {
            writeFailure("Plane is invalid");
            return;
        }
        if (!Double.isFinite(clipMargin) || clipMargin < 0.0d) {
            writeFailure("Clip margin must be a non-negative finite number");
            return;
        }

        List<Vector3d> world = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_SITES_ID));
        if (world.isEmpty()) {
            writeFailure("At least one site is required");
            return;
        }

        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        List<Vector2d> uvSites = new ArrayList<>();
        for (Vector3d p : world) {
            if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) {
                writeFailure("Sites must be finite points");
                return;
            }
            Vector3d proj = plane.projectPoint(p);
            uvSites.add(axes.to2d(proj));
        }

        List<Vector2d> uniqueUv = dedupeUv(uvSites);
        if (uniqueUv.isEmpty()) {
            writeFailure("No unique sites remain after de-duplication");
            return;
        }
        if (uniqueUv.size() > maxSites) {
            writeFailure("Site count exceeds max sites (" + maxSites + ")");
            return;
        }

        double minU = uniqueUv.stream().mapToDouble(p -> p.x).min().orElse(0.0d);
        double maxU = uniqueUv.stream().mapToDouble(p -> p.x).max().orElse(0.0d);
        double minV = uniqueUv.stream().mapToDouble(p -> p.y).min().orElse(0.0d);
        double maxV = uniqueUv.stream().mapToDouble(p -> p.y).max().orElse(0.0d);
        minU -= clipMargin;
        maxU += clipMargin;
        minV -= clipMargin;
        maxV += clipMargin;

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
        String conversionError = appendPolygons(diagram, axes, plane, cells);
        if (conversionError != null) {
            writeFailure(conversionError);
            return;
        }
        if (cells.isEmpty()) {
            writeFailure("Voronoi diagram produced no cells");
            return;
        }

        String budgetError = ProfilePlanarOps.validateOutputBudget(cells);
        if (budgetError != null) {
            writeFailure(budgetError);
            return;
        }

        outputValues.put(OUTPUT_CELLS_ID, new ArrayList<>(cells));
        outputValues.put(OUTPUT_CELL_COUNT_ID, cells.size());
        markSuccess();
    }

    private static @Nullable String appendPolygons(
            Geometry geometry,
            PlaneProjectionUtils.PlaneAxes axes,
            PlaneData plane,
            List<PolygonProfileData> out
    ) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (geometry instanceof Polygon polygon) {
            return ProfilePlanarOps.fromJtsPolygon(polygon, axes, plane, out);
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                String error = appendPolygons(collection.getGeometryN(i), axes, plane, out);
                if (error != null) {
                    return error;
                }
            }
            return null;
        }
        return "Voronoi result is not a polygon";
    }

    private void writeFailure(String error) {
        putEmptyListOutputs(OUTPUT_CELLS_ID);
        putIntOutputs(0, OUTPUT_CELL_COUNT_ID);
        markInvalid(error);
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
        if (maxSites >= 1 && this.maxSites != maxSites) {
            this.maxSites = maxSites;
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
        restoreFiniteDouble(map, "clipMargin", this::setClipMargin);
        restoreInteger(map, "maxSites", this::setMaxSites);
    }
}
