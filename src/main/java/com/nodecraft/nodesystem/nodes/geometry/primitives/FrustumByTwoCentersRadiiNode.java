package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.frustum_cone",
    displayName = "Frustum By Two Centers Radii",
    description = "Constructs a circular frustum from two parallel face centers and their radii (set top radius to 0 for a cone)",
    category = "geometry.primitives",
    order = 7
)
public class FrustumByTwoCentersRadiiNode extends BaseNode {

    private static final String INPUT_BASE_CENTER_ID = "input_base_center";
    private static final String INPUT_TOP_CENTER_ID = "input_top_center";
    private static final String INPUT_BASE_RADIUS_ID = "input_base_radius";
    private static final String INPUT_TOP_RADIUS_ID = "input_top_radius";

    private static final String OUTPUT_FRUSTUM_ID = "output_frustum";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_AXIS_LINE_ID = "output_axis_line";
    private static final String OUTPUT_AXIS_VECTOR_ID = "output_axis_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public FrustumByTwoCentersRadiiNode() {
        super(UUID.randomUUID(), "geometry.primitives.frustum_cone");

        addInputPort(new BasePort(INPUT_BASE_CENTER_ID, "Base Center", "Center of the base circular face", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_TOP_CENTER_ID, "Top Center", "Center of the top circular face", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_BASE_RADIUS_ID, "Base Radius", "Radius at the base face", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TOP_RADIUS_ID, "Top Radius", "Radius at the top face (0 for a sharp cone)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FRUSTUM_ID, "Frustum", "Constructed frustum geometry", NodeDataType.FRUSTUM_CONE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_LINE_ID, "Axis Line", "Line through base and top centers", NodeDataType.LINE, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Vector from base center to top center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Distance between face centers", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a frustum could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a circular frustum from two parallel face centers and their radii (set top radius to 0 for a cone)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d base = resolvePoint(inputValues.get(INPUT_BASE_CENTER_ID));
        Vector3d top = resolvePoint(inputValues.get(INPUT_TOP_CENTER_ID));
        Object baseRadObj = inputValues.get(INPUT_BASE_RADIUS_ID);
        Object topRadObj = inputValues.get(INPUT_TOP_RADIUS_ID);

        if (base == null || top == null || !(baseRadObj instanceof Number baseNum) || !(topRadObj instanceof Number topNum)) {
            writeEmptyOutputs();
            return;
        }

        double baseRadius = baseNum.doubleValue();
        double topRadius = topNum.doubleValue();
        Vector3d axisVector = new Vector3d(top).sub(base);
        double height = axisVector.length();
        if (!Double.isFinite(baseRadius) || !Double.isFinite(topRadius) || baseRadius < 0.0d || topRadius < 0.0d) {
            writeEmptyOutputs();
            return;
        }
        if (height <= 1.0e-9d || (baseRadius <= 1.0e-9d && topRadius <= 1.0e-9d)) {
            writeEmptyOutputs();
            return;
        }

        try {
            FrustumConeGeometryData frustum = new FrustumConeGeometryData(base, top, baseRadius, topRadius);
            LineData axisLine = new LineData(
                new Vec3d(base.x, base.y, base.z),
                new Vec3d(top.x, top.y, top.z)
            );

            outputValues.put(OUTPUT_FRUSTUM_ID, frustum);
            outputValues.put(OUTPUT_GEOMETRY_ID, frustum);
            outputValues.put(OUTPUT_AXIS_LINE_ID, axisLine);
            outputValues.put(OUTPUT_AXIS_VECTOR_ID, axisVector);
            outputValues.put(OUTPUT_HEIGHT_ID, height);
            outputValues.put(OUTPUT_VALID_ID, true);
        } catch (IllegalArgumentException ex) {
            writeEmptyOutputs();
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_FRUSTUM_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_AXIS_LINE_ID, null);
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, null);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }
}
