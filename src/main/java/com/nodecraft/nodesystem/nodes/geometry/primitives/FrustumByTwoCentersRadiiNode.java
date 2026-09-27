package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.frustum_cone",
    displayName = "Frustum By Two Centers Radii",
    description = "Constructs a circular frustum from two parallel face centers and their radii (set top radius to 0 for a cone)",
    category = "geometry.primitives",
    order = 7
)
public class FrustumByTwoCentersRadiiNode extends AbstractPrimitiveNode {

    private static final String INPUT_BASE_CENTER_ID = "input_base_center";
    private static final String INPUT_TOP_CENTER_ID = "input_top_center";
    private static final String INPUT_BASE_RADIUS_ID = "input_base_radius";
    private static final String INPUT_TOP_RADIUS_ID = "input_top_radius";

    private static final String OUTPUT_FRUSTUM_ID = "output_frustum";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_AXIS_PATH_ID = "output_axis_path";
    private static final String OUTPUT_AXIS_VECTOR_ID = "output_axis_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";

    @NodeProperty(displayName = "Base Radius", category = "Size", order = 10)
    private double baseRadius = 4.0d;

    @NodeProperty(displayName = "Top Radius", category = "Size", order = 11)
    private double topRadius = 2.0d;

    public FrustumByTwoCentersRadiiNode() {
        super("geometry.primitives.frustum_cone");

        addInputPort(new BasePort(INPUT_BASE_CENTER_ID, "Base Center", "Center of the base circular face", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_TOP_CENTER_ID, "Top Center", "Center of the top circular face", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_BASE_RADIUS_ID, "Base Radius", "Radius at the base face", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TOP_RADIUS_ID, "Top Radius", "Radius at the top face (0 for a sharp cone)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FRUSTUM_ID, "Frustum", "Constructed frustum geometry", NodeDataType.FRUSTUM_CONE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_PATH_ID, "Axis Path", "Path through base and top centers", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Vector from base center to top center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Distance between face centers", NodeDataType.DOUBLE, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Constructs a circular frustum from two parallel face centers and their radii (set top radius to 0 for a cone)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d base = resolveOptionalPoint(INPUT_BASE_CENTER_ID, null);
        if (base == null) {
            writeEmptyOutputs(isPortConnected(INPUT_BASE_CENTER_ID)
                ? "Base center input is invalid"
                : "Frustum requires a finite base center");
            return;
        }

        Vector3d top = resolveOptionalPoint(INPUT_TOP_CENTER_ID, null);
        if (top == null) {
            writeEmptyOutputs(isPortConnected(INPUT_TOP_CENTER_ID)
                ? "Top center input is invalid"
                : "Frustum requires a finite top center");
            return;
        }

        Double resolvedBaseRadius = resolveNonNegativeDouble(INPUT_BASE_RADIUS_ID, baseRadius);
        Double resolvedTopRadius = resolveNonNegativeDouble(INPUT_TOP_RADIUS_ID, topRadius);
        if (resolvedBaseRadius == null || resolvedTopRadius == null) {
            writeEmptyOutputs("Frustum radii must be finite and >= 0");
            return;
        }

        String error = PrimitiveGeometryValidator.validateFrustum(base, top, resolvedBaseRadius, resolvedTopRadius);
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        Vector3d axisVector = new Vector3d(top).sub(base);
        double height = axisVector.length();
        FrustumConeGeometryData frustum = new FrustumConeGeometryData(base, top, resolvedBaseRadius, resolvedTopRadius);

        outputValues.put(OUTPUT_FRUSTUM_ID, frustum);
        outputValues.put(OUTPUT_GEOMETRY_ID, frustum);
        outputValues.put(OUTPUT_AXIS_PATH_ID, pathFromLine(base, top));
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, axisVector);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_FRUSTUM_ID, OUTPUT_GEOMETRY_ID, OUTPUT_AXIS_PATH_ID, OUTPUT_AXIS_VECTOR_ID);
        putDoubleOutputs(0.0d, OUTPUT_HEIGHT_ID);
        markInvalid(reason);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("baseRadius", baseRadius);
        state.put("topRadius", topRadius);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("baseRadius") instanceof Number n) baseRadius = n.doubleValue();
        if (map.get("topRadius") instanceof Number n) topRadius = n.doubleValue();
    }
}
