package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.square_pyramid",
    displayName = "Square Pyramid",
    description = "Constructs square pyramid geometry from a base center, base size, height, and plane",
    category = "geometry.primitives",
    order = 12
)
public class SquarePyramidNode extends BaseNode {

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
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SquarePyramidNode() {
        super(UUID.randomUUID(), "geometry.primitives.square_pyramid");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Base Center", "Center point of the square base", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_BASE_SIZE_ID, "Base Size", "Length of each base edge", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Distance from base plane to apex", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Construction plane for the square base", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Optional in-plane axis to rotate the square base", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Square pyramid geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_APEX_ID, "Apex", "Apex point above the base plane", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Four square base corners", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BASE_SIZE_ID, "Base Size", "Resolved base size", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Resolved height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved base plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the pyramid could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs square pyramid geometry from a base center, base size, height, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object baseSizeObj = inputValues.get(INPUT_BASE_SIZE_ID);
        Object heightObj = inputValues.get(INPUT_HEIGHT_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferredXAxis = inputValues.get(INPUT_X_AXIS_ID) instanceof Vector3d vector ? new Vector3d(vector) : null;

        if (center == null || !(baseSizeObj instanceof Number baseSizeNumber) || !(heightObj instanceof Number heightNumber)) {
            writeEmptyOutputs();
            return;
        }

        double baseSize = baseSizeNumber.doubleValue();
        double height = heightNumber.doubleValue();
        if (!Double.isFinite(baseSize) || !Double.isFinite(height) || baseSize <= 0.0d || height <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        Basis basis = createBasis(plane, preferredXAxis);
        if (basis == null) {
            writeEmptyOutputs();
            return;
        }

        PlaneData resolvedPlane = new PlaneData(center, basis.normal);
        SquarePyramidGeometryData geometry = new SquarePyramidGeometryData(
            center,
            basis.xAxis,
            basis.yAxis,
            basis.normal,
            baseSize,
            height
        );

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_APEX_ID, geometry.getApex());
        outputValues.put(OUTPUT_BASE_POINTS_ID, geometry.getBaseVertices());
        outputValues.put(OUTPUT_BASE_SIZE_ID, baseSize);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_APEX_ID, null);
        outputValues.put(OUTPUT_BASE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_BASE_SIZE_ID, 0.0d);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Basis createBasis(PlaneData plane, @Nullable Vector3d preferredXAxis) {
        Vector3d normal = plane.getNormal();
        if (normal.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        normal.normalize();

        Vector3d xAxis = preferredXAxis != null ? new Vector3d(preferredXAxis) : null;
        if (xAxis != null) {
            xAxis.sub(new Vector3d(normal).mul(xAxis.dot(normal)));
        }
        if (xAxis == null || xAxis.lengthSquared() <= 1.0e-12d) {
            xAxis = fallbackAxis(normal);
        }
        if (xAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        xAxis.normalize();

        Vector3d yAxis = new Vector3d(normal).cross(xAxis);
        if (yAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        yAxis.normalize();
        xAxis = new Vector3d(yAxis).cross(normal).normalize();

        return new Basis(xAxis, yAxis, normal);
    }

    private Vector3d fallbackAxis(Vector3d normal) {
        Vector3d reference = Math.abs(normal.z) < 0.99d
            ? new Vector3d(0.0d, 0.0d, 1.0d)
            : new Vector3d(0.0d, 1.0d, 0.0d);
        return reference.sub(new Vector3d(normal).mul(reference.dot(normal)));
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) return pointData.getPosition();
        if (value instanceof Vector3d vector) return new Vector3d(vector);
        if (value instanceof BlockPos blockPos) return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        return null;
    }

    private record Basis(Vector3d xAxis, Vector3d yAxis, Vector3d normal) { }
}
