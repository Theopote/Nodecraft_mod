package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractBoxGeneratorNode extends AbstractPrimitiveNode {

    protected static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    protected static final String OUTPUT_BOX_GEOMETRY_ID = "output_box_geometry";
    protected static final String OUTPUT_CORNERS_ID = "output_corners";
    protected static final String OUTPUT_FACES_ID = "output_faces";

    private String lastFailureReason = "Box could not be constructed";

    protected AbstractBoxGeneratorNode(String typeId) {
        super(typeId);
        addCommonOutputs();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        lastFailureReason = "Box could not be constructed";
        BoxDefinition definition = resolveBoxDefinition();
        if (definition == null) {
            putNullOutputs(OUTPUT_GEOMETRY_ID, OUTPUT_BOX_GEOMETRY_ID);
            putEmptyListOutputs(OUTPUT_CORNERS_ID, OUTPUT_FACES_ID);
            markInvalid(lastFailureReason);
            return;
        }

        BoxGeometryData geometry = definition.toGeometryData();
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_BOX_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_CORNERS_ID, toPointList(geometry.getCorners()));
        outputValues.put(OUTPUT_FACES_ID, geometry.getFaces());
        markSuccess();
    }

    protected void failBox(String reason) {
        lastFailureReason = reason;
    }

    private static List<PointData> toPointList(List<Vector3d> vectors) {
        List<PointData> points = new ArrayList<>(vectors.size());
        for (Vector3d vector : vectors) {
            points.add(new PointData(vector));
        }
        return List.copyOf(points);
    }

    protected abstract BoxDefinition resolveBoxDefinition();

    protected void addCommonOutputs() {
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Canonical continuous box geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BOX_GEOMETRY_ID, "Box Geometry", "Resolved box geometry for analysis and editing", NodeDataType.BOX_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CORNERS_ID, "Corners",
            "Ordered list of the 8 box corner points. Use Get Box Corner to access one by index 0-7.",
            NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FACES_ID, "Faces",
            "Ordered list of the 6 box faces. Use Get Box Face to access one by index 0-5.",
            NodeDataType.BOX_FACE_LIST, this));
        addValidAndErrorOutputs();
    }

    protected BoxDefinition createContinuousCenterDefinition(
        Vector3d center,
        double sizeX,
        double sizeY,
        double sizeZ,
        @Nullable PlaneData plane,
        double rotationX,
        double rotationY,
        double rotationZ
    ) {
        if (!Double.isFinite(sizeX) || !Double.isFinite(sizeY) || !Double.isFinite(sizeZ)
                || sizeX <= 0.0d || sizeY <= 0.0d || sizeZ <= 0.0d) {
            failBox("Box sizes must be finite and > 0");
            return null;
        }

        Vector3d centerVector = new Vector3d(center);
        Vector3d halfExtents = new Vector3d(sizeX * 0.5d, sizeY * 0.5d, sizeZ * 0.5d);
        Matrix3d orientationMatrix = createOrientationMatrix(plane, rotationX, rotationY, rotationZ);
        boolean rotated = hasRotation(rotationX, rotationY, rotationZ) || plane != null;
        return new BoxDefinition(centerVector, halfExtents, orientationMatrix, rotated);
    }

    protected BoxDefinition createContinuousAxisAlignedDefinition(Vector3d cornerA, Vector3d cornerB) {
        double minX = Math.min(cornerA.x, cornerB.x);
        double minY = Math.min(cornerA.y, cornerB.y);
        double minZ = Math.min(cornerA.z, cornerB.z);
        double maxX = Math.max(cornerA.x, cornerB.x);
        double maxY = Math.max(cornerA.y, cornerB.y);
        double maxZ = Math.max(cornerA.z, cornerB.z);
        Vector3d minCorner = new Vector3d(minX, minY, minZ);
        Vector3d maxCorner = new Vector3d(maxX, maxY, maxZ);
        Vector3d extent = VectorUtils.safeSubtract(maxCorner, minCorner);
        if (extent == null) {
            failBox("Box corners must be distinct with positive extent on every axis");
            return null;
        }
        double extentX = extent.x;
        double extentY = extent.y;
        double extentZ = extent.z;
        if (!Double.isFinite(extentX) || !Double.isFinite(extentY) || !Double.isFinite(extentZ)
            || extentX <= 0.0d || extentY <= 0.0d || extentZ <= 0.0d) {
            failBox("Box corners must be distinct with positive extent on every axis");
            return null;
        }

        Vector3d center = new Vector3d(
            minX * 0.5d + maxX * 0.5d,
            minY * 0.5d + maxY * 0.5d,
            minZ * 0.5d + maxZ * 0.5d
        );
        Vector3d halfExtents = new Vector3d(extentX * 0.5d, extentY * 0.5d, extentZ * 0.5d);
        if (!VectorUtils.isFinite(center) || !VectorUtils.isFinite(halfExtents)) {
            failBox("Box corners produced a non-finite center or size");
            return null;
        }
        return new BoxDefinition(center, halfExtents, new Matrix3d().identity(), false);
    }

    protected BoxDefinition createContinuousCornerAndSizeDefinition(
        Vector3d corner,
        double sizeX,
        double sizeY,
        double sizeZ,
        @Nullable PlaneData plane,
        double rotationX,
        double rotationY,
        double rotationZ
    ) {
        if (!Double.isFinite(sizeX) || !Double.isFinite(sizeY) || !Double.isFinite(sizeZ)
                || sizeX == 0.0d || sizeY == 0.0d || sizeZ == 0.0d) {
            failBox("Box sizes must be finite and non-zero");
            return null;
        }

        Matrix3d orientationMatrix = createOrientationMatrix(plane, rotationX, rotationY, rotationZ);
        Vector3d endOffset = new Vector3d(sizeX, sizeY, sizeZ);
        orientationMatrix.transform(endOffset);
        if (!VectorUtils.isFinite(endOffset)) {
            failBox("Box corner and size produced a non-finite extent");
            return null;
        }
        Vector3d startCorner = new Vector3d(corner);
        Vector3d endCorner = VectorUtils.safeAdd(startCorner, endOffset);
        Vector3d center = PrimitiveGeometryValidator.overflowSafeMidpoint(startCorner, endCorner);
        Vector3d halfExtents = new Vector3d(
            Math.abs(sizeX) / 2.0d,
            Math.abs(sizeY) / 2.0d,
            Math.abs(sizeZ) / 2.0d
        );
        if (endCorner == null || center == null || !VectorUtils.isFinite(halfExtents)) {
            failBox("Box corner and size produced a non-finite center or size");
            return null;
        }
        boolean rotated = hasRotation(rotationX, rotationY, rotationZ) || plane != null;
        return new BoxDefinition(center, halfExtents, orientationMatrix, rotated);
    }

    protected boolean hasRotation(double rotationX, double rotationY, double rotationZ) {
        return Math.abs(rotationX) > 1e-9d
            || Math.abs(rotationY) > 1e-9d
            || Math.abs(rotationZ) > 1e-9d;
    }

    protected Matrix3d createRotationMatrix(double rotationX, double rotationY, double rotationZ) {
        return new Matrix3d()
            .rotateXYZ(
                Math.toRadians(rotationX),
                Math.toRadians(rotationY),
                Math.toRadians(rotationZ)
            );
    }

    protected Matrix3d createOrientationMatrix(@Nullable PlaneData plane, double rotationX, double rotationY, double rotationZ) {
        Matrix3d orientationMatrix = plane != null
            ? createPlaneAlignmentMatrix(plane)
            : new Matrix3d().identity();

        orientationMatrix.mul(createRotationMatrix(rotationX, rotationY, rotationZ));
        return orientationMatrix;
    }

    protected Matrix3d createPlaneAlignmentMatrix(PlaneData planeData) {
        Vector3d up = VectorUtils.safeNormalize(planeData.getNormal());
        if (up == null) {
            return new Matrix3d().identity();
        }

        Vector3d reference = Math.abs(up.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);

        Vector3d xAxis = VectorUtils.safeNormalize(VectorUtils.safeCross(reference, up));
        if (xAxis == null) {
            xAxis = new Vector3d(0.0d, 0.0d, 1.0d);
        }

        Vector3d zAxis = VectorUtils.safeNormalize(VectorUtils.safeCross(xAxis, up));
        if (zAxis == null) {
            return new Matrix3d().identity();
        }

        return new Matrix3d(
            xAxis.x, up.x, zAxis.x,
            xAxis.y, up.y, zAxis.y,
            xAxis.z, up.z, zAxis.z
        );
    }

    protected record BoxDefinition(
        Vector3d center,
        Vector3d halfExtents,
        Matrix3d orientationMatrix,
        boolean rotated
    ) {
        private BoxGeometryData toGeometryData() {
            if (center == null || halfExtents == null || orientationMatrix == null) {
                return null;
            }
            return new BoxGeometryData(center, halfExtents, orientationMatrix, rotated);
        }
    }
}
