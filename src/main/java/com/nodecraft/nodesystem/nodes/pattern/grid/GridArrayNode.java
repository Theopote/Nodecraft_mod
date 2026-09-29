package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryStructureUtils;
import com.nodecraft.nodesystem.util.GeometryTransform;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
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
    id = "pattern.grid.grid_array",
    displayName = "Grid Array",
    description = "Creates rectangular or box arrays of geometry using first/second/third array axes",
    category = "pattern.grid",
    order = 0
)
public class GridArrayNode extends AbstractPatternGridNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_X_DIRECTION_ID = "input_x_direction";
    private static final String INPUT_X_DISTANCE_ID = "input_x_distance";
    private static final String INPUT_X_COUNT_ID = "input_x_count";
    private static final String INPUT_Y_DIRECTION_ID = "input_y_direction";
    private static final String INPUT_Y_DISTANCE_ID = "input_y_distance";
    private static final String INPUT_Y_COUNT_ID = "input_y_count";
    private static final String INPUT_Z_DIRECTION_ID = "input_z_direction";
    private static final String INPUT_Z_DISTANCE_ID = "input_z_distance";
    private static final String INPUT_Z_COUNT_ID = "input_z_count";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_OFFSETS_ID = "output_offsets";
    private static final String OUTPUT_GEOMETRY_TREE_ID = "output_geometry_tree";
    private static final String OUTPUT_OFFSET_TREE_ID = "output_offset_tree";
    private static final String OUTPUT_COUNT_ID = "output_count";

    @NodeProperty(displayName = "X Distance", category = "Array", order = 1)
    private double xDistance = 1.0d;

    @NodeProperty(displayName = "X Count", category = "Array", order = 2)
    private int xCount = 1;

    @NodeProperty(displayName = "Y Distance", category = "Array", order = 3)
    private double yDistance = 1.0d;

    @NodeProperty(displayName = "Y Count", category = "Array", order = 4)
    private int yCount = 1;

    @NodeProperty(displayName = "Z Distance", category = "Array", order = 5)
    private double zDistance = 1.0d;

    @NodeProperty(displayName = "Z Count", category = "Array", order = 6)
    private int zCount = 1;

    public GridArrayNode() {
        super(UUID.randomUUID(), "pattern.grid.grid_array");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to copy", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_X_DIRECTION_ID, "X Direction", "First array axis direction", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_X_DISTANCE_ID, "X Distance", "Spacing along X Direction (signed allowed)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_COUNT_ID, "X Count", "Number of positions along X Direction", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Y_DIRECTION_ID, "Y Direction", "Second array axis direction", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Y_DISTANCE_ID, "Y Distance", "Spacing along Y Direction (signed allowed)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_COUNT_ID, "Y Count", "Number of positions along Y Direction", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Z_DIRECTION_ID, "Z Direction", "Optional third array axis for box arrays", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Z_DISTANCE_ID, "Z Distance", "Spacing along Z Direction (signed allowed)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Z_COUNT_ID, "Z Count", "Number of positions along Z Direction. Use 1 for rectangular arrays.", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all grid copies", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_OFFSETS_ID, "Offsets", "Offset vectors used for each copy", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_TREE_ID, "Geometry Tree", "One branch per grid position using {x;y;z} paths", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_OFFSET_TREE_ID, "Offset Tree", "Offset vectors keyed by grid position paths", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted geometry copies", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Creates rectangular or box arrays of geometry using first/second/third array axes";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeFail("Missing or invalid Geometry");
            return;
        }

        Integer resolvedXCount = OptionalPortDrive.resolveOptionalInteger(this, INPUT_X_COUNT_ID, xCount);
        Integer resolvedYCount = OptionalPortDrive.resolveOptionalInteger(this, INPUT_Y_COUNT_ID, yCount);
        Integer resolvedZCount = OptionalPortDrive.resolveOptionalInteger(this, INPUT_Z_COUNT_ID, zCount);
        if (resolvedXCount == null) {
            writeFail("X Count connected but invalid");
            return;
        }
        if (resolvedYCount == null) {
            writeFail("Y Count connected but invalid");
            return;
        }
        if (resolvedZCount == null) {
            writeFail("Z Count connected but invalid");
            return;
        }

        String productError = GenerationLimits.validateGridProduct(
            resolvedXCount, resolvedYCount, resolvedZCount, GenerationLimits.MAX_GEOMETRY_INSTANCES);
        if (productError != null) {
            writeFail(productError);
            return;
        }

        Vector3d xStep = resolveAxisStep(INPUT_X_DIRECTION_ID, INPUT_X_DISTANCE_ID,
            new Vector3d(1, 0, 0), xDistance, "X");
        if (xStep == null) {
            return;
        }
        Vector3d yStep = resolveAxisStep(INPUT_Y_DIRECTION_ID, INPUT_Y_DISTANCE_ID,
            new Vector3d(0, 1, 0), yDistance, "Y");
        if (yStep == null) {
            return;
        }
        Vector3d zStep = resolveAxisStep(INPUT_Z_DIRECTION_ID, INPUT_Z_DISTANCE_ID,
            new Vector3d(0, 0, 1), zDistance, "Z");
        if (zStep == null) {
            return;
        }

        long maxInstances = GenerationLimits.MAX_GEOMETRY_INSTANCES;
        long instanceCount = (long) resolvedXCount * resolvedYCount * resolvedZCount;
        long sourceLeaves = GeometryStructureUtils.countLeavesBounded(geometry, maxInstances);
        if (sourceLeaves > maxInstances || sourceLeaves * instanceCount > maxInstances) {
            writeFail("Array workload exceeds limit (source leaves × grid count > MAX_GEOMETRY_INSTANCES)");
            return;
        }

        int capacity = (int) instanceCount;
        List<GeometryData> copies = new ArrayList<>(capacity);
        List<Vector3d> offsets = new ArrayList<>(capacity);
        List<List<Integer>> paths = new ArrayList<>(capacity);

        for (int z = 0; z < resolvedZCount; z++) {
            for (int y = 0; y < resolvedYCount; y++) {
                for (int x = 0; x < resolvedXCount; x++) {
                    Vector3d offset = new Vector3d(xStep).mul(x)
                        .add(new Vector3d(yStep).mul(y))
                        .add(new Vector3d(zStep).mul(z));
                    if (!VectorUtils.isFinite(offset)) {
                        writeFail("Non-finite grid offset");
                        return;
                    }
                    GeometryData copy = (x == 0 && y == 0 && z == 0)
                        ? geometry
                        : GeometryTransform.transform(geometry, offset, 0.0d, 0.0d, 0.0d, 1.0d);
                    if (copy == null) {
                        writeFail("Geometry copy transform failed");
                        return;
                    }
                    copies.add(copy);
                    offsets.add(offset);
                    paths.add(resolvedZCount > 1 ? List.of(x, y, z) : List.of(x, y));
                }
            }
        }

        writeSuccess(copies, offsets, paths);
    }

    private @Nullable Vector3d resolveAxisStep(
        String directionId,
        String distanceId,
        Vector3d defaultDirection,
        double distanceProperty,
        String axisLabel
    ) {
        Vector3d direction = resolveOptionalNonZeroDirection(this, directionId, defaultDirection);
        if (direction == null) {
            writeFail(axisLabel + " Direction connected but invalid or zero");
            return null;
        }
        Double distance = OptionalPortDrive.resolveOptionalDouble(this, distanceId, distanceProperty);
        if (distance == null) {
            writeFail(axisLabel + " Distance connected but invalid");
            return null;
        }
        if (!Double.isFinite(distance)) {
            writeFail(axisLabel + " Distance must be finite");
            return null;
        }
        return new Vector3d(direction).mul(distance);
    }

    private void writeFail(String error) {
        markInvalid(error);
        putNullOutputs(OUTPUT_GEOMETRY_ID);
        putEmptyListOutputs(OUTPUT_OFFSETS_ID);
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, new DataTreeData(List.of()));
        outputValues.put(OUTPUT_OFFSET_TREE_ID, new DataTreeData(List.of()));
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    private void writeSuccess(List<GeometryData> copies, List<Vector3d> offsets, List<List<Integer>> paths) {
        markSuccess();
        if (copies.isEmpty()) {
            putNullOutputs(OUTPUT_GEOMETRY_ID);
        } else if (copies.size() == 1) {
            outputValues.put(OUTPUT_GEOMETRY_ID, copies.getFirst());
        } else {
            outputValues.put(OUTPUT_GEOMETRY_ID, new CompositeGeometryData(copies));
        }
        outputValues.put(OUTPUT_OFFSETS_ID, List.copyOf(offsets));
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, buildTree(copies, paths));
        outputValues.put(OUTPUT_OFFSET_TREE_ID, buildTree(offsets, paths));
        putIntOutputs(copies.size(), OUTPUT_COUNT_ID);
    }

    private DataTreeData buildTree(List<?> values, List<List<Integer>> paths) {
        List<DataTreeData.Branch> branches = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            List<Integer> path = i < paths.size() ? paths.get(i) : List.of(i);
            branches.add(new DataTreeData.Branch(path, List.of(values.get(i))));
        }
        return new DataTreeData(branches);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("xDistance", xDistance);
        state.put("xCount", xCount);
        state.put("yDistance", yDistance);
        state.put("yCount", yCount);
        state.put("zDistance", zDistance);
        state.put("zCount", zCount);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("xDistance") instanceof Number value) {
            xDistance = value.doubleValue();
        }
        if (map.get("xCount") instanceof Number value) {
            xCount = value.intValue();
        }
        if (map.get("yDistance") instanceof Number value) {
            yDistance = value.doubleValue();
        }
        if (map.get("yCount") instanceof Number value) {
            yCount = value.intValue();
        }
        if (map.get("zDistance") instanceof Number value) {
            zDistance = value.doubleValue();
        }
        if (map.get("zCount") instanceof Number value) {
            zCount = value.intValue();
        }
    }
}
