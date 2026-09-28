package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_capsule",
    displayName = "Deconstruct Capsule",
    description = "Extracts axis, radius, component geometry, bounds, and analytical values from capsule geometry",
    category = "geometry.primitives",
    order = 30
)
public class DeconstructCapsuleNode extends AbstractPrimitiveDeconstructNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_START_ID = "output_start";
    private static final String OUTPUT_END_ID = "output_end";
    private static final String OUTPUT_AXIS_PATH_ID = "output_axis_path";
    private static final String OUTPUT_AXIS_LENGTH_ID = "output_axis_length";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_TOTAL_LENGTH_ID = "output_total_length";
    private static final String OUTPUT_CYLINDER_ID = "output_cylinder";
    private static final String OUTPUT_START_HEMISPHERE_ID = "output_start_hemisphere";
    private static final String OUTPUT_END_HEMISPHERE_ID = "output_end_hemisphere";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";

    public DeconstructCapsuleNode() {
        super("geometry.primitives.deconstruct_capsule");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Capsule geometry (cylinder + two hemispheres)", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_START_ID, "Start", "Capsule axis start point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_END_ID, "End", "Capsule axis end point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_PATH_ID, "Axis Path", "Capsule axis path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_LENGTH_ID, "Axis Length", "Distance between start and end points", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Capsule radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_LENGTH_ID, "Total Length", "Axis length plus two cap diameters", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CYLINDER_ID, "Cylinder", "Capsule middle cylinder", NodeDataType.CYLINDER_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_START_HEMISPHERE_ID, "Start Hemisphere", "Start cap hemisphere", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_END_HEMISPHERE_ID, "End Hemisphere", "End cap hemisphere", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Total capsule surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Total capsule volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", REGION_PORT_DESCRIPTION, NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", BOUNDING_BOX_PORT_DESCRIPTION, NodeDataType.BOUNDING_BOX, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts axis, radius, component geometry, bounds, and analytical values from capsule geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeEmptyOutputs("Valid capsule geometry is required");
            return;
        }

        CapsuleParts parts = resolveCapsuleParts(geometry);
        if (parts == null) {
            writeEmptyOutputs("Geometry must be a capsule (cylinder + two hemispheres)");
            return;
        }

        CylinderGeometryData cylinder = parts.cylinder();
        HemisphereGeometryData startCap = parts.startHemisphere();
        HemisphereGeometryData endCap = parts.endHemisphere();

        String cylinderError = PrimitiveGeometryValidator.validateCylinder(cylinder);
        if (cylinderError != null) {
            writeEmptyOutputs(cylinderError);
            return;
        }

        Vector3d start = cylinder.getStart();
        Vector3d end = cylinder.getEnd();
        double radius = cylinder.getRadius();
        String capsuleError = PrimitiveGeometryValidator.validateCapsule(start, end, radius);
        if (capsuleError != null) {
            writeEmptyOutputs(capsuleError);
            return;
        }

        double axisLength = new Vector3d(end).sub(start).length();
        double totalLength = axisLength + 2.0d * radius;
        double cylinderLateral = 2.0d * Math.PI * radius * axisLength;
        double hemisphereCurved = 2.0d * Math.PI * radius * radius;
        double surfaceArea = cylinderLateral + 2.0d * hemisphereCurved;
        double cylinderVolume = Math.PI * radius * radius * axisLength;
        double hemisphereVolume = (2.0d / 3.0d) * Math.PI * radius * radius * radius;
        double volume = cylinderVolume + 2.0d * hemisphereVolume;

        BoundsAndRegion boundsAndRegion = resolveContinuousBoundsAndRegion(geometry);
        if (boundsAndRegion == null) {
            writeEmptyOutputs("Unable to resolve continuous bounds");
            return;
        }

        outputValues.put(OUTPUT_START_ID, new PointData(start));
        outputValues.put(OUTPUT_END_ID, new PointData(end));
        outputValues.put(OUTPUT_AXIS_PATH_ID, pathFromLine(start, end));
        outputValues.put(OUTPUT_AXIS_LENGTH_ID, axisLength);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_TOTAL_LENGTH_ID, totalLength);
        outputValues.put(OUTPUT_CYLINDER_ID, cylinder);
        outputValues.put(OUTPUT_START_HEMISPHERE_ID, startCap);
        outputValues.put(OUTPUT_END_HEMISPHERE_ID, endCap);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, boundsAndRegion.region());
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundsAndRegion.boundingBox());
        markSuccess();
    }

    private @Nullable CapsuleParts resolveCapsuleParts(GeometryData geometry) {
        if (!(geometry instanceof CompositeGeometryData(List<GeometryData> leaves))) {
            return null;
        }

        if (leaves.size() != 3) {
            return null;
        }

        CylinderGeometryData cylinder = null;
        HemisphereGeometryData startCap = null;
        HemisphereGeometryData endCap = null;
        for (GeometryData leaf : leaves) {
            if (leaf instanceof CylinderGeometryData cylinderLeaf) {
                cylinder = cylinderLeaf;
            } else if (leaf instanceof HemisphereGeometryData hemisphere) {
                if (startCap == null) {
                    startCap = hemisphere;
                } else {
                    endCap = hemisphere;
                }
            } else {
                return null;
            }
        }

        if (cylinder == null || startCap == null || endCap == null) {
            return null;
        }

        Vector3d start = cylinder.getStart();
        Vector3d end = cylinder.getEnd();
        double radius = cylinder.getRadius();
        if (Math.abs(startCap.radius() - radius) > 1e-6d || Math.abs(endCap.radius() - radius) > 1e-6d) {
            return null;
        }
        if (start.distance(startCap.center()) > 1e-6d || end.distance(endCap.center()) > 1e-6d) {
            return null;
        }

        return new CapsuleParts(cylinder, startCap, endCap);
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(
            OUTPUT_START_ID,
            OUTPUT_END_ID,
            OUTPUT_AXIS_PATH_ID,
            OUTPUT_CYLINDER_ID,
            OUTPUT_START_HEMISPHERE_ID,
            OUTPUT_END_HEMISPHERE_ID,
            OUTPUT_REGION_ID,
            OUTPUT_BOUNDING_BOX_ID
        );
        putDoubleOutputs(0.0d,
            OUTPUT_AXIS_LENGTH_ID,
            OUTPUT_RADIUS_ID,
            OUTPUT_TOTAL_LENGTH_ID,
            OUTPUT_SURFACE_AREA_ID,
            OUTPUT_VOLUME_ID
        );
        markInvalid(reason);
    }

    private record CapsuleParts(
        CylinderGeometryData cylinder,
        HemisphereGeometryData startHemisphere,
        HemisphereGeometryData endHemisphere
    ) {
    }
}
