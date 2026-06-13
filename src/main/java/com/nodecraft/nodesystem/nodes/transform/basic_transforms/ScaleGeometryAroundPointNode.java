package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryTransform;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "transform.basic_transforms.scale_geometry_point",
    displayName = "Scale Geometry Around Point",
    description = "Uniformly scales analytic geometry around a center point",
    category = "transform.basic_transforms",
    order = 15
)
public class ScaleGeometryAroundPointNode extends BaseNode {

    @NodeProperty(displayName = "Default Scale", category = "Scale", order = 1)
    private double defaultScale = 1.0d;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_SCALE_ID = "input_scale";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_EFFECTIVE_SCALE_ID = "output_effective_scale";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ScaleGeometryAroundPointNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.scale_geometry_point");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to scale", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Scale center point", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_SCALE_ID, "Scale", "Uniform scale factor", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Scaled geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_SCALE_ID, "Effective Scale", "Scale factor actually applied", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when geometry was scaled", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Uniformly scales analytic geometry around a center point";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(null, false);
            return;
        }

        Vector3d center = inputValues.get(INPUT_CENTER_ID) instanceof Vector3d value ? new Vector3d(value) : new Vector3d();
        double scale = getInputDouble(INPUT_SCALE_ID, defaultScale);
        if (!isFinite(center) || !Double.isFinite(scale) || Math.abs(scale) <= 1.0e-9d) {
            writeResult(null, false);
            return;
        }

        Vector3d translation = new Vector3d(center).mul(1.0d - scale);
        GeometryData scaled = GeometryTransform.transform(geometry, translation, 0.0d, 0.0d, 0.0d, scale);
        writeResult(scaled, scaled != null);
    }

    public double getDefaultScale() {
        return defaultScale;
    }

    public void setDefaultScale(double defaultScale) {
        if (Double.compare(this.defaultScale, defaultScale) != 0) {
            this.defaultScale = defaultScale;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of("defaultScale", defaultScale);
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("defaultScale") instanceof Number value) {
            setDefaultScale(value.doubleValue());
        }
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private void writeResult(@Nullable GeometryData geometry, boolean valid) {
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_EFFECTIVE_SCALE_ID, valid ? getInputDouble(INPUT_SCALE_ID, defaultScale) : 0.0d);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}
