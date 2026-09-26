package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Locale;
import java.util.UUID;

/**
 * Single architectural column from a placement frame or base point + height.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.column",
    displayName = "Column",
    description = "Generates a single column from a frame or base point",
    category = "geometry.architectural_primitives",
    order = 22
)
public class ColumnNode extends BaseNode {

    private static final String INPUT_FRAME_ID = "input_frame";
    private static final String INPUT_BASE_ID = "input_base";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_SHAPE_ID = "input_shape";
    private static final String INPUT_TOP_SCALE_ID = "input_top_scale";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_BASE_ID = "output_base";
    private static final String OUTPUT_TOP_ID = "output_top";
    private static final String OUTPUT_FRAME_ID = "output_frame";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ColumnNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.column");

        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame",
            "Optional placement frame (origin = base; Y = up). Overrides Base when connected",
            NodeDataType.FRAME, this));
        addInputPort(new BasePort(INPUT_BASE_ID, "Base",
            "Column base point when Frame is unconnected", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Column height along up", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Base column radius or half-width", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SHAPE_ID, "Shape", "Column shape: cylinder, box, or frustum", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_TOP_SCALE_ID, "Top Scale", "Top radius scale for frustum columns", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Column solid", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BASE_ID, "Base", "Resolved column base point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_TOP_ID, "Top", "Column top point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Placement frame at the column base", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid column could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a single column from a frame or base point";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GeometryData geometry = null;
        PointData baseOut = null;
        PointData topOut = null;
        FrameData frameOut = null;
        boolean valid = false;

        ResolvedBasis basis = resolveBasis();
        if (basis != null) {
            double height = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_HEIGHT_ID), 3.0d);
            double radius = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_RADIUS_ID), 0.5d);
            double topScale = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_TOP_SCALE_ID), 1.0d);
            String shape = resolveShape(inputValues.get(INPUT_SHAPE_ID));

            Vector3d base = basis.base();
            Vector3d top = new Vector3d(base).fma(height, basis.up());
            geometry = createColumnGeometry(base, top, radius, topScale, shape, basis);
            baseOut = new PointData(base);
            topOut = new PointData(top);
            frameOut = new FrameData(base, basis.x(), basis.up(), basis.z());
            valid = true;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_BASE_ID, baseOut);
        outputValues.put(OUTPUT_TOP_ID, topOut);
        outputValues.put(OUTPUT_FRAME_ID, frameOut);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private @Nullable ResolvedBasis resolveBasis() {
        Object frameObj = inputValues.get(INPUT_FRAME_ID);
        if (frameObj instanceof FrameData frame) {
            FrameData orthonormal = frame.orthonormalized();
            if (orthonormal == null) {
                return null;
            }
            return new ResolvedBasis(
                orthonormal.getOrigin(),
                orthonormal.getXAxis(),
                orthonormal.getYAxis(),
                orthonormal.getZAxis()
            );
        }

        Vector3d base = resolveBasePoint(inputValues.get(INPUT_BASE_ID));
        if (base == null) {
            return null;
        }
        Vector3d up = new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d x = new Vector3d(1.0d, 0.0d, 0.0d);
        if (Math.abs(up.dot(x)) > 0.9d) {
            x.set(0.0d, 0.0d, 1.0d);
        }
        Vector3d z = new Vector3d(x).cross(up).normalize();
        x = new Vector3d(up).cross(z).normalize();
        return new ResolvedBasis(base, x, up, z);
    }

    private static @Nullable Vector3d resolveBasePoint(Object value) {
        if (value instanceof PointData point) {
            Vector3d position = point.position();
            if (Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)) {
                return new Vector3d(position);
            }
            return null;
        }
        if (value instanceof Vector3d vector
            && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z)) {
            return new Vector3d(vector);
        }
        return null;
    }

    private GeometryData createColumnGeometry(
        Vector3d base,
        Vector3d top,
        double radius,
        double topScale,
        String shape,
        ResolvedBasis basis
    ) {
        String normalized = shape.toLowerCase(Locale.ROOT);
        if ("box".equals(normalized)) {
            Vector3d center = new Vector3d(base).add(top).mul(0.5d);
            double height = base.distance(top);
            Vector3d halfExtents = new Vector3d(radius, height / 2.0d, radius);
            return ArchitecturalPrimitiveSupport.createOrientedBox(
                center, halfExtents, basis.x(), basis.up(), basis.z());
        }
        if ("frustum".equals(normalized)) {
            return new FrustumConeGeometryData(base, top, radius, radius * topScale);
        }
        return new CylinderGeometryData(base, top, radius);
    }

    private String resolveShape(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim();
        }
        return "cylinder";
    }

    private record ResolvedBasis(Vector3d base, Vector3d x, Vector3d up, Vector3d z) {
    }
}
