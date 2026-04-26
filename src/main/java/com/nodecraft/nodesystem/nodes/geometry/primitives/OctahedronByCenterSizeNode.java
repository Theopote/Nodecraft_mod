package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.octahedron",
    displayName = "Octahedron By Center Size",
    description = "Constructs octahedron geometry from a center point and vertex radius",
    category = "geometry.primitives",
    order = 10
)
public class OctahedronByCenterSizeNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_SIZE_ID = "input_size";

    private static final String OUTPUT_OCTAHEDRON_ID = "output_octahedron";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_SIZE_ID = "output_size";
    private static final String OUTPUT_VERTICES_ID = "output_vertices";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OctahedronByCenterSizeNode() {
        super(UUID.randomUUID(), "geometry.primitives.octahedron");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Octahedron center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_SIZE_ID, "Size", "Distance from center to vertices", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_OCTAHEDRON_ID, "Octahedron", "Constructed octahedron geometry", NodeDataType.OCTAHEDRON_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_ID, "Size", "Resolved vertex radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Vertices", "Resolved octahedron vertices", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when an octahedron could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs octahedron geometry from a center point and vertex radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object sizeObj = inputValues.get(INPUT_SIZE_ID);
        if (center == null || !(sizeObj instanceof Number sizeNumber)) {
            writeEmptyOutputs();
            return;
        }

        double size = sizeNumber.doubleValue();
        if (!Double.isFinite(size) || size < 0.0d) {
            writeEmptyOutputs();
            return;
        }

        OctahedronGeometryData octahedron = new OctahedronGeometryData(center, size);
        outputValues.put(OUTPUT_OCTAHEDRON_ID, octahedron);
        outputValues.put(OUTPUT_GEOMETRY_ID, octahedron);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_SIZE_ID, size);
        outputValues.put(OUTPUT_VERTICES_ID, octahedron.getVertices());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_OCTAHEDRON_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_SIZE_ID, 0.0d);
        outputValues.put(OUTPUT_VERTICES_ID, java.util.List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) return pointData.getPosition();
        if (value instanceof Vector3d vector) return new Vector3d(vector);
        if (value instanceof BlockPos blockPos) return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        return null;
    }
}
