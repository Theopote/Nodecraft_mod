package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.ellipsoid",
    displayName = "Ellipsoid By Center Radii",
    description = "Constructs ellipsoid geometry from a center point and X/Y/Z radii",
    category = "geometry.primitives",
    order = 9
)
public class EllipsoidByCenterRadiiNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_X_ID = "input_radius_x";
    private static final String INPUT_RADIUS_Y_ID = "input_radius_y";
    private static final String INPUT_RADIUS_Z_ID = "input_radius_z";

    private static final String OUTPUT_ELLIPSOID_ID = "output_ellipsoid";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_RADII_ID = "output_radii";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public EllipsoidByCenterRadiiNode() {
        super(UUID.randomUUID(), "geometry.primitives.ellipsoid");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Ellipsoid center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_RADIUS_X_ID, "Radius X", "Ellipsoid X radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_Y_ID, "Radius Y", "Ellipsoid Y radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_Z_ID, "Radius Z", "Ellipsoid Z radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ELLIPSOID_ID, "Ellipsoid", "Constructed ellipsoid geometry", NodeDataType.ELLIPSOID_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved ellipsoid center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_RADII_ID, "Radii", "Resolved ellipsoid radii vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when an ellipsoid could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs ellipsoid geometry from a center point and X/Y/Z radii";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object rxObj = inputValues.get(INPUT_RADIUS_X_ID);
        Object ryObj = inputValues.get(INPUT_RADIUS_Y_ID);
        Object rzObj = inputValues.get(INPUT_RADIUS_Z_ID);

        if (center == null || !(rxObj instanceof Number rx) || !(ryObj instanceof Number ry) || !(rzObj instanceof Number rz)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d radii = new Vector3d(rx.doubleValue(), ry.doubleValue(), rz.doubleValue());
        if (!Double.isFinite(radii.x) || !Double.isFinite(radii.y) || !Double.isFinite(radii.z)
            || radii.x < 0.0d || radii.y < 0.0d || radii.z < 0.0d) {
            writeEmptyOutputs();
            return;
        }

        EllipsoidGeometryData ellipsoid = new EllipsoidGeometryData(center, radii);
        outputValues.put(OUTPUT_ELLIPSOID_ID, ellipsoid);
        outputValues.put(OUTPUT_GEOMETRY_ID, ellipsoid);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_RADII_ID, radii);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_ELLIPSOID_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_RADII_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) return pointData.getPosition();
        if (value instanceof Vector3d vector) return new Vector3d(vector);
        if (value instanceof BlockPos blockPos) return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        return null;
    }
}
