package com.nodecraft.nodesystem.nodes.spatial.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Emits high-level metadata for any supported geometry object.
 */
@NodeInfo(
    id = "spatial.analysis.geometry_info",
    displayName = "Geometry Info",
    description = "Reports type and bounds information for any supported geometry",
    category = "spatial.analysis"
)
public class GeometryInfoNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_TYPE_ID = "output_type";
    private static final String OUTPUT_HAS_GEOMETRY_ID = "output_has_geometry";
    private static final String OUTPUT_IS_BOX_ID = "output_is_box";
    private static final String OUTPUT_IS_CONE_ID = "output_is_cone";
    private static final String OUTPUT_IS_CYLINDER_ID = "output_is_cylinder";
    private static final String OUTPUT_IS_ELLIPSOID_ID = "output_is_ellipsoid";
    private static final String OUTPUT_IS_OCTAHEDRON_ID = "output_is_octahedron";
    private static final String OUTPUT_IS_PRISM_ID = "output_is_prism";
    private static final String OUTPUT_IS_SPHERE_ID = "output_is_sphere";
    private static final String OUTPUT_IS_TETRAHEDRON_ID = "output_is_tetrahedron";
    private static final String OUTPUT_IS_TORUS_ID = "output_is_torus";
    private static final String OUTPUT_IS_COMPOSITE_ID = "output_is_composite";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_CHILD_COUNT_ID = "output_child_count";

    public GeometryInfoNode() {
        super(UUID.randomUUID(), "spatial.analysis.geometry_info");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_TYPE_ID, "Type", "Resolved geometry type", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_HAS_GEOMETRY_ID, "Has Geometry", "Whether geometry input is present", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_BOX_ID, "Is Box", "Whether the geometry is a box", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_CONE_ID, "Is Cone", "Whether the geometry is a cone", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_CYLINDER_ID, "Is Cylinder", "Whether the geometry is a cylinder", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_ELLIPSOID_ID, "Is Ellipsoid", "Whether the geometry is an ellipsoid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_OCTAHEDRON_ID, "Is Octahedron", "Whether the geometry is an octahedron", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_PRISM_ID, "Is Prism", "Whether the geometry is a prism", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_SPHERE_ID, "Is Sphere", "Whether the geometry is a sphere", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_TETRAHEDRON_ID, "Is Tetrahedron", "Whether the geometry is a tetrahedron", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_TORUS_ID, "Is Torus", "Whether the geometry is a torus", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_COMPOSITE_ID, "Is Composite", "Whether the geometry is a composite", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Axis-aligned bounding box data", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Bounding region center block", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_CHILD_COUNT_ID, "Child Count", "Number of child geometries when composite", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Reports type and bounds information for any supported geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        boolean hasGeometry = geometryObj instanceof GeometryData;

        String geometryType = "none";
        RegionData region = null;
        BoundingBoxData boundingBox = null;
        BlockPos center = null;

        boolean isBox = geometryObj instanceof BoxGeometryData;
        boolean isCone = geometryObj instanceof ConeGeometryData;
        boolean isCylinder = geometryObj instanceof CylinderGeometryData;
        boolean isEllipsoid = geometryObj instanceof EllipsoidGeometryData;
        boolean isOctahedron = geometryObj instanceof OctahedronGeometryData;
        boolean isPrism = geometryObj instanceof PrismGeometryData;
        boolean isSphere = geometryObj instanceof SphereData;
        boolean isTetrahedron = geometryObj instanceof TetrahedronGeometryData;
        boolean isTorus = geometryObj instanceof TorusGeometryData;
        boolean isComposite = geometryObj instanceof CompositeGeometryData;
        int childCount = geometryObj instanceof CompositeGeometryData composite ? composite.size() : (hasGeometry ? 1 : 0);

        if (isBox) geometryType = "box";
        else if (isCone) geometryType = "cone";
        else if (isCylinder) geometryType = "cylinder";
        else if (isEllipsoid) geometryType = "ellipsoid";
        else if (isOctahedron) geometryType = "octahedron";
        else if (isPrism) geometryType = "prism";
        else if (isSphere) geometryType = "sphere";
        else if (isTetrahedron) geometryType = "tetrahedron";
        else if (isTorus) geometryType = "torus";
        else if (isComposite) geometryType = "composite";

        if (geometryObj instanceof GeometryData geometry) {
            region = GeometryVoxelizer.createBoundingRegion(geometry);
            if (region != null && region.isComplete()) {
                BlockPos minCorner = region.getMinCorner();
                BlockPos maxCorner = region.getMaxCorner();
                if (minCorner != null && maxCorner != null) {
                    boundingBox = new BoundingBoxData(
                        new Vector3d(minCorner.getX(), minCorner.getY(), minCorner.getZ()),
                        new Vector3d(maxCorner.getX() + 1.0d, maxCorner.getY() + 1.0d, maxCorner.getZ() + 1.0d)
                    );
                    center = new BlockPos(
                        minCorner.getX() + ((maxCorner.getX() - minCorner.getX()) / 2),
                        minCorner.getY() + ((maxCorner.getY() - minCorner.getY()) / 2),
                        minCorner.getZ() + ((maxCorner.getZ() - minCorner.getZ()) / 2)
                    );
                }
            }
        }

        outputValues.put(OUTPUT_TYPE_ID, geometryType);
        outputValues.put(OUTPUT_HAS_GEOMETRY_ID, hasGeometry);
        outputValues.put(OUTPUT_IS_BOX_ID, isBox);
        outputValues.put(OUTPUT_IS_CONE_ID, isCone);
        outputValues.put(OUTPUT_IS_CYLINDER_ID, isCylinder);
        outputValues.put(OUTPUT_IS_ELLIPSOID_ID, isEllipsoid);
        outputValues.put(OUTPUT_IS_OCTAHEDRON_ID, isOctahedron);
        outputValues.put(OUTPUT_IS_PRISM_ID, isPrism);
        outputValues.put(OUTPUT_IS_SPHERE_ID, isSphere);
        outputValues.put(OUTPUT_IS_TETRAHEDRON_ID, isTetrahedron);
        outputValues.put(OUTPUT_IS_TORUS_ID, isTorus);
        outputValues.put(OUTPUT_IS_COMPOSITE_ID, isComposite);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_CHILD_COUNT_ID, childCount);
    }
}
