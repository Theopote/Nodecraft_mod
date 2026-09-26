package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.flow_accumulation_field",
    displayName = "Flow Accumulation Field",
    description = "Routes runoff with selectable fast or high-quality (MFD) flow accumulation. "
        + "Samples at block cell centers; auto-downsamples when the region exceeds the grid cell cap.",
    category = "world.terrain",
    order = 6
)
public class FlowAccumulationFieldNode extends BaseNode {

    public enum AccumulationMode {
        FAST_APPROXIMATE,
        HIGH_QUALITY_MFD
    }

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_FLOW_FIELD_ID = "input_flow_field";
    private static final String INPUT_RAIN_FIELD_ID = "input_rain_field";
    private static final String INPUT_ITERATIONS_ID = "input_iterations";

    private static final String OUTPUT_ACCUMULATION_FIELD_ID = "output_accumulation_field";
    private static final String OUTPUT_STRIDE_ID = "output_stride";
    private static final String OUTPUT_GRID_WIDTH_ID = "output_grid_width";
    private static final String OUTPUT_GRID_DEPTH_ID = "output_grid_depth";
    private static final String OUTPUT_CELL_COUNT_ID = "output_cell_count";
    private static final String OUTPUT_WAS_DOWNSAMPLED_ID = "output_was_downsampled";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Iterations", category = "Simulation", order = 1)
    private int iterations = 64;

    @NodeProperty(displayName = "Mode", category = "Simulation", order = 2)
    private AccumulationMode mode = AccumulationMode.HIGH_QUALITY_MFD;

    @NodeProperty(displayName = "Fast Routed Ratio", category = "Fast", order = 3)
    private double fastRoutedRatio = 0.35d;

    @NodeProperty(displayName = "Routing Rate", category = "Quality", order = 4)
    private double routingRate = 0.9d;

    @NodeProperty(displayName = "Normalize Output", category = "Output", order = 5)
    private boolean normalizeOutput = true;

    public FlowAccumulationFieldNode() {
        super(UUID.randomUUID(), "world.terrain.flow_accumulation_field");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region",
            "Optional simulation bounds; defaults to local 64x64 (−32..31, sample Y=64) when omitted",
            NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_FLOW_FIELD_ID, "Flow Field",
            "Normalized downslope direction field", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_RAIN_FIELD_ID, "Rain Field",
            "Optional precipitation input", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations",
            "Routing iterations (exact INTEGER, hard-capped)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_ACCUMULATION_FIELD_ID, "Accumulation Field",
            "Drainage accumulation estimate", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_STRIDE_ID, "Stride",
            "Actual grid stride used for routing", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_GRID_WIDTH_ID, "Grid Width",
            "Internal routing grid width", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_GRID_DEPTH_ID, "Grid Depth",
            "Internal routing grid depth", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_CELL_COUNT_ID, "Cell Count",
            "Internal routing cell count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_WAS_DOWNSAMPLED_ID, "Was Downsampled",
            "True when the region was routed with stride > 1", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "Whether accumulation succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when accumulation failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!(inputValues.get(INPUT_FLOW_FIELD_ID) instanceof VectorFieldData flowField)) {
            writeInvalid("Missing flow field input.");
            return;
        }

        RegionData region = TerrainNodeUtils.resolveOptionalRegion(this, INPUT_REGION_ID);
        if (TerrainNodeUtils.isInvalidRegionMarker(region)) {
            writeInvalid("Region is connected but incomplete or invalid.");
            return;
        }

        ScalarFieldData rainField = null;
        if (OptionalPortDrive.isConnected(this, INPUT_RAIN_FIELD_ID)) {
            if (!(inputValues.get(INPUT_RAIN_FIELD_ID) instanceof ScalarFieldData connectedRain)) {
                writeInvalid("Rain Field is connected but null or invalid.");
                return;
            }
            rainField = connectedRain;
        } else if (inputValues.get(INPUT_RAIN_FIELD_ID) instanceof ScalarFieldData localRain) {
            rainField = localRain;
        }

        Integer resolvedIterations = TerrainNodeUtils.resolveBoundedExactInteger(
            this, INPUT_ITERATIONS_ID, iterations, 1, GenerationLimits.MAX_TERRAIN_FLOW_ITERATIONS);
        if (resolvedIterations == null) {
            writeInvalid("Iterations must be an exact INTEGER between 1 and "
                + GenerationLimits.MAX_TERRAIN_FLOW_ITERATIONS + ".");
            return;
        }

        AccumulationMode resolvedMode = mode == null ? AccumulationMode.HIGH_QUALITY_MFD : mode;
        double resolvedFastRoutedRatio = clamp01(fastRoutedRatio);
        double resolvedRoutingRate = clamp01(routingRate);

