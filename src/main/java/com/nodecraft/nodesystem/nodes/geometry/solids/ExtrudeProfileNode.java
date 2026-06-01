package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Vector3;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.extrude",
    displayName = "Extrude Profile",
    description = "Extrudes a polygon profile by a direction vector and emits prism geometry plus side surface strip data",
    category = "geometry.solids",
    order = 0
)
public class ExtrudeProfileNode extends BaseNode {

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_DIRECTION_ID = "input_direction";

    private static final String OUTPUT_PRISM_ID = "output_prism";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_BASE_PROFILE_ID = "output_base_profile";
    private static final String OUTPUT_TOP_PROFILE_ID = "output_top_profile";
    private static final String OUTPUT_BASE_POINTS_ID = "output_base_points";
    private static final String OUTPUT_TOP_POINTS_ID = "output_top_points";
    private static final String OUTPUT_SIDE_SURFACE_ID = "output_side_surface";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ExtrudeProfileNode() {
        super(UUID.randomUUID(), "geometry.solids.extrude");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to extrude", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", "Extrusion direction vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PRISM_ID, "Prism", "Constructed prism geometry", NodeDataType.PRISM_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BASE_PROFILE_ID, "Base Profile", "Original polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_TOP_PROFILE_ID, "Top Profile", "Extruded polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Closed base polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TOP_POINTS_ID, "Top Points", "Closed extruded polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_SURFACE_ID, "Side Surface", "Side strip surface between the base and top profiles (surface shell representation)", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Extrusion vector length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid profile and direction were provided", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extrudes a polygon profile by a direction vector and emits prism geometry plus side surface strip data";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        Vector3d direction = resolveDirection(inputValues.get(INPUT_DIRECTION_ID));

        if (!(profileObj instanceof PolygonProfileData baseProfile) || direction == null) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> baseUniquePoints = baseProfile.getUniquePoints();
        if (baseUniquePoints.size() < 3) {
            writeEmptyOutputs();
            return;
        }

        double height = direction.length();
        if (height <= 1.0e-9d) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> baseClosedPoints = baseProfile.getClosedPoints();
        List<Vector3d> topClosedPoints = new ArrayList<>(baseClosedPoints.size());
        for (Vector3d point : baseClosedPoints) {
            topClosedPoints.add(new Vector3d(point).add(direction));
        }

        PlaneData basePlane = baseProfile.getPlane();
        PlaneData topPlane = new PlaneData(
            new Vector3d(basePlane.getPoint()).add(direction),
            basePlane.getNormal()
        );
        PolygonProfileData topProfile = new PolygonProfileData(topClosedPoints, topPlane);
        PrismGeometryData prism = new PrismGeometryData(baseUniquePoints, direction);

        SurfaceStripData sideSurface = new SurfaceStripData(
            List.of(baseProfile.getUniquePoints(), topProfile.getUniquePoints()),
            List.of(true, true)
        );

        outputValues.put(OUTPUT_PRISM_ID, prism);
        outputValues.put(OUTPUT_GEOMETRY_ID, prism);
        outputValues.put(OUTPUT_BASE_PROFILE_ID, baseProfile);
        outputValues.put(OUTPUT_TOP_PROFILE_ID, topProfile);
        outputValues.put(OUTPUT_BASE_POINTS_ID, List.copyOf(baseClosedPoints));
        outputValues.put(OUTPUT_TOP_POINTS_ID, List.copyOf(topClosedPoints));
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, sideSurface);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_PRISM_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_BASE_PROFILE_ID, null);
        outputValues.put(OUTPUT_TOP_PROFILE_ID, null);
        outputValues.put(OUTPUT_BASE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_TOP_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, null);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private @Nullable Vector3d resolveDirection(@Nullable Object value) {
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        if (value instanceof Vector3 vector) {
            return new Vector3d(vector.getX(), vector.getY(), vector.getZ());
        }
        return null;
    }
}
