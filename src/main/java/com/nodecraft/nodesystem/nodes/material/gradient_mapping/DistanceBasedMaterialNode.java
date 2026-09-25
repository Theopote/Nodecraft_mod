package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.gradient_mapping.distance_material",
    displayName = "Distance-Based Material",
    description = "Assigns block types from a palette based on distance to exactly one reference point, plane, curve, polyline, or line.",
    category = "material.gradient_mapping",
    order = 3
)
public class DistanceBasedMaterialNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_PALETTE_ID = "input_palette";
    private static final String INPUT_FALLBACK_BLOCK_ID = "input_fallback_block";
    private static final String INPUT_MIN_DISTANCE_ID = "input_min_distance";
    private static final String INPUT_MAX_DISTANCE_ID = "input_max_distance";
    private static final String INPUT_REFERENCE_POINT_ID = "input_reference_point";
    private static final String INPUT_REFERENCE_PLANE_ID = "input_reference_plane";
    private static final String INPUT_REFERENCE_CURVE_ID = "input_reference_curve";
    private static final String INPUT_REFERENCE_POLYLINE_ID = "input_reference_polyline";
    private static final String INPUT_REFERENCE_LINE_ID = "input_reference_line";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public DistanceBasedMaterialNode() {
        super(UUID.randomUUID(), "material.gradient_mapping.distance_material");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to remap", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Palette", "Typed block palette (BLOCK_PALETTE)", NodeDataType.BLOCK_PALETTE, this));
        addInputPort(new BasePort(INPUT_FALLBACK_BLOCK_ID, "Fallback Block", "Geometry voxelization base when palette is empty", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_MIN_DISTANCE_ID, "Min Distance", "Distance mapped to first palette entry", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_DISTANCE_ID, "Max Distance", "Distance mapped to last palette entry", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_REFERENCE_POINT_ID, "Reference Point", "Point reference for radial distance", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_REFERENCE_PLANE_ID, "Reference Plane", "Plane reference", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_REFERENCE_CURVE_ID, "Reference Curve", "Curve reference", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_REFERENCE_POLYLINE_ID, "Reference Polyline", "Polyline reference", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_REFERENCE_LINE_ID, "Reference Line", "Line reference", NodeDataType.LINE, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Distance-mapped placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Distance per resolved position", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when exactly one reference and domain are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Assigns block types from a palette based on distance to exactly one reference point, plane, curve, polyline, or line.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double minDistance = readDouble(INPUT_MIN_DISTANCE_ID, 0.0d);
        double maxDistance = readDouble(INPUT_MAX_DISTANCE_ID, 16.0d);
        GradientMaterialUtils.Validation domain = GradientMaterialUtils.requirePositiveWidth(minDistance, maxDistance);
        if (!domain.valid()) {
            emitFail(domain.message());
            return;
        }

        ReferenceResolution reference = resolveExactlyOneReference();
        if (!reference.valid()) {
            emitFail(reference.error());
            return;
        }

        BlockPaletteData palette = GradientMaterialUtils.resolvePalette(inputValues.get(INPUT_PALETTE_ID));
        String fallbackMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_FALLBACK_BLOCK_ID));

        List<BlockPlacementData> fromPlacements = MaterialMappingSupport.extractPlacements(inputValues.get(INPUT_PLACEMENTS_ID));
        boolean placementSource = !fromPlacements.isEmpty();

        List<BlockPlacementData> sources = placementSource
            ? fromPlacements
            : MaterialMappingSupport.resolveSourcePlacements(
                null,
                inputValues.get(INPUT_COORDINATES_ID),
                inputValues.get(INPUT_GEOMETRY_ID),
                inputValues.get(INPUT_BOX_GEOMETRY_ID),
                inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
                inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
                inputValues.get(INPUT_TORUS_GEOMETRY_ID),
                MaterialMappingSupport.firstMappedBlockType(
                    fallbackMapped,
                    palette.isEmpty() ? null : palette.entries().getFirst().blockId()
                )
            );

        if (!placementSource
            && sources.isEmpty()
            && GradientMaterialUtils.hasNonPlacementSource(
                inputValues.get(INPUT_COORDINATES_ID),
                inputValues.get(INPUT_GEOMETRY_ID),
                inputValues.get(INPUT_BOX_GEOMETRY_ID),
                inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
                inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
                inputValues.get(INPUT_TORUS_GEOMETRY_ID)
            )
            && palette.isEmpty()
            && fallbackMapped == null) {
            emitFail("Palette or fallback block required for geometry or coordinates input");
            return;
        }

        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        List<Double> distances = new ArrayList<>(sources.size());
        double span = maxDistance - minDistance;

        for (BlockPlacementData source : sources) {
            BlockPos pos = source.pos();
            if (pos == null) {
                continue;
            }
            Vector3d sample = new Vector3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);
            double distance = reference.distance().applyAsDouble(sample);
            if (!Double.isFinite(distance)) {
                emitFail("Distance sample produced a non-finite value");
                return;
            }
            double normalized = GradientMaterialUtils.clamp01((distance - minDistance) / span);
            String blockId = GradientMaterialUtils.pickByNormalized(palette, normalized, source.blockId());
            placements.add(MaterialMappingSupport.remapBlockId(source, blockId));
            distances.add(distance);
        }

        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private ReferenceResolution resolveExactlyOneReference() {
        Object pointObj = inputValues.get(INPUT_REFERENCE_POINT_ID);
        Object planeObj = inputValues.get(INPUT_REFERENCE_PLANE_ID);
        Object curveObj = inputValues.get(INPUT_REFERENCE_CURVE_ID);
        Object polylineObj = inputValues.get(INPUT_REFERENCE_POLYLINE_ID);
        Object lineObj = inputValues.get(INPUT_REFERENCE_LINE_ID);

        int count = 0;
        ToDoubleFunction<Vector3d> chosen = null;
        String error = null;

        if (pointObj != null) {
            count++;
            if (pointObj instanceof PointData point) {
                Vector3d position = point.getPosition();
                chosen = sample -> sample.distance(position);
            } else {
                error = "Reference Point must be a POINT value";
            }
        }
        if (planeObj != null) {
            count++;
            if (planeObj instanceof PlaneData plane) {
                chosen = sample -> Math.abs(plane.signedDistanceTo(sample));
            } else {
                error = "Reference Plane must be a PLANE value";
            }
        }
        if (curveObj != null) {
            count++;
            if (curveObj instanceof Curve curve) {
                List<Vec3d> points = curve.getSamplePoints();
                if (points == null || points.size() < 2) {
                    error = "Reference Curve must have at least 2 sample points";
                } else {
                    chosen = sample -> distanceToPolyline(sample, points);
                }
            } else {
                error = "Reference Curve must be a CURVE value";
            }
        }
        if (polylineObj != null) {
            count++;
            if (polylineObj instanceof PolylineData polyline) {
                List<Vec3d> points = polyline.getPoints();
                if (points == null || points.size() < 2) {
                    error = "Reference Polyline must have at least 2 points";
                } else {
                    chosen = sample -> distanceToPolyline(sample, points);
                }
            } else {
                error = "Reference Polyline must be a POLYLINE value";
            }
        }
        if (lineObj != null) {
            count++;
            if (lineObj instanceof LineData line) {
                Vector3d start = toVector(line.getStart());
                Vector3d end = toVector(line.getEnd());
                chosen = sample -> distanceToSegment(sample, start, end);
            } else {
                error = "Reference Line must be a LINE value";
            }
        }

        if (count != 1) {
            return ReferenceResolution.fail("Exactly one reference (Point, Plane, Curve, Polyline, or Line) is required");
        }
        if (error != null) {
            return ReferenceResolution.fail(error);
        }
        return ReferenceResolution.ok(chosen);
    }

    private static double distanceToPolyline(Vector3d sample, List<Vec3d> points) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size() - 1; i++) {
            double d = distanceToSegment(sample, toVector(points.get(i)), toVector(points.get(i + 1)));
            best = Math.min(best, d);
        }
        return best;
    }

    private static double distanceToSegment(Vector3d p, Vector3d a, Vector3d b) {
        Vector3d ab = new Vector3d(b).sub(a);
        double lenSq = ab.lengthSquared();
        if (lenSq <= 0.0d) {
            return p.distance(a);
        }
        double t = new Vector3d(p).sub(a).dot(ab) / lenSq;
        double clamped = Math.max(0.0d, Math.min(1.0d, t));
        Vector3d closest = new Vector3d(a).lerp(b, clamped);
        return p.distance(closest);
    }

    private static Vector3d toVector(Vec3d value) {
        return new Vector3d(value.x, value.y, value.z);
    }

    private void emitFail(String message) {
        outputValues.putAll(GradientMaterialUtils.failResult(message, OUTPUT_DISTANCES_ID));
    }

    private double readDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private record ReferenceResolution(boolean valid, String error, @Nullable ToDoubleFunction<Vector3d> distance) {
        static ReferenceResolution ok(ToDoubleFunction<Vector3d> distance) {
            return new ReferenceResolution(true, "", distance);
        }

        static ReferenceResolution fail(String error) {
            return new ReferenceResolution(false, error, null);
        }
    }
}
