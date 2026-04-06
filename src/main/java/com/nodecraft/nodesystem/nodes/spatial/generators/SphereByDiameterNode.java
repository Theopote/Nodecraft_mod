package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "spatial.generators.sphere_by_diameter",
    displayName = "Sphere By Diameter",
    description = "Constructs sphere geometry from two diameter endpoints",
    category = "spatial.generators"
)
public class SphereByDiameterNode extends BaseNode {

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";

    private static final String OUTPUT_SPHERE_ID = "output_sphere";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_DIAMETER_ID = "output_diameter";
    private static final String OUTPUT_DIAMETER_LINE_ID = "output_diameter_line";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SphereByDiameterNode() {
        super(UUID.randomUUID(), "spatial.generators.sphere_by_diameter");

        addInputPort(new BasePort(INPUT_START_ID, "Point A", "First diameter endpoint", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_END_ID, "Point B", "Second diameter endpoint", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_SPHERE_ID, "Sphere", "Constructed sphere geometry", NodeDataType.SPHERE, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved sphere center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_DIAMETER_ID, "Diameter", "Diameter length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_DIAMETER_LINE_ID, "Diameter Line", "Line segment between both diameter endpoints", NodeDataType.LINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a sphere could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs sphere geometry from two diameter endpoints";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d start = resolvePoint(inputValues.get(INPUT_START_ID));
        Vector3d end = resolvePoint(inputValues.get(INPUT_END_ID));

        if (start == null || end == null) {
            writeEmptyOutputs();
            return;
        }

        Vector3d center = new Vector3d(start).add(end).mul(0.5d);
        double diameter = start.distance(end);
        double radius = diameter * 0.5d;

        SphereData sphere = new SphereData(center, radius);
        LineData diameterLine = new LineData(
            new Vec3d(start.x, start.y, start.z),
            new Vec3d(end.x, end.y, end.z)
        );

        outputValues.put(OUTPUT_SPHERE_ID, sphere);
        outputValues.put(OUTPUT_GEOMETRY_ID, sphere);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_DIAMETER_ID, diameter);
        outputValues.put(OUTPUT_DIAMETER_LINE_ID, diameterLine);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SPHERE_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_DIAMETER_ID, 0.0d);
        outputValues.put(OUTPUT_DIAMETER_LINE_ID, null);
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
