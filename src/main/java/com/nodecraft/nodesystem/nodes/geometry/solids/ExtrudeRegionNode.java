package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PlanarRegionValidator;
import com.nodecraft.nodesystem.util.ProfileExtrusionUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.extrude_region",
    displayName = "Extrude Region",
    description = "Extrudes a planar region (outer + holes) into solid geometry via prism difference",
    category = "geometry.solids",
    order = 22
)
public class ExtrudeRegionNode extends AbstractSolidNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_DIRECTION_ID = "input_direction";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_BASE_REGION_ID = "output_base_region";
    private static final String OUTPUT_HEIGHT_ID = "output_height";

    public ExtrudeRegionNode() {
        super(UUID.randomUUID(), "geometry.solids.extrude_region");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region",
            "Planar region to extrude (outer + optional holes)", NodeDataType.PLANAR_REGION, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction",
            "Extrusion direction vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry",
            "Extruded solid (prism, or difference when holes exist)", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BASE_REGION_ID, "Base Region",
            "Input planar region", NodeDataType.PLANAR_REGION, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height",
            "Extrusion vector length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when region and direction produced geometry", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Extrudes a planar region (outer + holes) into solid geometry via prism difference";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Vector3d direction = resolveDirection(INPUT_DIRECTION_ID);

        if (!(regionObj instanceof PlanarRegionData region)) {
            invalidate("Valid planar region is required");
            return;
        }
        String regionError = PlanarRegionValidator.validate(region);
        if (regionError != null) {
            invalidate(regionError);
            return;
        }
        if (direction == null || direction.lengthSquared() <= SolidNodeUtils.EPSILON * SolidNodeUtils.EPSILON) {
            invalidate("Direction is missing, invalid, or zero-length");
            return;
        }

        StringBuilder error = new StringBuilder();
        ProfileExtrusionUtils.ExtrusionResult outerResult =
            ProfileExtrusionUtils.extrudeProfile(region.outer(), direction, error);
        if (outerResult == null) {
            invalidate(error.isEmpty() ? "Failed to extrude outer profile" : error.toString());
            return;
        }

        GeometryData geometry;
        if (!region.hasHoles()) {
            geometry = outerResult.prism();
        } else {
            List<GeometryData> holePrisms = new ArrayList<>(region.holeCount());
            for (PolygonProfileData hole : region.holes()) {
                error.setLength(0);
                ProfileExtrusionUtils.ExtrusionResult holeResult =
                    ProfileExtrusionUtils.extrudeProfile(hole, direction, error);
                if (holeResult == null) {
                    invalidate(error.isEmpty() ? "Failed to extrude hole profile" : error.toString());
                    return;
                }
                holePrisms.add(holeResult.prism());
            }
            GeometryData cutter = holePrisms.size() == 1
                ? holePrisms.getFirst()
                : new CompositeGeometryData(holePrisms);
            geometry = new DifferenceGeometryData(outerResult.prism(), cutter);
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_BASE_REGION_ID, region);
        outputValues.put(OUTPUT_HEIGHT_ID, outerResult.height());
        markSuccess();
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_GEOMETRY_ID, OUTPUT_BASE_REGION_ID);
        putDoubleOutputs(0.0d, OUTPUT_HEIGHT_ID);
        markInvalid(error);
    }
}
