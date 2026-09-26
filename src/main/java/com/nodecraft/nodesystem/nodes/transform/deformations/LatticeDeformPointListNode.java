package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
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
    order = 8
)
public class LatticeDeformPointListNode extends BaseNode {

    @NodeProperty(displayName = "Grid X", category = "Lattice", order = 1,
        description = "Number of cells along X (control points = cells + 1); must be 1..8")
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
        List<Vector3d> pointsInput = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        Vector3d min = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_MIN_ID));
        Vector3d max = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_MAX_ID));
        List<Vector3d> controls = VectorUtils.resolveStrictVectorList(inputValues.get(INPUT_OFFSETS_ID));

        if (pointsInput == null || min == null || max == null || controls == null) {
            writeInvalid();
            return;
        }
        if (!(min.x < max.x && min.y < max.y && min.z < max.z)) {
            writeInvalid();
            return;
        }
        if (!isValidGrid(gridX) || !isValidGrid(gridY) || !isValidGrid(gridZ)) {
            writeInvalid();
            return;
        }

        int nx = gridX;
        int ny = gridY;
        int nz = gridZ;
        int cx = nx + 1;
        int cy = ny + 1;
        int cz = nz + 1;
        int expected = cx * cy * cz;
        if (controls.size() != expected) {
            writeInvalid();
            return;
        }

        Vector3d span = new Vector3d(max).sub(min);
        List<Vector3d> out = new ArrayList<>(pointsInput.size());
        for (Vector3d p : pointsInput) {
            Vector3d delta = sampleLatticeDelta(p, min, span, nx, ny, nz, controls, cx, cy);
            out.add(new Vector3d(p).add(delta));
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

    private static boolean isValidGrid(int g) {
        return g >= 1 && g <= 8;
    }

    private static double clamp01(double v) {
        return Math.max(0.0d, Math.min(1.0d, v));
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public void setGridX(int gridX) {
        if (isValidGrid(gridX)) {
            this.gridX = gridX;
            markDirty();
        }
    }

    public void setGridY(int gridY) {
        if (isValidGrid(gridY)) {
            this.gridY = gridY;
            markDirty();
        }
    }

    public void setGridZ(int gridZ) {
        if (isValidGrid(gridZ)) {
            this.gridZ = gridZ;
            markDirty();
        }
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
        if (map.get("gridX") instanceof Integer value && isValidGrid(value)) {
            gridX = value;
        }
        if (map.get("gridY") instanceof Integer value && isValidGrid(value)) {
            gridY = value;
        }
        if (map.get("gridZ") instanceof Integer value && isValidGrid(value)) {
            gridZ = value;
        }
    }
}