        int minX;
        int maxX;
        int minZ;
        int maxZ;
        int baseY;
        if (region != null && region.isComplete()) {
            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min == null || max == null) {
                writeInvalid("Region bounds are incomplete.");
                return;
            }
            minX = min.getX();
            maxX = max.getX();
            minZ = min.getZ();
            maxZ = max.getZ();
            baseY = min.getY();
        } else {
            minX = TerrainNodeUtils.DEFAULT_MIN_X;
            maxX = TerrainNodeUtils.DEFAULT_MAX_X;
            minZ = TerrainNodeUtils.DEFAULT_MIN_Z;
            maxZ = TerrainNodeUtils.DEFAULT_MAX_Z;
            baseY = TerrainNodeUtils.DEFAULT_BASE_Y;
        }

        long widthLong = (long) maxX - (long) minX + 1L;
        long depthLong = (long) maxZ - (long) minZ + 1L;
        if (widthLong <= 0L || depthLong <= 0L) {
            writeInvalid("Region has no X/Z area.");
            return;
        }
        if (widthLong > Integer.MAX_VALUE || depthLong > Integer.MAX_VALUE) {
            writeInvalid("Region X/Z extent overflows integer range.");
            return;
        }
        int width = (int) widthLong;
        int depth = (int) depthLong;

        int stride = resolveStride(width, depth, GenerationLimits.MAX_TERRAIN_GRID_CELLS);
        int gridWidth = Math.max(1, ((width - 1) / stride) + 1);
        int gridDepth = Math.max(1, ((depth - 1) / stride) + 1);
        long gridCells;
        try {
            gridCells = Math.multiplyExact((long) gridWidth, (long) gridDepth);
        } catch (ArithmeticException e) {
            writeInvalid("Routing grid cell count overflows.");
            return;
        }

        long work;
        try {
            work = Math.multiplyExact(gridCells, (long) resolvedIterations);
        } catch (ArithmeticException e) {
            writeInvalid("Simulation work overflows.");
            return;
        }
        if (work > GenerationLimits.MAX_TERRAIN_SIMULATION_WORK) {
            writeInvalid("Simulation work (" + work + ") exceeds MAX_TERRAIN_SIMULATION_WORK ("
                + GenerationLimits.MAX_TERRAIN_SIMULATION_WORK + ").");
            return;
        }

        double[][] accumulation = new double[gridDepth][gridWidth];
        double[][] mobile = new double[gridDepth][gridWidth];
        double[][] rainfall = new double[gridDepth][gridWidth];
        Vector3d samplePoint = new Vector3d();

        for (int gz = 0; gz < gridDepth; gz++) {
            int worldZ = minZ + gz * stride;
            for (int gx = 0; gx < gridWidth; gx++) {
                int worldX = minX + gx * stride;
                samplePoint.set(
                    worldX + BlockSpace.CELL_CENTER_OFFSET,
                    baseY + BlockSpace.CELL_CENTER_OFFSET,
                    worldZ + BlockSpace.CELL_CENTER_OFFSET
                );
                double rain = 1.0d;
                if (rainField != null) {
                    rain = rainField.sampleScalar(samplePoint);
                    if (!Double.isFinite(rain)) {
                        writeInvalid("Rain field returned a non-finite sample.");
                        return;
                    }
                    rain = Math.max(0.0d, rain);
                }
                rainfall[gz][gx] = rain;
                mobile[gz][gx] = rain;
            }
        }

        Vector3d flow = new Vector3d();
        for (int iterationIndex = 0; iterationIndex < resolvedIterations; iterationIndex++) {
            double[][] next = new double[gridDepth][gridWidth];

            for (int gz = 0; gz < gridDepth; gz++) {
                int worldZ = minZ + gz * stride;
                for (int gx = 0; gx < gridWidth; gx++) {
                    double mass = mobile[gz][gx];
                    if (mass <= 1.0e-12d) {
                        continue;
                    }

                    accumulation[gz][gx] += mass;

                    int worldX = minX + gx * stride;
                    samplePoint.set(
                        worldX + BlockSpace.CELL_CENTER_OFFSET,
                        baseY + BlockSpace.CELL_CENTER_OFFSET,
                        worldZ + BlockSpace.CELL_CENTER_OFFSET
                    );
                    flowField.sampleVector(samplePoint, flow);
                    if (!Double.isFinite(flow.x) || !Double.isFinite(flow.y) || !Double.isFinite(flow.z)) {
                        writeInvalid("Flow field returned a non-finite sample.");
                        return;
                    }

                    if (resolvedMode == AccumulationMode.FAST_APPROXIMATE) {
                        routeFast(gx, gz, mass, flow, resolvedFastRoutedRatio, gridWidth, gridDepth, next);
                    } else {
                        routeMfd(gx, gz, mass, flow, resolvedRoutingRate, gridWidth, gridDepth, next);
                    }
                }
            }

            mobile = next;
        }

        if (normalizeOutput) {
            normalizeGrid(accumulation);
        }

        FlowGrid flowGrid = new FlowGrid(minX, minZ, baseY, stride, gridWidth, gridDepth, accumulation);
        ScalarFieldData accumulationField = point -> flowGrid.sample(point.x, point.z);
        outputValues.put(OUTPUT_ACCUMULATION_FIELD_ID, accumulationField);
        outputValues.put(OUTPUT_STRIDE_ID, stride);
        outputValues.put(OUTPUT_GRID_WIDTH_ID, gridWidth);
        outputValues.put(OUTPUT_GRID_DEPTH_ID, gridDepth);
        outputValues.put(OUTPUT_CELL_COUNT_ID, (int) Math.min(Integer.MAX_VALUE, gridCells));
        outputValues.put(OUTPUT_WAS_DOWNSAMPLED_ID, stride > 1);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_ACCUMULATION_FIELD_ID, null);
        outputValues.put(OUTPUT_STRIDE_ID, 0);
        outputValues.put(OUTPUT_GRID_WIDTH_ID, 0);
        outputValues.put(OUTPUT_GRID_DEPTH_ID, 0);
        outputValues.put(OUTPUT_CELL_COUNT_ID, 0);
        outputValues.put(OUTPUT_WAS_DOWNSAMPLED_ID, false);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private void routeFast(int x,
                           int z,
                           double mass,
                           Vector3d flow,
                           double routedRatio,
                           int gridWidth,
                           int gridDepth,
                           double[][] next) {
        int stepX = (int) Math.signum(flow.x);
        int stepZ = (int) Math.signum(flow.z);

        double flowStrength = Math.min(1.0d, Math.sqrt(flow.x * flow.x + flow.z * flow.z));
        double routed = mass * routedRatio * flowStrength;

        int targetX = x + stepX;
        int targetZ = z + stepZ;
        if (isInside(targetX, targetZ, gridWidth, gridDepth) && (stepX != 0 || stepZ != 0)) {
            next[z][x] += mass - routed;
            next[targetZ][targetX] += routed;
        } else {
            next[z][x] += mass;
        }
    }

    private void routeMfd(int x,
                          int z,
                          double mass,
                          Vector3d flow,
                          double routingRate,
                          int gridWidth,
                          int gridDepth,
                          double[][] next) {
        double flowX = flow.x;
        double flowZ = flow.z;
        double flowLen = Math.sqrt(flowX * flowX + flowZ * flowZ);
        if (flowLen <= 1.0e-12d) {
            next[z][x] += mass;
            return;
        }

        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dz = {-1, -1, -1, 0, 0, 1, 1, 1};
        double[] weights = new double[8];
        double sum = 0.0d;

        for (int i = 0; i < 8; i++) {
            int nx = x + dx[i];
            int nz = z + dz[i];
            if (!isInside(nx, nz, gridWidth, gridDepth)) {
                continue;
            }

            double dirX = dx[i];
            double dirZ = dz[i];
            double dot = flowX * dirX + flowZ * dirZ;
            if (dot <= 0.0d) {
                continue;
            }

            double distance = (dirX != 0.0d && dirZ != 0.0d) ? Math.sqrt(2.0d) : 1.0d;
            double weight = dot / distance;
            weights[i] = weight;
            sum += weight;
        }

        if (sum <= 1.0e-12d) {
            next[z][x] += mass;
            return;
        }

        double routedTotal = mass * routingRate * Math.min(1.0d, flowLen);
        double remain = mass - routedTotal;
        next[z][x] += remain;

        for (int i = 0; i < 8; i++) {
            double weight = weights[i];
            if (weight <= 0.0d) {
                continue;
            }
            int nx = x + dx[i];
            int nz = z + dz[i];
            double share = routedTotal * (weight / sum);
            next[nz][nx] += share;
        }
    }

    private void normalizeGrid(double[][] grid) {
        double max = 0.0d;
        for (double[] row : grid) {
            for (double value : row) {
                max = Math.max(max, value);
            }
        }
        if (max <= 1.0e-12d) {
            return;
        }
        for (int z = 0; z < grid.length; z++) {
            for (int x = 0; x < grid[z].length; x++) {
                grid[z][x] /= max;
            }
        }
    }

    private int resolveStride(int width, int depth, int maxCells) {
        int stride = 1;
        while ((((width - 1L) / stride) + 1L) * (((depth - 1L) / stride) + 1L) > maxCells) {
            stride++;
            if (stride > width && stride > depth) {
                break;
            }
        }
        return stride;
    }

    private boolean isInside(int x, int z, int width, int depth) {
        return x >= 0 && x < width && z >= 0 && z < depth;
    }

    private double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private record FlowGrid(int minX, int minZ, int baseY, int stride, int width, int depth, double[][] values) {

        private double sample(double worldX, double worldZ) {
            int cellX = BlockSpace.nearestCellIndex(worldX);
            int cellZ = BlockSpace.nearestCellIndex(worldZ);
            int gx = clamp((cellX - minX) / stride, 0, width - 1);
            int gz = clamp((cellZ - minZ) / stride, 0, depth - 1);
            return values[gz][gx];
        }

        private static int clamp(int value, int min, int max) {
            if (value < min) {
                return min;
            }
            return Math.min(value, max);
        }
    }
}
