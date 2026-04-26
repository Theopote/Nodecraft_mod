package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.deconstruct_dodecahedron",
    displayName = "Deconstruct Dodecahedron",
    description = "Extracts center, edge length, vertices, bounds, and analytical values from dodecahedron geometry",
    category = "geometry.primitives",
    order = 26
)
public class DeconstructDodecahedronNode extends BaseNode {

    private static final String INPUT_DODECAHEDRON_ID = "input_dodecahedron";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_EDGE_LENGTH_ID = "output_edge_length";
    private static final String OUTPUT_CIRCUMRADIUS_ID = "output_circumradius";
    private static final String OUTPUT_VERTICES_ID = "output_vertices";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructDodecahedronNode() {
        super(UUID.randomUUID(), "geometry.primitives.deconstruct_dodecahedron");

        addInputPort(new BasePort(INPUT_DODECAHEDRON_ID, "Dodecahedron", "Dodecahedron geometry to deconstruct", NodeDataType.DODECAHEDRON_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Dodecahedron center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_LENGTH_ID, "Edge Length", "Edge length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CIRCUMRADIUS_ID, "Circumradius", "Circumscribed sphere radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Vertices", "World-space vertices", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Total surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Interior volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Axis-aligned bounds", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when input is present", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts center, edge length, vertices, bounds, and analytical values from dodecahedron geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object obj = inputValues.get(INPUT_DODECAHEDRON_ID);
        if (!(obj instanceof DodecahedronGeometryData dodeca)) {
            writeEmptyOutputs();
            return;
        }

        double a = dodeca.getEdgeLength();
        double surface = 3.0d * Math.sqrt(25.0d + 10.0d * Math.sqrt(5.0d)) * a * a;
        double volume = ((15.0d + 7.0d * Math.sqrt(5.0d)) / 4.0d) * a * a * a;
        RegionData region = GeometryVoxelizer.createBoundingRegion(dodeca);
        BoundingBoxData boundingBox = createBoundingBox(region);

        outputValues.put(OUTPUT_CENTER_ID, dodeca.getCenter());
        outputValues.put(OUTPUT_EDGE_LENGTH_ID, a);
        outputValues.put(OUTPUT_CIRCUMRADIUS_ID, dodeca.getCircumradius());
        outputValues.put(OUTPUT_VERTICES_ID, dodeca.getVertices());
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surface);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_EDGE_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_CIRCUMRADIUS_ID, 0.0d);
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
