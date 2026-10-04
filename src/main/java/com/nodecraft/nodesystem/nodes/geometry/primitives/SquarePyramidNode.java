package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.square_pyramid",
    displayName = "Square Pyramid",
    description = "Constructs square pyramid geometry from a base center, base size, height, and plane",
    category = "geometry.primitives",
    order = 12
)
public class SquarePyramidNode extends AbstractPrimitiveNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_BASE_SIZE_ID = "input_base_size";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_X_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_APEX_ID = "output_apex";
    private static final String OUTPUT_BASE_POINTS_ID = "output_base_points";
    private static final String OUTPUT_BASE_SIZE_ID = "output_base_size";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_PLANE_ID = "output_plane";

    @NodeProperty(displayName = "Base Center X", category = "Center", order = 1)
    private double centerX = 0.0d;
    @NodeProperty(displayName = "Base Center Y", category = "Center", order = 2)
    private double centerY = 0.0d;
    @NodeProperty(displayName = "Base Center Z", category = "Center", order = 3)
    private double centerZ = 0.0d;

    @NodeProperty(displayName = "Base Size", category = "Size", order = 10)
    private double baseSize = 5.0d;

    @NodeProperty(displayName = "Height", category = "Size", order = 11)
    private double height = 5.0d;

    public SquarePyramidNode() {
        super("geometry.primitives.square_pyramid");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Base Center", "Center point of the square base", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_BASE_SIZE_ID, "Base Size", "Length of each base edge", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Distance from base plane to apex", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Construction plane for the square base", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Optional in-plane axis to rotate the square base", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Square pyramid geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_APEX_ID, "Apex", "Apex point above the base plane", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Four square base corners", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BASE_SIZE_ID, "Base Size", "Resolved base size", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Resolved height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved base plane", NodeDataType.PLANE, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Constructs square pyramid geometry from a base center, base size, height, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveOptionalPoint(INPUT_CENTER_ID, new Vector3d(centerX, centerY, centerZ));
        if (center == null) {
            writeEmptyOutputs(isPortConnected(INPUT_CENTER_ID)
                ? "Base center input is invalid"
                : "Square pyramid requires a finite base center");
            return;
        }

        Double resolvedBaseSize = resolvePositiveDouble(INPUT_BASE_SIZE_ID, baseSize);
        Double resolvedHeight = resolvePositiveDouble(INPUT_HEIGHT_ID, height);
        if (resolvedBaseSize == null || resolvedHeight == null) {
            writeEmptyOutputs("Base size and height must be finite and > 0");
            return;
        }

        PlaneData plane = resolveOptionalPlane(INPUT_PLANE_ID, PlaneData.XZ_PLANE);
        if (isPortConnected(INPUT_PLANE_ID) && plane == null) {
            writeEmptyOutputs("Plane input is invalid");
            return;
        }

        Vector3d preferredXAxis = resolveOptionalVector(INPUT_X_AXIS_ID, null);
        if (isPortConnected(INPUT_X_AXIS_ID) && preferredXAxis == null) {
            writeEmptyOutputs("X axis input must be a usable vector");
            return;
        }

        FrameData frame;
        if (isPortConnected(INPUT_X_AXIS_ID)) {
            frame = FrameUtils.fromPlaneRequireHint(plane, preferredXAxis);
            if (frame == null) {
                writeEmptyOutputs("X axis must be usable in the base plane");
                return;
            }
        } else {
            frame = FrameUtils.fromPlane(plane, preferredXAxis);
            if (frame == null) {
                writeEmptyOutputs("Square pyramid basis could not be constructed");
                return;
            }
        }

        PlaneData resolvedPlane = new PlaneData(center, frame.getZAxis());
        SquarePyramidGeometryData geometry = new SquarePyramidGeometryData(
            center,
            frame.getXAxis(),
            frame.getYAxis(),
            frame.getZAxis(),
            resolvedBaseSize,
            resolvedHeight
        );

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_APEX_ID, new PointData(geometry.getApex()));
        outputValues.put(OUTPUT_BASE_POINTS_ID, SpatialValueResolver.toPointDataList(geometry.getBaseVertices()));
        outputValues.put(OUTPUT_BASE_SIZE_ID, resolvedBaseSize);
        outputValues.put(OUTPUT_HEIGHT_ID, resolvedHeight);
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_GEOMETRY_ID, OUTPUT_APEX_ID, OUTPUT_PLANE_ID);
        putEmptyListOutputs(OUTPUT_BASE_POINTS_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_BASE_SIZE_ID, OUTPUT_HEIGHT_ID);
        markInvalid(reason);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("centerX", centerX);
        state.put("centerY", centerY);
        state.put("centerZ", centerZ);
        state.put("baseSize", baseSize);
        state.put("height", height);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        restoreFiniteDouble(map, "centerX", v -> centerX = v);
        restoreFiniteDouble(map, "centerY", v -> centerY = v);
        restoreFiniteDouble(map, "centerZ", v -> centerZ = v);
        restoreFiniteDouble(map, "baseSize", v -> baseSize = v);
        restoreFiniteDouble(map, "height", v -> height = v);
    }
}
