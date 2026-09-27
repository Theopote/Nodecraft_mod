package com.nodecraft.nodesystem.nodes.geometry.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryBoundsResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Continuous AABB for any supported geometry (no voxelizer / discrete ports).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.analysis.geometry_bounds",
    displayName = "Geometry Bounds",
    description = "Calculates a continuous AABB from any supported geometry",
    category = "geometry.analysis",
    order = 1
)
public class GeometryBoundsNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_MIN_POINT_ID = "output_min_point";
    private static final String OUTPUT_MAX_POINT_ID = "output_max_point";
    private static final String OUTPUT_CENTER_POINT_ID = "output_center_point";
    private static final String OUTPUT_SIZE_X_ID = "output_size_x";
    private static final String OUTPUT_SIZE_Y_ID = "output_size_y";
    private static final String OUTPUT_SIZE_Z_ID = "output_size_z";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GeometryBoundsNode() {
        super(UUID.randomUUID(), "geometry.analysis.geometry_bounds");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Unified geometry input", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box",
            "Continuous axis-aligned bounding box", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_MIN_POINT_ID, "Min",
            "Minimum geometric corner", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_MAX_POINT_ID, "Max",
            "Maximum geometric corner", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_POINT_ID, "Center",
            "Geometric center of the bounds", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_X_ID, "Size X",
            "Geometric size on the X axis", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Y_ID, "Size Y",
            "Geometric size on the Y axis", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Z_ID, "Size Z",
            "Geometric size on the Z axis", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume",
            "Product of continuous size axes", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when geometry produced a finite AABB", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Calculates a continuous AABB from any supported geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeInvalid("Geometry input is required");
            return;
        }

        BoundingBoxData box = GeometryBoundsResolver.resolve(geometry);
        if (box == null || !box.isValid()) {
            writeInvalid("Unable to resolve continuous bounds for geometry");
            return;
        }

        Vector3d center = box.finiteCenter();
        Vector3d size = box.finiteSize();
        Double volume = box.finiteVolume();
        if (center == null || size == null || volume == null) {
            writeInvalid("Bounding box derived metrics are non-finite");
            return;
        }

        outputValues.put(OUTPUT_BOUNDING_BOX_ID, box);
        outputValues.put(OUTPUT_MIN_POINT_ID, new PointData(box.getMin()));
        outputValues.put(OUTPUT_MAX_POINT_ID, new PointData(box.getMax()));
        outputValues.put(OUTPUT_CENTER_POINT_ID, new PointData(center));
        outputValues.put(OUTPUT_SIZE_X_ID, size.x);
        outputValues.put(OUTPUT_SIZE_Y_ID, size.y);
        outputValues.put(OUTPUT_SIZE_Z_ID, size.z);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
        outputValues.put(OUTPUT_MIN_POINT_ID, null);
        outputValues.put(OUTPUT_MAX_POINT_ID, null);
        outputValues.put(OUTPUT_CENTER_POINT_ID, null);
        outputValues.put(OUTPUT_SIZE_X_ID, Double.NaN);
        outputValues.put(OUTPUT_SIZE_Y_ID, Double.NaN);
        outputValues.put(OUTPUT_SIZE_Z_ID, Double.NaN);
        outputValues.put(OUTPUT_VOLUME_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
