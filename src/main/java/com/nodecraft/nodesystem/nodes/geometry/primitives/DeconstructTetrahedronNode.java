package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.deconstruct_tetrahedron",
    displayName = "Deconstruct Tetrahedron",
    description = "Extracts center, edge length, vertices, bounds, and analytical values from tetrahedron geometry",
    category = "geometry.primitives",
    order = 17
)
public class DeconstructTetrahedronNode extends BaseNode {

    private static final String INPUT_TETRAHEDRON_ID = "input_tetrahedron";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_EDGE_ID = "output_edge";
    private static final String OUTPUT_CIRCUMRADIUS_ID = "output_circumradius";
    private static final String OUTPUT_VERTICES_ID = "output_vertices";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_ORIENTATION_ID = "output_orientation";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructTetrahedronNode() {
        super(UUID.randomUUID(), "geometry.primitives.deconstruct_tetrahedron");

        addInputPort(new BasePort(INPUT_TETRAHEDRON_ID, "Tetrahedron", "Tetrahedron geometry to deconstruct", NodeDataType.TETRAHEDRON_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Tetrahedron center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_ID, "Edge Length", "Resolved edge length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CIRCUMRADIUS_ID, "Circumradius", "Distance from center to vertices", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Vertices", "Resolved tetrahedron vertices", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Regular tetrahedron surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Regular tetrahedron volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Geometric axis-aligned bounds", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_ORIENTATION_ID, "Orientation", "Rotation matrix (local vertex frame → world)", NodeDataType.MATRIX3, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when tetrahedron input is present", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts center, edge length, vertices, bounds, and analytical values from tetrahedron geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object tetrahedronObj = inputValues.get(INPUT_TETRAHEDRON_ID);
        if (!(tetrahedronObj instanceof TetrahedronGeometryData tetrahedron)) {
            writeEmptyOutputs();
            return;
        }

        double edgeLength = tetrahedron.getEdgeLength();
        double surfaceArea = Math.sqrt(3.0d) * edgeLength * edgeLength;
        double volume = (edgeLength * edgeLength * edgeLength) / (6.0d * Math.sqrt(2.0d));
        RegionData region = GeometryVoxelizer.createBoundingRegion(tetrahedron);
        BoundingBoxData boundingBox = createBoundingBox(region);

        outputValues.put(OUTPUT_CENTER_ID, tetrahedron.getCenter());
        outputValues.put(OUTPUT_EDGE_ID, edgeLength);
        outputValues.put(OUTPUT_CIRCUMRADIUS_ID, tetrahedron.getCircumradius());
        outputValues.put(OUTPUT_VERTICES_ID, tetrahedron.getVertices());
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_ORIENTATION_ID, tetrahedron.getOrientationMatrix());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_EDGE_ID, 0.0d);
        outputValues.put(OUTPUT_CIRCUMRADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_VERTICES_ID, java.util.List.of());
        outputValues.put(OUTPUT_SURFACE_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_VOLUME_ID, 0.0d);
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
        outputValues.put(OUTPUT_ORIENTATION_ID, new Matrix3d().identity());
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
