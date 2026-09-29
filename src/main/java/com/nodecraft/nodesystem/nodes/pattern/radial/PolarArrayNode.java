package com.nodecraft.nodesystem.nodes.pattern.radial;

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
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.radial.polar_array",
    displayName = "Polar Array",
    description = "Creates repeated geometry copies around a center point and axis",
    category = "pattern.radial",
    order = 0
)
public class PolarArrayNode extends AbstractPatternRadialNode {

    private static final double ANGLE_EPS = 1.0e-9d;

    @NodeProperty(
        displayName = "Include End",
        category = "Array",
        order = 1,
        description = "When true and Count >= 2, the last instance sits at Total Angle. Ignored for exact full-circle angles (multiple of 360°) so the start is not duplicated."
    )
    private boolean includeEnd = false;

    @NodeProperty(displayName = "Count", category = "Array", order = 2)
    private int count = 1;

    @NodeProperty(displayName = "Total Angle", category = "Array", order = 3)
    private double totalAngle = 360.0d;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_TOTAL_ANGLE_ID = "input_total_angle";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_GEOMETRY_TREE_ID = "output_geometry_tree";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public PolarArrayNode() {
        super(UUID.randomUUID(), "pattern.radial.polar_array");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to copy", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Array center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Rotation axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Total number of emitted instances around the center", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_TOTAL_ANGLE_ID, "Total Angle", "Total angle span in degrees. Full circles (multiple of 360°) never emit a duplicate at 360°. Zero is legal.", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all copies", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_TREE_ID, "Geometry Tree", "One branch per emitted geometry copy", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted geometry copies", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Creates repeated geometry copies around a center point and axis";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeFail("Missing or invalid Geometry");
            return;
        }

        Vector3d center = resolveOptionalOrigin(this, INPUT_CENTER_ID);
        if (center == null) {
            writeFail("Center connected but invalid");
            return;
        }

        Vector3d axis = resolveOptionalNonZeroDirection(this, INPUT_AXIS_ID, new Vector3d(0.0d, 1.0d, 0.0d));
        if (axis == null) {
            writeFail("Axis connected but invalid or zero");
            return;
        }

        Integer resolvedCount = OptionalPortDrive.resolveOptionalInteger(this, INPUT_COUNT_ID, count);
        if (resolvedCount == null) {
            writeFail("Count connected but invalid");
            return;
        }
        if (resolvedCount <= 0) {
            writeFail("Count must be >= 1");
            return;
        }
        if (resolvedCount > GenerationLimits.MAX_GEOMETRY_INSTANCES) {
            writeFail("Count exceeds MAX_GEOMETRY_INSTANCES");
            return;
        }

        Double resolvedAngle = OptionalPortDrive.resolveOptionalDouble(this, INPUT_TOTAL_ANGLE_ID, totalAngle);
        if (resolvedAngle == null) {
            writeFail("Total Angle connected but invalid");
            return;
        }
        if (!Double.isFinite(resolvedAngle)) {
            writeFail("Total Angle must be finite");
            return;
        }

        long maxInstances = GenerationLimits.MAX_GEOMETRY_INSTANCES;
        long sourceLeaves = GeometryStructureUtils.countLeavesBounded(geometry, maxInstances);
        if (sourceLeaves > maxInstances
            || sourceLeaves * (long) resolvedCount > maxInstances) {
            writeFail("Array workload exceeds limit (source leaves × Count > MAX_GEOMETRY_INSTANCES)");
            return;
        }

        boolean fullCircle = isFullCircle(resolvedAngle);
        boolean useInclusiveEnd = includeEnd && !fullCircle && resolvedCount >= 2;
        List<GeometryData> copies = new ArrayList<>(resolvedCount);
        for (int i = 0; i < resolvedCount; i++) {
            double degrees = useInclusiveEnd
                ? resolvedAngle * i / (double) (resolvedCount - 1)
                : resolvedAngle * i / (double) resolvedCount;
            if (Math.abs(degrees) <= ANGLE_EPS) {
                copies.add(geometry);
                continue;
            }
            Quaterniond quaternion = new Quaterniond(new AxisAngle4d(Math.toRadians(degrees), axis.x, axis.y, axis.z));
            Matrix3d rotation = new Matrix3d().set(quaternion);
            GeometryData copy = GeometryTransform.transformAround(geometry, center, rotation, 1.0d);
            if (copy == null) {
                writeFail("Geometry copy transform failed");
                return;
            }
            copies.add(copy);
        }

        if (copies.size() != resolvedCount) {
            writeFail("Emitted count does not match requested Count");
            return;
        }

        writeSuccess(copies);
    }

    static boolean isFullCircle(double totalAngleDegrees) {
        if (!Double.isFinite(totalAngleDegrees) || Math.abs(totalAngleDegrees) <= ANGLE_EPS) {
            return false;
        }
        double mod = Math.abs(totalAngleDegrees) % 360.0d;
        return mod <= ANGLE_EPS || Math.abs(mod - 360.0d) <= ANGLE_EPS;
    }

    public boolean isIncludeEnd() {
        return includeEnd;
    }

    public void setIncludeEnd(boolean includeEnd) {
        if (this.includeEnd != includeEnd) {
            this.includeEnd = includeEnd;
            markDirty();
        }
    }

    private void writeFail(String error) {
        markInvalid(error);
        putNullOutputs(OUTPUT_GEOMETRY_ID);
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, new DataTreeData(List.of()));
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    private void writeSuccess(List<GeometryData> copies) {
        markSuccess();
        if (copies.isEmpty()) {
            putNullOutputs(OUTPUT_GEOMETRY_ID);
        } else if (copies.size() == 1) {
            outputValues.put(OUTPUT_GEOMETRY_ID, copies.getFirst());
        } else {
            outputValues.put(OUTPUT_GEOMETRY_ID, new CompositeGeometryData(copies));
        }
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, buildCopyTree(copies));
        putIntOutputs(copies.size(), OUTPUT_COUNT_ID);
    }

    private DataTreeData buildCopyTree(List<GeometryData> copies) {
        List<DataTreeData.Branch> branches = new ArrayList<>(copies.size());
        for (int i = 0; i < copies.size(); i++) {
            branches.add(new DataTreeData.Branch(List.of(i), List.of(copies.get(i))));
        }
        return new DataTreeData(branches);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("includeEnd", includeEnd);
        state.put("count", count);
        state.put("totalAngle", totalAngle);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("includeEnd") instanceof Boolean value) {
            setIncludeEnd(value);
        }
        if (map.get("count") instanceof Number value) {
            count = value.intValue();
        }
        if (map.get("totalAngle") instanceof Number value) {
            totalAngle = value.doubleValue();
        }
    }
}
