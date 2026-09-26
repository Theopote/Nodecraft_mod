package com.nodecraft.nodesystem.nodes.pattern.voronoi_3d;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.Voronoi3DGridLloyd;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.voronoi_3d.lloyd_relax",
    displayName = "Lloyd Relax 3D",
    description = "Approximates Lloyd relaxation inside an axis-aligned 3D box using a uniform sampling grid. Not an exact Voronoi diagram.",
    category = "pattern.voronoi_3d",
    order = 1
)
public class Voronoi3DLloydRelaxNode extends BaseNode {

    private static final double BOUNDS_EPS = 1.0e-12d;
    private static final double SITE_DISTINCT_EPS_SQ = 1.0e-12d;

    @NodeProperty(displayName = "Cells Per Axis", category = "Grid", order = 1,
        description = "Resolution of the internal uniform grid (higher = slower, more accurate)")
    private int cellsPerAxis = 24;

    @NodeProperty(displayName = "Iterations", category = "Lloyd", order = 2)
    private int iterations = 4;

    private static final String INPUT_SITES_ID = "input_sites";
    private static final String INPUT_CORNER_A_ID = "input_corner_a";
    private static final String INPUT_CORNER_B_ID = "input_corner_b";
    private static final String INPUT_CELLS_ID = "input_cells";
    private static final String INPUT_ITERATIONS_ID = "input_iterations";

    private static final String OUTPUT_SITES_ID = "output_sites";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public Voronoi3DLloydRelaxNode() {
        super(UUID.randomUUID(), "pattern.voronoi_3d.lloyd_relax");

        addInputPort(new BasePort(INPUT_SITES_ID, "Sites", "Seed site positions", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_CORNER_A_ID, "Corner A", "First corner of the axis-aligned bounds box", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_CORNER_B_ID, "Corner B", "Second corner of the axis-aligned bounds box", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_CELLS_ID, "Cells", "Grid cells per axis (optional override)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations", "Lloyd rounds (optional override)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_SITES_ID, "Sites", "Relaxed site positions", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of sites", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when relaxation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Approximates Lloyd relaxation inside an axis-aligned 3D box using a uniform sampling grid. Not an exact Voronoi diagram.";
    }

    @Override
    public String getDisplayName() {
        return "Lloyd Relax 3D";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d cornerA = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_CORNER_A_ID));
        Vector3d cornerB = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_CORNER_B_ID));
        List<Vector3d> sites = resolveValidatedPointList(inputValues.get(INPUT_SITES_ID));

        if (cornerA == null || cornerB == null || sites == null) {
            writeInvalid();
            return;
        }
        if (!isFinite(cornerA) || !isFinite(cornerB)) {
            writeInvalid();
            return;
        }

        Vector3d min = new Vector3d(cornerA);
        Vector3d max = new Vector3d(cornerB);
        normalizeBounds(min, max);

        if (max.x - min.x <= BOUNDS_EPS || max.y - min.y <= BOUNDS_EPS || max.z - min.z <= BOUNDS_EPS) {
            writeInvalid();
            return;
        }

        if (sites.size() > GenerationLimits.MAX_VORONOI_LLOYD_SITES) {
            writeInvalid();
            return;
        }
        if (!sitesInsideBounds(sites, min, max)) {
            writeInvalid();
            return;
        }
        if (hasDuplicateSites(sites)) {
            writeInvalid();
            return;
        }

        int cells = getInputInteger(INPUT_CELLS_ID, cellsPerAxis);
        int iters = getInputInteger(INPUT_ITERATIONS_ID, iterations);
        if (cells < GenerationLimits.MIN_VORONOI_LLOYD_CELLS_PER_AXIS
                || cells > GenerationLimits.MAX_VORONOI_LLOYD_CELLS_PER_AXIS
                || iters < 0
                || iters > GenerationLimits.MAX_VORONOI_LLOYD_ITERATIONS) {
            writeInvalid();
            return;
        }
        if (GenerationLimits.exceedsLloydWorkBudget(cells, sites.size(), iters)) {
            writeInvalid();
            return;
        }

        if (iters == 0) {
            writeSuccess(copySites(sites));
            return;
        }

        List<Vector3d> relaxed = Voronoi3DGridLloyd.relax(min, max, sites, cells, iters);
        if (relaxed.isEmpty()) {
            writeInvalid();
            return;
        }
        writeSuccess(relaxed);
    }

    private void writeSuccess(List<Vector3d> sites) {
        outputValues.put(OUTPUT_SITES_ID, SpatialValueResolver.toPointDataList(sites));
        outputValues.put(OUTPUT_COUNT_ID, sites.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_SITES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private int getInputInteger(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Integer i ? i : fallback;
    }

    private static void normalizeBounds(Vector3d min, Vector3d max) {
        if (min.x > max.x) {
            swapAxis(min, max, 'x');
        }
        if (min.y > max.y) {
            swapAxis(min, max, 'y');
        }
        if (min.z > max.z) {
            swapAxis(min, max, 'z');
        }
    }

    private static void swapAxis(Vector3d min, Vector3d max, char axis) {
        double temp = switch (axis) {
            case 'x' -> min.x;
            case 'y' -> min.y;
            default -> min.z;
        };
        switch (axis) {
            case 'x' -> {
                min.x = max.x;
                max.x = temp;
            }
            case 'y' -> {
                min.y = max.y;
                max.y = temp;
            }
            default -> {
                min.z = max.z;
                max.z = temp;
            }
        }
    }

    private static boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static boolean sitesInsideBounds(List<Vector3d> sites, Vector3d min, Vector3d max) {
        for (Vector3d site : sites) {
            if (site.x < min.x || site.x > max.x
                    || site.y < min.y || site.y > max.y
                    || site.z < min.z || site.z > max.z) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDuplicateSites(List<Vector3d> sites) {
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                if (sites.get(i).distanceSquared(sites.get(j)) <= SITE_DISTINCT_EPS_SQ) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Vector3d> copySites(List<Vector3d> sites) {
        List<Vector3d> copied = new ArrayList<>(sites.size());
        for (Vector3d site : sites) {
            copied.add(new Vector3d(site));
        }
        return copied;
    }

    private static @Nullable List<Vector3d> resolveValidatedPointList(@Nullable Object value) {
        if (!(value instanceof List<?> rawSites) || rawSites.isEmpty()) {
            return null;
        }
        List<Vector3d> sites = new ArrayList<>(rawSites.size());
        for (Object entry : rawSites) {
            Vector3d resolved = SpatialValueResolver.resolvePoint(entry);
            if (resolved == null || !isFinite(resolved)) {
                return null;
            }
            sites.add(new Vector3d(resolved));
        }
        return sites;
    }
}
