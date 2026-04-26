package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.deconstruct_octahedron",
    displayName = "Deconstruct Octahedron",
    description = "Extracts center, size, vertices, bounds, and analytical values from octahedron geometry",
    category = "geometry.primitives",
    order = 16
)
public class DeconstructOctahedronNode extends BaseNode {

    private static final String INPUT_OCTAHEDRON_ID = "input_octahedron";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_SIZE_ID = "output_size";
    private static final String OUTPUT_VERTICES_ID = "output_vertices";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructOctahedronNode() {
        super(UUID.randomUUID(), "geometry.primitives.deconstruct_octahedron");

        addInputPort(new BasePort(INPUT_OCTAHEDRON_ID, "Octahedron", "Octahedron geometry to deconstruct", NodeDataType.OCTAHEDRON_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Octahedron center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_ID, "Size", "Distance from center to vertices", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Vertices", "Resolved octahedron vertices", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Regular octahedron surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Regular octahedron volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Geometric axis-aligned bounds", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when octahedron input is present", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts center, size, vertices, bounds, and analytical values from octahedron geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object octahedronObj = inputValues.get(INPUT_OCTAHEDRON_ID);
        if (!(octahedronObj instanceof OctahedronGeometryData octahedron)) {
            writeEmptyOutputs();
            return;
        }

        double size = octahedron.getVertexRadius();
        double edgeLength = size * Math.sqrt(2.0d);
        double surfaceArea = 2.0d * Math.sqrt(3.0d) * edgeLength * edgeLength;
        double volume = (Math.sqrt(2.0d) / 3.0d) * edgeLength * edgeLength * edgeLength;
        RegionData region = GeometryVoxelizer.createBoundingRegion(octahedron);
        BoundingBoxData boundingBox = createBoundingBox(region);

        outputValues.put(OUTPUT_CENTER_ID, octahedron.getCenter());
        outputValues.put(OUTPUT_SIZE_ID, size);
        outputValues.put(OUTPUT_VERTICES_ID, octahedron.getVertices());
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_SIZE_ID, 0.0d);
        outputValues.put(OUTPUT_VERTICES_ID, java.util.List.of());
        outputValues.put(OUTPUT_SURFACE_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_VOLUME_ID, 0.0d);
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private BoundingBoxData createBoundingBox(RegionData region) {
        if (region == null || !region.isComplete()) {
            return null;
        }
        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return null;
        }
        return new BoundingBoxData(
            new Vector3d(minCorner.getX(), minCorner.getY(), minCorner.getZ()),
            new Vector3d(maxCorner.getX() + 1.0d, maxCorner.getY() + 1.0d, maxCorner.getZ() + 1.0d)
        );
    }
}
