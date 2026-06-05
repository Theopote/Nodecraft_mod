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
    id = "transform.basic_transforms.move_geometry",
    displayName = "Move Geometry",
    description = "Moves analytic geometry by a translation vector",
    category = "transform.basic_transforms",
    order = 13
)
public class MoveGeometryNode extends BaseNode {

    @NodeProperty(displayName = "X", category = "Translation", order = 1)
    private double x = 0.0d;

    @NodeProperty(displayName = "Y", category = "Translation", order = 2)
    private double y = 0.0d;

    @NodeProperty(displayName = "Z", category = "Translation", order = 3)
    private double z = 0.0d;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_TRANSLATION_ID = "input_translation";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MoveGeometryNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.move_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to move", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_TRANSLATION_ID, "Translation", "Translation vector override", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Moved geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when geometry was moved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Moves analytic geometry by a translation vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(null, false);
            return;
        }

        Vector3d translation = inputValues.get(INPUT_TRANSLATION_ID) instanceof Vector3d vector
            ? new Vector3d(vector)
            : new Vector3d(x, y, z);
        GeometryData moved = GeometryTransform.transform(geometry, translation, 0.0d, 0.0d, 0.0d, 1.0d);
        writeResult(moved, moved != null);
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        if (Double.compare(this.x, x) != 0) {
            this.x = x;
            markDirty();
        }
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        if (Double.compare(this.y, y) != 0) {
            this.y = y;
            markDirty();
        }
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        if (Double.compare(this.z, z) != 0) {
            this.z = z;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of("x", x, "y", y, "z", z);
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("x") instanceof Number value) setX(value.doubleValue());
        if (map.get("y") instanceof Number value) setY(value.doubleValue());
        if (map.get("z") instanceof Number value) setZ(value.doubleValue());
    }

    private void writeResult(@Nullable GeometryData geometry, boolean valid) {
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}
