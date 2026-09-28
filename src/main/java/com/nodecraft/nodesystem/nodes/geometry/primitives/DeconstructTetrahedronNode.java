package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_tetrahedron",
    displayName = "Deconstruct Tetrahedron",
    description = "Extracts center, edge length, vertices, bounds, and analytical values from tetrahedron geometry",
    category = "geometry.primitives",
    order = 25
)
public class DeconstructTetrahedronNode extends AbstractPrimitiveDeconstructNode {

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

    public DeconstructTetrahedronNode() {
        super("geometry.primitives.deconstruct_tetrahedron");

        addInputPort(new BasePort(INPUT_TETRAHEDRON_ID, "Tetrahedron", "Tetrahedron geometry to deconstruct", NodeDataType.TETRAHEDRON_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Tetrahedron center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_ID, "Edge Length", "Resolved edge length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CIRCUMRADIUS_ID, "Circumradius", "Distance from center to vertices", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Vertices", "Resolved tetrahedron vertices", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Regular tetrahedron surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Regular tetrahedron volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", REGION_PORT_DESCRIPTION, NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", BOUNDING_BOX_PORT_DESCRIPTION, NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_ORIENTATION_ID, "Orientation", "Rotation matrix (local vertex frame -> world)", NodeDataType.MATRIX3, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts center, edge length, vertices, bounds, and analytical values from tetrahedron geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object tetrahedronObj = inputValues.get(INPUT_TETRAHEDRON_ID);
        if (!(tetrahedronObj instanceof TetrahedronGeometryData tetrahedron)) {
            writeEmptyOutputs("Valid tetrahedron geometry is required");
            return;
        }

        double edgeLength = tetrahedron.getEdgeLength();
        if (edgeLength <= 0.0d) {
            writeEmptyOutputs("Tetrahedron edge length must be > 0");
            return;
        }

        double surfaceArea = Math.sqrt(3.0d) * edgeLength * edgeLength;
        double volume = (edgeLength * edgeLength * edgeLength) / (6.0d * Math.sqrt(2.0d));
        BoundsAndRegion boundsAndRegion = resolveContinuousBoundsAndRegion(tetrahedron);
        if (boundsAndRegion == null) {
            writeEmptyOutputs("Unable to resolve continuous bounds");
            return;
        }

        outputValues.put(OUTPUT_CENTER_ID, new PointData(tetrahedron.getCenter()));
        outputValues.put(OUTPUT_EDGE_ID, edgeLength);
        outputValues.put(OUTPUT_CIRCUMRADIUS_ID, tetrahedron.getCircumradius());
        outputValues.put(OUTPUT_VERTICES_ID, SpatialValueResolver.toPointDataList(tetrahedron.getVertices()));
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, boundsAndRegion.region());
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundsAndRegion.boundingBox());
        outputValues.put(OUTPUT_ORIENTATION_ID, tetrahedron.getOrientationMatrix());
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_CENTER_ID, OUTPUT_REGION_ID, OUTPUT_BOUNDING_BOX_ID, OUTPUT_ORIENTATION_ID);
        putEmptyListOutputs(OUTPUT_VERTICES_ID);
        putDoubleOutputs(0.0d, OUTPUT_EDGE_ID, OUTPUT_CIRCUMRADIUS_ID, OUTPUT_SURFACE_AREA_ID, OUTPUT_VOLUME_ID);
        markInvalid(reason);
    }
}
