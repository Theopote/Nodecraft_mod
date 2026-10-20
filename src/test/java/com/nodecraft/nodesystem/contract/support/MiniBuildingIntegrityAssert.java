package com.nodecraft.nodesystem.contract.support;

import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.util.GeometryStructureUtils;
import com.nodecraft.nodesystem.util.PrimitiveVolumeSampler;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spatial integrity checks for architectural.residential.mini_building_v1 placement sets.
 * Operates on world-space solid cells after Move-to-Player.
 */
public final class MiniBuildingIntegrityAssert {

    private MiniBuildingIntegrityAssert() {
    }

    public static Bounds boundsOf(Set<BlockPos> solid) {
        assertTrue(solid != null && !solid.isEmpty(), "solid set must be non-empty");
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : solid) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Floor slab: bottom Y band must cover the building footprint with few gaps
     * (openings are on walls, not the floor plane) and form a single 4-connected component.
     */
    public static void assertFloorContinuous(Set<BlockPos> solid, Bounds bounds) {
        Set<BlockPos> floor = filterY(solid, bounds.minY());
        assertTrue(floor.size() >= 20, "floor band too sparse: " + floor.size());
        Bounds floorBounds = boundsOf(floor);
        int footprint = (floorBounds.maxX() - floorBounds.minX() + 1)
                * (floorBounds.maxZ() - floorBounds.minZ() + 1);
        // Floor need not fill interior courtyard fully, but must cover a large share of its own bbox.
        assertTrue(floor.size() * 4 >= footprint,
                "floor coverage too low: cells=" + floor.size() + " footprint=" + footprint);
        assertStrictlyConnectedOrthogonally(floor, "floor");
    }

    /**
     * Roof band (top Y and optionally one below for thickness/overhang) must span most of the
     * XZ footprint and sit above the wall height.
     */
    public static void assertRoofCoverage(Set<BlockPos> solid, Bounds bounds) {
        assertTrue(bounds.maxY() - bounds.minY() >= 3, "building height too short for roof+walls");
        Set<BlockPos> roof = new HashSet<>();
        for (BlockPos pos : solid) {
            if (pos.getY() >= bounds.maxY() - 1) {
                roof.add(pos);
            }
        }
        assertTrue(roof.size() >= 16, "roof band too sparse: " + roof.size());
        int roofMinX = Integer.MAX_VALUE;
        int roofMaxX = Integer.MIN_VALUE;
        int roofMinZ = Integer.MAX_VALUE;
        int roofMaxZ = Integer.MIN_VALUE;
        for (BlockPos pos : roof) {
            roofMinX = Math.min(roofMinX, pos.getX());
            roofMaxX = Math.max(roofMaxX, pos.getX());
            roofMinZ = Math.min(roofMinZ, pos.getZ());
            roofMaxZ = Math.max(roofMaxZ, pos.getZ());
        }
        assertTrue(roofMaxX - roofMinX + 1 >= (bounds.maxX() - bounds.minX()) * 0.7,
                "roof X span insufficient");
        assertTrue(roofMaxZ - roofMinZ + 1 >= (bounds.maxZ() - bounds.minZ()) * 0.7,
                "roof Z span insufficient");
    }

