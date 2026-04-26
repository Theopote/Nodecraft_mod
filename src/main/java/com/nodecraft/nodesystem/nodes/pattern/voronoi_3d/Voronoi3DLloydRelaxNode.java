package com.nodecraft.nodesystem.nodes.pattern.voronoi_3d;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Voronoi3DGridLloyd;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "pattern.voronoi_3d.lloyd_relax",
    displayName = "Voronoi 3D Lloyd Relax (Grid)",
    description = "Approximate 3D Lloyd relaxation: grid cell centers vote for nearest site; sites move to cell centroids (repeat). Not an exact Voronoi diagram.",
    category = "pattern.voronoi_3d",
    order = 1
)
public class Voronoi3DLloydRelaxNode extends BaseNode {

    @NodeProperty(displayName = "Cells Per Axis", category = "Grid", order = 1,
        description = "Resolution of the internal uniform grid (higher = slower, more accurate)")
    private int cellsPerAxis = 24;

    @NodeProperty(displayName = "Iterations", category = "Lloyd", order = 2)
    private int iterations = 4;

    private static final String INPUT_SITES_ID = "input_sites";
    private static final String INPUT_MIN_ID = "input_min";
    private static final String INPUT_MAX_ID = "input_max";
    private static final String INPUT_CELLS_ID = "input_cells";
    private static final String INPUT_ITERATIONS_ID = "input_iterations";

    private static final String OUTPUT_SITES_ID = "output_sites";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public Voronoi3DLloydRelaxNode() {
        super(UUID.randomUUID(), "pattern.voronoi_3d.lloyd_relax");

        addInputPort(new BasePort(INPUT_SITES_ID, "Sites", "Seed sites as Vector3d list", NodeDataType.VECTOR_LIST, this));
        addInputPort(new BasePort(INPUT_MIN_ID, "Min", "Axis-aligned box minimum corner", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_MAX_ID, "Max", "Axis-aligned box maximum corner", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_CELLS_ID, "Cells", "Grid cells per axis (optional override)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations", "Lloyd rounds (optional override)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_SITES_ID, "Sites", "Relaxed site positions", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of sites", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when relaxation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Approximate 3D Lloyd relaxation: grid cell centers vote for nearest site; sites move to cell centroids (repeat). Not an exact Voronoi diagram.";
    }

    @Override
    public String getDisplayName() {
        return "Voronoi 3D Lloyd Relax (Grid)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sitesObj = inputValues.get(INPUT_SITES_ID);
        Object minObj = inputValues.get(INPUT_MIN_ID);
        Object maxObj = inputValues.get(INPUT_MAX_ID);
        if (!(sitesObj instanceof Collection<?> collection) || !(minObj instanceof Vector3d minRaw) || !(maxObj instanceof Vector3d maxRaw)) {
            writeInvalid();
            return;
        }
        List<Vector3d> sites = new ArrayList<>();
        for (Object o : collection) {
            if (o instanceof Vector3d v) {
                sites.add(new Vector3d(v));
            }
        }
        if (sites.isEmpty()) {
            writeInvalid();
            return;
        }

        Vector3d min = new Vector3d(minRaw);
        Vector3d max = new Vector3d(maxRaw);
        if (min.x > max.x) {
            double t = min.x;
            min.x = max.x;
            max.x = t;
        }
        if (min.y > max.y) {
            double t = min.y;
            min.y = max.y;
            max.y = t;
        }
        if (min.z > max.z) {
            double t = min.z;
            min.z = max.z;
            max.z = t;
        }

        int cells = getInputInt(INPUT_CELLS_ID, cellsPerAxis);
        int iters = getInputInt(INPUT_ITERATIONS_ID, iterations);
        cells = Math.max(4, Math.min(96, cells));
        iters = Math.max(1, Math.min(32, iters));

        List<Vector3d> relaxed = Voronoi3DGridLloyd.relax(min, max, sites, cells, iters);
        outputValues.put(OUTPUT_SITES_ID, relaxed);
        outputValues.put(OUTPUT_COUNT_ID, relaxed.size());
        outputValues.put(OUTPUT_VALID_ID, !relaxed.isEmpty());
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_SITES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private int getInputInt(String portId, int fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.intValue() : fallback;
    }
}
