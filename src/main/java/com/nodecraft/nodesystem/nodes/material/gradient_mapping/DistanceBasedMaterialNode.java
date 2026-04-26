package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "material.gradient_mapping.distance_material",
    displayName = "Distance-Based Material",
    description = "Assigns block types from a palette based on distance to a reference point, plane, curve, or line.",
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

    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";

    public DistanceBasedMaterialNode() {
        super(UUID.randomUUID(), "material.gradient_mapping.distance_material");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to remap", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Palette", "Ordered block id list from near to far", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_FALLBACK_BLOCK_ID, "Fallback Block", "Fallback block when palette is empty", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_MIN_DISTANCE_ID, "Min Distance", "Distance mapped to first palette entry", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_DISTANCE_ID, "Max Distance", "Distance mapped to last palette entry", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_REFERENCE_POINT_ID, "Reference Point", "Point reference for radial distance", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_REFERENCE_PLANE_ID, "Reference Plane", "Plane reference", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_REFERENCE_CURVE_ID, "Reference Curve", "Curve reference", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_REFERENCE_POLYLINE_ID, "Reference Polyline", "Polyline reference", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_REFERENCE_LINE_ID, "Reference Line", "Line reference", NodeDataType.LINE, this));

        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block ids aligned with positions", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Distance-mapped placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Distance per resolved position", NodeDataType.LIST, this));
    }

    @Override
    public String getDescription() {
        return "Assigns block types from a palette based on distance to a reference point, plane, curve, or line.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String fallback = getInputString(INPUT_FALLBACK_BLOCK_ID, "minecraft:stone");
        List<String> palette = resolvePalette(fallback);
        List<BlockPlacementData> base = resolvePlacements(fallback);

        double minDistance = getInputDouble(INPUT_MIN_DISTANCE_ID, 0.0d);
        double maxDistance = getInputDouble(INPUT_MAX_DISTANCE_ID, 16.0d);
        double rangeMin = Math.min(minDistance, maxDistance);
        double rangeMax = Math.max(minDistance, maxDistance);
        double span = Math.max(1.0e-9d, rangeMax - rangeMin);

        Object planeObj = inputValues.get(INPUT_REFERENCE_PLANE_ID);
        Object curveObj = inputValues.get(INPUT_REFERENCE_CURVE_ID);
        Object polylineObj = inputValues.get(INPUT_REFERENCE_POLYLINE_ID);
        Object lineObj = inputValues.get(INPUT_REFERENCE_LINE_ID);
        Vector3d referencePoint = resolveVector(inputValues.get(INPUT_REFERENCE_POINT_ID));

        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(base.size());
        List<BlockPlacementData> placements = new ArrayList<>(base.size());
        List<Double> distances = new ArrayList<>(base.size());

        for (BlockPlacementData placement : base) {
            if (placement.pos() == null) {
                continue;
            }
            Vector3d sample = new Vector3d(
                placement.pos().getX() + 0.5d,
                placement.pos().getY() + 0.5d,
                placement.pos().getZ() + 0.5d
            );
            double distance = resolveDistance(sample, referencePoint, planeObj, curveObj, polylineObj, lineObj);
            double normalized = clamp01((distance - rangeMin) / span);
            int index = Math.min(palette.size() - 1, Math.max(0, (int) Math.floor(normalized * palette.size())));
            String selected = palette.get(index);
            String blockId = (selected == null || selected.isBlank()) ? fallback : selected;

            positions.add(placement.pos());
            blockIds.add(blockId);
            placements.add(new BlockPlacementData(placement.pos(), blockId, placement.stateData()));
            distances.add(distance);
        }

        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
    }

    private double resolveDistance(Vector3d sample, @Nullable Vector3d pointRef, Object planeObj, Object curveObj, Object polylineObj, Object lineObj) {
        if (planeObj instanceof PlaneData plane) {
            return Math.abs(plane.signedDistanceTo(sample));
        }
        if (curveObj instanceof Curve curve) {
            return distanceToCurve(sample, curve);
        }
        if (polylineObj instanceof PolylineData polyline) {
            return distanceToPolyline(sample, polyline.getPoints());
        }
        if (lineObj instanceof LineData line) {
            return distanceToSegment(sample, toVector(line.getStart()), toVector(line.getEnd()));
        }
        if (pointRef != null) {
            return sample.distance(pointRef);
        }
        return sample.length();
    }

    private double distanceToCurve(Vector3d sample, Curve curve) {
        List<Vec3d> points = curve.getSamplePoints();
        if (points.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        return distanceToPolyline(sample, points);
    }

    private double distanceToPolyline(Vector3d sample, List<Vec3d> points) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size() - 1; i++) {
            double d = distanceToSegment(sample, toVector(points.get(i)), toVector(points.get(i + 1)));
            best = Math.min(best, d);
        }
        return best;
    }

    private double distanceToSegment(Vector3d p, Vector3d a, Vector3d b) {
        Vector3d ab = new Vector3d(b).sub(a);
        double lenSq = ab.lengthSquared();
        if (lenSq <= 1.0e-12d) {
            return p.distance(a);
        }
        double t = new Vector3d(p).sub(a).dot(ab) / lenSq;
        double clamped = Math.max(0.0d, Math.min(1.0d, t));
        Vector3d closest = new Vector3d(a).lerp(b, clamped);
        return p.distance(closest);
    }

    private Vector3d toVector(Vec3d value) {
        return new Vector3d(value.x, value.y, value.z);
    }

    private List<BlockPlacementData> resolvePlacements(String fallbackBlockId) {
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (placementsObj instanceof List<?> placementList && !placementList.isEmpty()) {
            List<BlockPlacementData> resolved = new ArrayList<>();
            for (Object entry : placementList) {
                if (entry instanceof BlockPlacementData placement && placement.pos() != null) {
                    resolved.add(placement);
                }
            }
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }

        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            true
        );

        List<BlockPlacementData> generated = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            generated.add(new BlockPlacementData(pos, fallbackBlockId));
        }
        return generated;
    }

    private List<String> resolvePalette(String fallback) {
        Object paletteObj = inputValues.get(INPUT_PALETTE_ID);
        List<String> palette = new ArrayList<>();
        if (paletteObj instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String blockId && !blockId.isBlank()) {
                    palette.add(blockId);
                }
            }
        }
        if (palette.isEmpty()) {
            palette.add(fallback);
        }
        return palette;
    }

    private @Nullable Vector3d resolveVector(Object value) {
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        return null;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