    /**
     * Four wall faces must form a hollow perimeter at mid height; exterior corners connect.
     * Face extents come from the mid-wall Y band so roof overhangs that inflate the full building
     * AABB do not falsely fail wall checks. Each edge must carry multiple cells (not a tautology
     * of “min/max of the same set”).
     */
    public static void assertWallsAndCorners(Set<BlockPos> solid, Bounds bounds) {
        // Prefer the first storey above the floor so gable/roof fill does not land in the band.
        assertTrue(bounds.maxY() - bounds.minY() >= 2, "need floor + wall height");
        int midY = bounds.minY() + 1;
        Set<BlockPos> mid = filterY(solid, midY);
        if (mid.isEmpty() && midY + 1 < bounds.maxY()) {
            midY = midY + 1;
            mid = filterY(solid, midY);
        }
        assertFalse(mid.isEmpty(), "mid-wall band empty");

        Bounds wallBounds = boundsOf(mid);
        assertTrue(wallBounds.maxX() - wallBounds.minX() >= 2, "wall X span too narrow");
        assertTrue(wallBounds.maxZ() - wallBounds.minZ() >= 2, "wall Z span too narrow");

        int edgeMinX = 0;
        int edgeMaxX = 0;
        int edgeMinZ = 0;
        int edgeMaxZ = 0;
        int interior = 0;
        for (BlockPos pos : mid) {
            boolean onMinX = pos.getX() == wallBounds.minX();
            boolean onMaxX = pos.getX() == wallBounds.maxX();
            boolean onMinZ = pos.getZ() == wallBounds.minZ();
            boolean onMaxZ = pos.getZ() == wallBounds.maxZ();
            if (onMinX) {
                edgeMinX++;
            }
            if (onMaxX) {
                edgeMaxX++;
            }
            if (onMinZ) {
                edgeMinZ++;
            }
            if (onMaxZ) {
                edgeMaxZ++;
            }
            if (!onMinX && !onMaxX && !onMinZ && !onMaxZ) {
                interior++;
            }
        }
        assertTrue(edgeMinX >= 2 && edgeMaxX >= 2 && edgeMinZ >= 2 && edgeMaxZ >= 2,
                "wall edges too sparse at mid band: minX=" + edgeMinX + " maxX=" + edgeMaxX
                        + " minZ=" + edgeMinZ + " maxZ=" + edgeMaxZ);
        // Shell (possibly thick): solid cells must not fill the entire mid-band footprint.
        int footprint = (wallBounds.maxX() - wallBounds.minX() + 1)
                * (wallBounds.maxZ() - wallBounds.minZ() + 1);
        assertTrue(mid.size() < footprint,
                "mid-band fully solid (expected wall shell with cavity): solids=" + mid.size()
                        + " footprint=" + footprint + " interiorSolids=" + interior);

        Set<BlockPos> corners = Set.of(
                new BlockPos(wallBounds.minX(), midY, wallBounds.minZ()),
                new BlockPos(wallBounds.minX(), midY, wallBounds.maxZ()),
                new BlockPos(wallBounds.maxX(), midY, wallBounds.minZ()),
                new BlockPos(wallBounds.maxX(), midY, wallBounds.maxZ())
        );
        int cornerHits = 0;
        for (BlockPos corner : corners) {
            if (solid.contains(corner)
                    || solid.contains(corner.up())
                    || solid.contains(corner.down())) {
                cornerHits++;
            }
        }
        assertTrue(cornerHits >= 3, "expected ≥3 wall corners connected, got " + cornerHits);
    }

    /**
     * Opening centers empty; pier between windows solid; end walls still present on the host face.
     */
    public static void assertOpeningsAndPiers(
            Set<BlockPos> solid,
            List<PointData> centers,
            Vector3d worldOffset,
            Vector3d faceNormal
    ) {
        assertTrue(centers != null && centers.size() >= 2, "need ≥2 window centers");
        List<BlockPos> openingCells = new java.util.ArrayList<>();
        for (PointData center : centers) {
            Vector3d p = center.position().add(worldOffset, new Vector3d());
            BlockPos cell = BlockPos.ofFloored(p.x, p.y, p.z);
            openingCells.add(cell);
            assertFalse(solid.contains(cell), "opening center must be empty: " + cell);
        }

        Vector3d mid = centers.get(0).position().add(centers.get(1).position(), new Vector3d())
                .mul(0.5d)
                .add(worldOffset);
        BlockPos pier = BlockPos.ofFloored(mid.x, mid.y, mid.z);
        if (!solid.contains(pier)) {
            pier = BlockPos.ofFloored(
                    mid.x + faceNormal.x * 0.6d,
                    mid.y + faceNormal.y * 0.6d,
                    mid.z + faceNormal.z * 0.6d
            );
        }
        assertTrue(solid.contains(pier), "pier between windows missing at " + pier);

        // End walls: cells outward along the face tangent from each opening should remain solid.
        Vector3d across = new Vector3d(centers.get(1).position()).sub(centers.get(0).position());
        if (across.lengthSquared() > 1.0e-6d) {
            across.normalize();
            BlockPos leftEnd = BlockPos.ofFloored(
                    openingCells.get(0).getX() - across.x * 2.0d + faceNormal.x * 0.5d,
                    openingCells.get(0).getY(),
                    openingCells.get(0).getZ() - across.z * 2.0d + faceNormal.z * 0.5d
            );
            BlockPos rightEnd = BlockPos.ofFloored(
                    openingCells.get(1).getX() + across.x * 2.0d + faceNormal.x * 0.5d,
                    openingCells.get(1).getY(),
                    openingCells.get(1).getZ() + across.z * 2.0d + faceNormal.z * 0.5d
            );
            assertTrue(neighborsContain(solid, leftEnd), "left end wall missing near " + leftEnd);
            assertTrue(neighborsContain(solid, rightEnd), "right end wall missing near " + rightEnd);
        }
    }

