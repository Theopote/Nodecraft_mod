package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates an arched opening volume using a profile extrusion.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.arch_opening",
    displayName = "Arch Opening",
    description = "Generates a rectangular, round, or pointed arch opening volume",
    category = "geometry.architectural_primitives",
    order = 7
)
public class ArchOpeningNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_DEPTH_ID = "input_depth";
    private static final String INPUT_ARCH_TYPE_ID = "input_arch_type";
    private static final String INPUT_SEGMENTS_ID = "input_segments";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ArchOpeningNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.arch_opening");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the opening reference surface", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Opening width across the face", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Rectangular stem height below the arch", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPTH_ID, "Depth", "Opening depth along the face normal", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ARCH_TYPE_ID, "Arch Type", "Arch type: rectangle, round, or pointed", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Curve segments used to approximate the arch", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the opening volume", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid arch opening could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a rectangular, round, or pointed arch opening volume";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        GeometryData geometry = null;
        boolean valid = false;

        if (faceObj instanceof BoxFaceData face) {
            ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
            if (frame != null) {
                double width = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_WIDTH_ID), 2.0d);
                double stemHeight = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_HEIGHT_ID), 2.0d);
                double depth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_DEPTH_ID), 1.0d);
                int segments = GenerationLimits.clampSegments(6, ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_SEGMENTS_ID), 12));
                String archType = resolveArchType(inputValues.get(INPUT_ARCH_TYPE_ID));

                geometry = buildArchVolume(frame, width, stemHeight, depth, segments, archType);
                valid = geometry != null;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private GeometryData buildArchVolume(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        double width,
        double stemHeight,
        double depth,
        int segments,
        String archType
    ) {
        double halfWidth = width / 2.0d;
        double archRadius = Math.max(halfWidth, 0.5d);
        Vector3d center = new Vector3d(frame.center());
        Vector3d inward = new Vector3d(frame.zAxis());

        List<GeometryData> geometry = new ArrayList<>();

        if ("rectangle".equals(archType)) {
            Vector3d boxCenter = new Vector3d(center).fma((stemHeight / 2.0d) - depth / 2.0d, inward);
            Vector3d halfExtents = new Vector3d(halfWidth, stemHeight / 2.0d, depth / 2.0d);
            geometry.add(ArchitecturalPrimitiveSupport.createOrientedBox(boxCenter, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis()));
            return new CompositeGeometryData(geometry);
        }

        List<Vector3d> closedPoints = switch (archType) {
            case "pointed" -> buildPointedProfile(center, frame, width, stemHeight, archRadius, segments);
            default -> buildRoundProfile(center, frame, width, stemHeight, archRadius, segments);
        };

        if (closedPoints.size() < 4) {
            return null;
        }

        PolygonProfileData profile = new PolygonProfileData(closedPoints, new PlaneData(center, frame.zAxis()));
        geometry.add(new PrismGeometryData(profile.getUniquePoints(), new Vector3d(frame.zAxis()).mul(depth)));
        return new CompositeGeometryData(geometry);
    }

    private List<Vector3d> buildRoundProfile(
        Vector3d center,
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        double width,
        double stemHeight,
        double radius,
        int segments
    ) {
        double halfWidth = width / 2.0d;
        double archBaseY = -frame.height() / 2.0d + stemHeight;
        double archCenterY = archBaseY + radius;
        List<Vector3d> points = new ArrayList<>();

        points.add(localPoint(center, frame, -halfWidth, -frame.height() / 2.0d));
        points.add(localPoint(center, frame, -halfWidth, archBaseY));
        for (int index = 0; index <= segments; index++) {
            double t = Math.PI - (Math.PI * index / segments);
            double x = radius * Math.cos(t);
            double y = archCenterY + radius * Math.sin(t);
            points.add(localPoint(center, frame, x, y));
        }
        points.add(localPoint(center, frame, halfWidth, archBaseY));
        points.add(localPoint(center, frame, halfWidth, -frame.height() / 2.0d));
        points.add(localPoint(center, frame, -halfWidth, -frame.height() / 2.0d));
        return points;
    }

    private List<Vector3d> buildPointedProfile(
        Vector3d center,
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        double width,
        double stemHeight,
        double radius,
        int segments
    ) {
        double halfWidth = width / 2.0d;
        double archBaseY = -frame.height() / 2.0d + stemHeight;
        double apexY = archBaseY + radius;
        List<Vector3d> points = new ArrayList<>();

        points.add(localPoint(center, frame, -halfWidth, -frame.height() / 2.0d));
        points.add(localPoint(center, frame, -halfWidth, archBaseY));
        for (int index = 0; index <= segments; index++) {
            double t = (double) index / segments;
            double x = -halfWidth + (halfWidth * 2.0d) * t;
            double archLift = apexY - Math.abs((t * 2.0d) - 1.0d) * radius;
            points.add(localPoint(center, frame, x, archLift));
        }
        points.add(localPoint(center, frame, halfWidth, archBaseY));
        points.add(localPoint(center, frame, halfWidth, -frame.height() / 2.0d));
        points.add(localPoint(center, frame, -halfWidth, -frame.height() / 2.0d));
        return points;
    }

    private Vector3d localPoint(Vector3d center, ArchitecturalPrimitiveSupport.FaceFrame frame, double x, double y) {
        return new Vector3d(center).fma(x, frame.xAxis()).fma(y, frame.yAxis());
    }

    private String resolveArchType(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim().toLowerCase(Locale.ROOT);
        }
        return "round";
    }
}