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
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Set;
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
    order = 17
)
public class ColumnNode extends BaseNode {

    private static final Set<String> SHAPES = Set.of("cylinder", "box", "frustum");

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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ColumnNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.column");

        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame",
            "Placement frame (origin = base; Y = up). Exactly one of Frame or Base must be connected",
            NodeDataType.FRAME, this));
        addInputPort(new BasePort(INPUT_BASE_ID, "Base",
            "Column base PointData when Frame is unconnected. Exactly one of Frame or Base must be connected",
            NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Column height along up", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Base column radius or half-width", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SHAPE_ID, "Shape", "Column shape: cylinder, box, or frustum", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_TOP_SCALE_ID, "Top Scale", "Top radius scale for frustum columns", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Column solid", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BASE_ID, "Base", "Resolved column base point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_TOP_ID, "Top", "Column top point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Placement frame at the column base", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid column could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a single column from a frame or base point";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean frameConnected = ArchitecturalInputUtils.isConnected(this, INPUT_FRAME_ID);
        boolean baseConnected = ArchitecturalInputUtils.isConnected(this, INPUT_BASE_ID);
        if (frameConnected == baseConnected) {
            writeInvalid(frameConnected
                ? "Connect exactly one of Frame or Base Point (not both)"
                : "Connect exactly one of Frame or Base Point");
            return;
        }

        ResolvedBasis basis = frameConnected ? resolveFrameBasis() : resolveBaseBasis();
        if (basis == null) {
            return;
        }

        Double height = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_HEIGHT_ID, 3.0d);
        if (height == null) {
            writeInvalid("Height must be a positive finite number");
            return;
        }
        Double radius = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_RADIUS_ID, 0.5d);
        if (radius == null) {
            writeInvalid("Radius must be a positive finite number");
            return;
        }
        Double topScale = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_TOP_SCALE_ID, 1.0d);
        if (topScale == null) {
            writeInvalid("Top Scale must be a positive finite number");
            return;
        }
        String shape = ArchitecturalInputUtils.resolveKnownStringEnum(
            this, INPUT_SHAPE_ID, "cylinder", SHAPES);
        if (shape == null) {
            writeInvalid("Shape must be one of: cylinder, box, frustum");
            return;
        }

        Vector3d base = basis.base();
        Vector3d top = new Vector3d(base).fma(height, basis.up());
        GeometryData column = createColumnGeometry(base, top, radius, topScale, shape, basis);
        GeometryData geometry = column == null ? null : GeometryOutputUtils.packGeometry(List.of(column));
        if (geometry == null) {
            writeInvalid("Could not generate column geometry");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_BASE_ID, new PointData(base));
        outputValues.put(OUTPUT_TOP_ID, new PointData(top));
        outputValues.put(OUTPUT_FRAME_ID, new FrameData(base, basis.x(), basis.up(), basis.z()));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private @Nullable ResolvedBasis resolveFrameBasis() {
        Object frameObj = getInput(INPUT_FRAME_ID);
        if (!(frameObj instanceof FrameData frame)) {
            writeInvalid("Frame must be a valid FRAME when connected");
            return null;
        }
        FrameData orthonormal = frame.orthonormalized();
        if (orthonormal == null) {
            writeInvalid("Frame must be a non-degenerate FRAME");
            return null;
        }
        return new ResolvedBasis(
            orthonormal.getOrigin(),
            orthonormal.getXAxis(),
            orthonormal.getYAxis(),
            orthonormal.getZAxis()
        );
    }

    private @Nullable ResolvedBasis resolveBaseBasis() {
        Vector3d base = ArchitecturalInputUtils.resolveRequiredPointData(this, INPUT_BASE_ID);
        if (base == null) {
            writeInvalid("Base must be PointData (bare Vector3d is not accepted)");
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

    private @Nullable GeometryData createColumnGeometry(
        Vector3d base,
        Vector3d top,
        double radius,
        double topScale,
        String shape,
        ResolvedBasis basis
    ) {
        if ("box".equals(shape)) {
            Vector3d center = com.nodecraft.nodesystem.util.VectorUtils.safeLerp(base, top, 0.5d);
            double height = com.nodecraft.nodesystem.util.VectorUtils.safeLength(
                com.nodecraft.nodesystem.util.VectorUtils.safeSubtract(top, base));
            if (center == null || !Double.isFinite(height)) {
                return null;
            }
            Vector3d halfExtents = new Vector3d(radius, height / 2.0d, radius);
            return ArchitecturalPrimitiveSupport.createOrientedBox(
                center, halfExtents, basis.x(), basis.up(), basis.z());
        }
        if ("frustum".equals(shape)) {
            return new FrustumConeGeometryData(base, top, radius, radius * topScale);
        }
        return new CylinderGeometryData(base, top, radius);
    }

    private void writeInvalid(String error) {
        ArchitecturalNodeOutputs.putNull(outputValues, OUTPUT_GEOMETRY_ID, OUTPUT_BASE_ID, OUTPUT_TOP_ID, OUTPUT_FRAME_ID);
        ArchitecturalNodeOutputs.markInvalid(outputValues, error);
    }

    private record ResolvedBasis(Vector3d base, Vector3d x, Vector3d up, Vector3d z) {
    }
}