    /**
     * Window frames must come from the Place-Geometry-On-Frames branch — not host wall solids.
     * <p>
     * Thin frame bars often vanish under 1-block voxelization, so this check is geometric:
     * leaf count, hollow opening centers, and rim samples that hit frame leaves while remaining
     * empty in the cut host wall (so a plain wall ring cannot pass).
     *
     * @param frameGeometry      {@code place_frames} output geometry (pre-move local space)
     * @param hostWallGeometry   cut wall geometry without frames (pre-move local space)
     * @param centers            window array opening centers (same local space)
     * @param faceNormal         host face outward normal
     * @param expectedFrameCount place_frames {@code output_count} (must match window count)
     */
    public static void assertFramesFromBranch(
            GeometryData frameGeometry,
            GeometryData hostWallGeometry,
            List<PointData> centers,
            Vector3d faceNormal,
            int expectedFrameCount
    ) {
        assertTrue(centers != null && !centers.isEmpty(), "need window centers");
        assertEquals(centers.size(), expectedFrameCount,
                "place_frames count must match Window Array openings");
        assertTrue(frameGeometry != null, "place_frames geometry missing");
        assertTrue(hostWallGeometry != null, "cut wall geometry missing");

        long frameLeaves = GeometryStructureUtils.countLeaves(frameGeometry);
        // WindowFrame packs 4 bars; Place-on-Frames copies once per opening.
        assertTrue(frameLeaves >= 4L * centers.size(),
                "frame leaf count too low for openings: leaves=" + frameLeaves
                        + " openings=" + centers.size());

        Vector3d across = centers.size() >= 2
                ? new Vector3d(centers.get(1).position()).sub(centers.get(0).position())
                : new Vector3d(1.0d, 0.0d, 0.0d);
        if (across.lengthSquared() < 1.0e-6d) {
            across.set(1.0d, 0.0d, 0.0d);
        } else {
            across.normalize();
        }
        Vector3d up = new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d normal = faceNormal == null ? new Vector3d(0.0d, 0.0d, 1.0d) : new Vector3d(faceNormal);
        if (normal.lengthSquared() > 1.0e-6d) {
            normal.normalize();
        }

        for (PointData center : centers) {
            Vector3d opening = new Vector3d(center.position());
            assertFalse(anyLeafContains(frameGeometry, opening),
                    "frame must not fill opening center: " + opening);
            assertFalse(anyLeafContains(hostWallGeometry, opening),
                    "cut host wall must leave opening center empty: " + opening);

            // Outer frame matches opening size (~1.5×1.0); sample near bar midlines, not hollow core.
            Vector3d[] rimSamples = {
                    new Vector3d(opening).fma(0.70d, across),
                    new Vector3d(opening).fma(-0.70d, across),
                    new Vector3d(opening).fma(0.45d, up),
                    new Vector3d(opening).fma(-0.45d, up),
                    new Vector3d(opening).fma(0.70d, across).fma(0.05d, normal),
                    new Vector3d(opening).fma(-0.70d, across).fma(0.05d, normal),
                    new Vector3d(opening).fma(0.45d, up).fma(0.05d, normal),
                    new Vector3d(opening).fma(-0.45d, up).fma(0.05d, normal)
            };
            int frameHits = 0;
            int hostHits = 0;
            for (Vector3d sample : rimSamples) {
                if (anyLeafContains(frameGeometry, sample)) {
                    frameHits++;
                }
                if (anyLeafContains(hostWallGeometry, sample)) {
                    hostHits++;
                }
            }
            assertTrue(frameHits >= 2,
                    "opening rim must hit ≥2 place_frames leaves near " + opening
                            + " (hits=" + frameHits + ")");
            assertTrue(frameHits > hostHits,
                    "frame rim hits must exceed host-wall hits (else wall alone could pass): "
                            + "frame=" + frameHits + " host=" + hostHits + " at " + opening);
        }
    }

    private static boolean anyLeafContains(GeometryData geometry, Vector3d point) {
        if (geometry == null || point == null) {
            return false;
        }
        if (geometry instanceof CompositeGeometryData composite) {
            for (GeometryData child : composite.geometries()) {
                if (anyLeafContains(child, point)) {
                    return true;
                }
            }
            return false;
        }
        return PrimitiveVolumeSampler.containsPoint(geometry, point);
    }

