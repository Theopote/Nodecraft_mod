package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.torus",
    displayName = "Torus By Center Axis Radii",
    description = "Constructs torus geometry from a center point, symmetry axis direction, major radius, and tube (minor) radius",
    category = "geometry.primitives",
    order = 6
)
public class TorusByCenterAxisRadiiNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_MAJOR_RADIUS_ID = "input_major_radius";
    private static final String INPUT_MINOR_RADIUS_ID = "input_minor_radius";

    private static final String OUTPUT_TORUS_ID = "output_torus";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_AXIS_NORMALIZED_ID = "output_axis_normalized";
    private static final String OUTPUT_MAJOR_RADIUS_ID = "output_major_radius";
    private static final String OUTPUT_MINOR_RADIUS_ID = "output_minor_radius";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TorusByCenterAxisRadiiNode() {
        super(UUID.randomUUID(), "geometry.primitives.torus");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Torus center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Symmetry axis direction (tube runs around this axis)", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_MAJOR_RADIUS_ID, "Major Radius", "Distance from center to tube center", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MINOR_RADIUS_ID, "Minor Radius", "Tube cross-section radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_TORUS_ID, "Torus", "Constructed torus geometry", NodeDataType.TORUS_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_NORMALIZED_ID, "Axis Normalized", "Unit axis direction stored on the torus", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_MAJOR_RADIUS_ID, "Major Radius", "Resolved major radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_MINOR_RADIUS_ID, "Minor Radius", "Resolved minor radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a torus could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs torus geometry from a center point, symmetry axis direction, major radius, and tube (minor) radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Vector3d axis = resolveVector(inputValues.get(INPUT_AXIS_ID));
        Object majorObj = inputValues.get(INPUT_MAJOR_RADIUS_ID);
        Object minorObj = inputValues.get(INPUT_MINOR_RADIUS_ID);

        if (center == null || axis == null || !(majorObj instanceof Number majorNum) || !(minorObj instanceof Number minorNum)) {
            writeEmptyOutputs();
            return;
        }

        double axisLen = axis.length();
        if (axisLen <= 1.0e-9d) {
            writeEmptyOutputs();
            return;
        }

        double major = majorNum.doubleValue();
        double minor = minorNum.doubleValue();
        if (!Double.isFinite(major) || !Double.isFinite(minor) || major <= 0.0d || minor <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        TorusGeometryData torus = new TorusGeometryData(center, axis, major, minor);
        Vector3d axisNorm = torus.getAxis();

        outputValues.put(OUTPUT_TORUS_ID, torus);
        outputValues.put(OUTPUT_GEOMETRY_ID, torus);
        outputValues.put(OUTPUT_AXIS_NORMALIZED_ID, axisNorm);
        outputValues.put(OUTPUT_MAJOR_RADIUS_ID, major);
        outputValues.put(OUTPUT_MINOR_RADIUS_ID, minor);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_TORUS_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_AXIS_NORMALIZED_ID, null);
        outputValues.put(OUTPUT_MAJOR_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_MINOR_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof PlaneData planeData) {
            return planeData.getPoint();
        }
        if (value instanceof Coordinate coordinate) {
            return new Vector3d(coordinate.getX(), coordinate.getY(), coordinate.getZ());
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vec3d) {
            return new Vector3d(vec3d.x, vec3d.y, vec3d.z);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }

    private Vector3d resolveVector(Object value) {
        if (value instanceof LineData lineData) {
            Vec3d start = lineData.getStart();
            Vec3d end = lineData.getEnd();
            return new Vector3d(end.x - start.x, end.y - start.y, end.z - start.z);
        }
        if (value instanceof PlaneData planeData) {
            return planeData.getNormal();
        }
        return resolvePoint(value);
    }
}
