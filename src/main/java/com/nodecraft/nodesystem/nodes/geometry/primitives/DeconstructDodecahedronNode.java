package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import com.nodecraft.nodesystem.util.PrimitiveNumericUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_dodecahedron",
    displayName = "Deconstruct Dodecahedron",
    description = "Extracts center, edge length, vertices, bounds, and analytical values from dodecahedron geometry",
    category = "geometry.primitives",
    order = 28
)
public class DeconstructDodecahedronNode extends AbstractPrimitiveDeconstructNode {

    private static final String INPUT_DODECAHEDRON_ID = "input_dodecahedron";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_EDGE_LENGTH_ID = "output_edge_length";
    private static final String OUTPUT_CIRCUMRADIUS_ID = "output_circumradius";
    private static final String OUTPUT_VERTICES_ID = "output_vertices";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_ORIENTATION_ID = "output_orientation";

    public DeconstructDodecahedronNode() {
        super("geometry.primitives.deconstruct_dodecahedron");

        addInputPort(new BasePort(INPUT_DODECAHEDRON_ID, "Dodecahedron", "Dodecahedron geometry to deconstruct", NodeDataType.DODECAHEDRON_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Dodecahedron center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_LENGTH_ID, "Edge Length", "Edge length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CIRCUMRADIUS_ID, "Circumradius", "Circumscribed sphere radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Vertices", "World-space vertices", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Total surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Interior volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", REGION_PORT_DESCRIPTION, NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", BOUNDING_BOX_PORT_DESCRIPTION, NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_ORIENTATION_ID, "Orientation", "Rotation matrix (local vertex frame -> world)", NodeDataType.MATRIX3, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts center, edge length, vertices, bounds, and analytical values from dodecahedron geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object obj = inputValues.get(INPUT_DODECAHEDRON_ID);
        if (!(obj instanceof DodecahedronGeometryData dodeca)) {
            writeEmptyOutputs("Valid dodecahedron geometry is required");
            return;
        }

        String error = PrimitiveGeometryValidator.validatePolyhedron(
            dodeca.getCenter(), dodeca.getEdgeLength(), "edge length");
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        double edgeLength = dodeca.getEdgeLength();
        double circumradius = dodeca.getCircumradius();
        double surface = PrimitiveNumericUtils.safeMul(
            3.0d * Math.sqrt(25.0d + 10.0d * Math.sqrt(5.0d)),
            PrimitiveNumericUtils.safeSquare(edgeLength));
        double volume = PrimitiveNumericUtils.safeMul(
            (15.0d + 7.0d * Math.sqrt(5.0d)) / 4.0d,
            PrimitiveNumericUtils.safeCube(edgeLength));
        java.util.List<org.joml.Vector3d> vertices = dodeca.getVertices();
        if (!requireFiniteOutputs(edgeLength, circumradius, surface, volume)
            || !PrimitiveNumericUtils.isFiniteMatrix(dodeca.getOrientationMatrix())
            || vertices == null) {
            writeEmptyOutputs("Derived analytical values are non-finite");
            return;
        }
        for (org.joml.Vector3d vertex : vertices) {
            if (!com.nodecraft.nodesystem.util.VectorUtils.isFinite(vertex)) {
                writeEmptyOutputs("Derived analytical values are non-finite");
                return;
            }
        }
        BoundsAndRegion boundsAndRegion = resolveContinuousBoundsAndRegion(dodeca);
        if (boundsAndRegion == null) {
            writeEmptyOutputs("Unable to resolve continuous bounds");
            return;
        }

        outputValues.put(OUTPUT_CENTER_ID, new PointData(dodeca.getCenter()));
        outputValues.put(OUTPUT_EDGE_LENGTH_ID, edgeLength);
        outputValues.put(OUTPUT_CIRCUMRADIUS_ID, circumradius);
        outputValues.put(OUTPUT_VERTICES_ID, SpatialValueResolver.toPointDataList(vertices));
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surface);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, boundsAndRegion.region());
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundsAndRegion.boundingBox());
        outputValues.put(OUTPUT_ORIENTATION_ID, dodeca.getOrientationMatrix());
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_CENTER_ID, OUTPUT_REGION_ID, OUTPUT_BOUNDING_BOX_ID, OUTPUT_ORIENTATION_ID);
        putEmptyListOutputs(OUTPUT_VERTICES_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_EDGE_LENGTH_ID, OUTPUT_CIRCUMRADIUS_ID, OUTPUT_SURFACE_AREA_ID, OUTPUT_VOLUME_ID);
        markInvalid(reason);
    }
}