    /**
     * @deprecated Prefer {@link #assertFramesFromBranch} — neighbor-of-solid checks can pass on walls alone.
     */
    @Deprecated
    public static void assertFramesAroundOpenings(
            Set<BlockPos> solid,
            List<PointData> centers,
            Vector3d worldOffset,
            int expectedFrameCount
    ) {
        assertTrue(expectedFrameCount >= centers.size(),
                "frame count " + expectedFrameCount + " < openings " + centers.size());
        int ringHits = 0;
        for (PointData center : centers) {
            Vector3d p = center.position().add(worldOffset, new Vector3d());
            BlockPos cell = BlockPos.ofFloored(p.x, p.y, p.z);
            boolean ring = false;
            for (BlockPos n : new BlockPos[]{
                    cell.north(), cell.south(), cell.east(), cell.west(), cell.up(), cell.down()
            }) {
                if (solid.contains(n)) {
                    ring = true;
                    break;
                }
            }
            if (ring) {
                ringHits++;
            }
        }
        assertTrue(ringHits == centers.size(),
                "each opening should have solid frame/wall neighbors; hits=" + ringHits);
    }

    public static void assertDominantMaterial(
            java.util.Map<BlockPos, net.minecraft.block.BlockState> map,
            net.minecraft.block.Block expected,
            double minFraction
    ) {
        assertTrue(map != null && !map.isEmpty(), "material map empty");
        int match = 0;
        for (net.minecraft.block.BlockState state : map.values()) {
            if (state.isOf(expected)) {
                match++;
            }
        }
        double fraction = match / (double) map.size();
        assertTrue(fraction >= minFraction,
                "material fraction too low: " + fraction + " expected ≥ " + minFraction
                        + " for " + expected);
    }

    private static boolean neighborsContain(Set<BlockPos> solid, BlockPos pos) {
        return solid.contains(pos)
                || solid.contains(pos.north())
                || solid.contains(pos.south())
                || solid.contains(pos.east())
                || solid.contains(pos.west());
    }

    private static Set<BlockPos> filterY(Set<BlockPos> solid, int y) {
        Set<BlockPos> out = new HashSet<>();
        for (BlockPos pos : solid) {
            if (pos.getY() == y) {
                out.add(pos);
            }
        }
        return out;
    }

    /**
     * Strict 4-connected check: every cell must belong to the same orthogonal component.
     * Do not use a fractional threshold — two equal-sized islands would otherwise pass.
     */
    public static void assertStrictlyConnectedOrthogonally(Set<BlockPos> cells, String label) {
        if (cells == null || cells.size() <= 1) {
            return;
        }
        BlockPos start = cells.iterator().next();
        Set<BlockPos> visited = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.removeFirst();
            for (BlockPos n : new BlockPos[]{cur.north(), cur.south(), cur.east(), cur.west()}) {
                if (cells.contains(n) && visited.add(n)) {
                    queue.add(n);
                }
            }
        }
        assertEquals(cells.size(), visited.size(),
                label + " must be a single orthogonal component; visited=" + visited.size()
                        + "/" + cells.size() + " sample=" + summarize(cells));
    }

    /** Offset a local-space solid set by a world translation (Move-to-Player). */
    public static Set<BlockPos> offset(Set<BlockPos> local, Vector3d worldOffset) {
        Set<BlockPos> out = new HashSet<>(local.size());
        int dx = (int) Math.floor(worldOffset.x);
        int dy = (int) Math.floor(worldOffset.y);
        int dz = (int) Math.floor(worldOffset.z);
        // Prefer ofFloored on continuous offset when fractional.
        if (Math.abs(worldOffset.x - dx) > 1.0e-9d
                || Math.abs(worldOffset.y - dy) > 1.0e-9d
                || Math.abs(worldOffset.z - dz) > 1.0e-9d) {
            for (BlockPos pos : local) {
                out.add(BlockPos.ofFloored(
                        pos.getX() + worldOffset.x,
                        pos.getY() + worldOffset.y,
                        pos.getZ() + worldOffset.z));
            }
            return out;
        }
        for (BlockPos pos : local) {
            out.add(pos.add(dx, dy, dz));
        }
        return out;
    }

    private static String summarize(Set<BlockPos> positions) {
        TreeSet<BlockPos> sorted = new TreeSet<>(BlockPos::compareTo);
        sorted.addAll(positions);
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (BlockPos pos : sorted) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ());
            if (i >= 8) {
                sb.append(", …");
                break;
            }
        }
        return sb.append(']').toString();
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }
}
