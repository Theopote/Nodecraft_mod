package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.lattice_deform",
    displayName = "Lattice Deform Point List",
    description = "Free-form deformation: trilinear blend of control displacements on a uniform (nx+1)(ny+1)(nz+1) lattice in an axis-aligned box",
    category = "transform.deformations",
    order = 6
)
public class LatticeDeformPointListNode extends BaseNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Grid X", category = "Lattice", order = 1,
        description = "Number of cells along X (control points = cells + 1)")
    private int gridX = 2;

    @NodeProperty(displayName = "Grid Y", category = "Lattice", order = 2)
    private int gridY = 2;

    @NodeProperty(displayName = "Grid Z", category = "Lattice", order = 3)
    private int gridZ = 2;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_MIN_ID = "input_min";
    private static final String INPUT_MAX_ID = "input_max";
    private static final String INPUT_OFFSETS_ID = "input_offsets";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public LatticeDeformPointListNode() {
        super(UUID.randomUUID(), "transform.deformations.lattice_deform");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to deform", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_MIN_ID, "Min", "Lattice box minimum corner", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_MAX_ID, "Max", "Lattice box maximum corner", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_OFFSETS_ID, "Offsets",
            "Control displacement vectors in index order i + (nx+1)*(j + (ny+1)*k)",
            NodeDataType.VECTOR_LIST, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Deformed point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when deformation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Free-form deformation: trilinear blend of control displacements on a uniform (nx+1)(ny+1)(nz+1) lattice in an axis-aligned box";
    }

    @Override
    public String getDisplayName() {
        return "Lattice Deform Point List";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pointsInput = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        Vector3d min = SpatialValueResolver.resolveVector3d(inputValues.get(INPUT_MIN_ID));
        Vector3d max = SpatialValueResolver.resolveVector3d(inputValues.get(INPUT_MAX_ID));
        Object offObj = inputValues.get(INPUT_OFFSETS_ID);
        if (pointsInput.isEmpty() || !DeformationUtils.isFinite(min) || !DeformationUtils.isFinite(max)
            || !(offObj instanceof List<?> offsetList)) {
            writeEmpty();
            return;
        }

        int nx = clampGrid(gridX);
        int ny = clampGrid(gridY);
        int nz = clampGrid(gridZ);
        int cx = nx + 1;
        int cy = ny + 1;
        int cz = nz + 1;
        int expected = cx * cy * cz;

        List<Vector3d> controls = new ArrayList<>(expected);
        for (Object o : offsetList) {
            if (o instanceof Vector3d v && DeformationUtils.isFinite(v)) {
                controls.add(new Vector3d(v));
            }
        }
        if (controls.size() != expected) {
            writeEmpty();
            return;
        }

        Vector3d mn = new Vector3d(min);
        Vector3d mx = new Vector3d(max);
        swapIfNeeded(mn, mx);
        Vector3d span = new Vector3d(mx).sub(mn);
        if (span.x <= EPS || span.y <= EPS || span.z <= EPS) {
            writeEmpty();
            return;
        }

        List<Vector3d> out = new ArrayList<>();
        for (Vector3d p : pointsInput) {
            Vector3d delta = sampleLatticeDelta(p, mn, span, nx, ny, nz, controls, cx, cy);
            out.add(new Vector3d(p).add(delta));
        }

        if (out.isEmpty()) {
            writeEmpty();
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(out));
        outputValues.put(OUTPUT_COUNT_ID, out.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static Vector3d sampleLatticeDelta(
        Vector3d p,
        Vector3d min,
        Vector3d span,
        int nx,
        int ny,
        int nz,
        List<Vector3d> controls,
        int cx,
        int cy
    ) {
        double ux = clamp01((p.x - min.x) / span.x);
        double uy = clamp01((p.y - min.y) / span.y);
        double uz = clamp01((p.z - min.z) / span.z);

        double sx = ux * nx;
        double sy = uy * ny;
        double sz = uz * nz;

        int i0 = Math.min((int) Math.floor(sx), nx - 1);
        int j0 = Math.min((int) Math.floor(sy), ny - 1);
        int k0 = Math.min((int) Math.floor(sz), nz - 1);
        int i1 = i0 + 1;
        int j1 = j0 + 1;
        int k1 = k0 + 1;

        double fx = sx - i0;
        double fy = sy - j0;
        double fz = sz - k0;

        Vector3d acc = new Vector3d();
        for (int ia = 0; ia < 2; ia++) {
            double wx = ia == 0 ? (1.0d - fx) : fx;
            int ii = ia == 0 ? i0 : i1;
            for (int jb = 0; jb < 2; jb++) {
                double wy = jb == 0 ? (1.0d - fy) : fy;
                int jj = jb == 0 ? j0 : j1;
                for (int kc = 0; kc < 2; kc++) {
                    double wz = kc == 0 ? (1.0d - fz) : fz;
                    int kk = kc == 0 ? k0 : k1;
                    int idx = ii + cx * (jj + cy * kk);
                    acc.fma(wx * wy * wz, controls.get(idx));
                }
            }
        }
        return acc;
    }

    private static int clampGrid(int g) {
        return Math.max(1, Math.min(8, g));
    }

    private static double clamp01(double v) {
        return Math.max(0.0d, Math.min(1.0d, v));
    }

    private static void swapIfNeeded(Vector3d min, Vector3d max) {
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
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("gridX", gridX);
        state.put("gridY", gridY);
        state.put("gridZ", gridZ);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        gridX = clampGrid(DeformationUtils.intOrCurrent(map.get("gridX"), gridX));
        gridY = clampGrid(DeformationUtils.intOrCurrent(map.get("gridY"), gridY));
        gridZ = clampGrid(DeformationUtils.intOrCurrent(map.get("gridZ"), gridZ));
    }

}
