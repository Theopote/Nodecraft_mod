package com.nodecraft.nodesystem.contract.support;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Voxel-level assertions for architectural opening semantics (holes, piers, sills, lintels).
 */
public final class ArchitecturalVoxelAssert {

    private ArchitecturalVoxelAssert() {
    }

    public static Set<BlockPos> toSolidSet(BlockPosList blocks) {
        Set<BlockPos> solid = new HashSet<>();
        for (BlockPos pos : blocks) {
            if (pos != null) {
                solid.add(pos);
            }
        }
        return solid;
    }

    public static void assertOpeningCentersEmpty(Set<BlockPos> solid, List<PointData> centers) {
        for (PointData center : centers) {
            BlockPos pos = blockAt(center.position());
            assertFalse(solid.contains(pos),
                "opening center should be empty at " + pos + " but found solid block");
        }
    }

    public static void assertPierBetweenWindowsHasBlock(
        Set<BlockPos> solid,
        PointData leftCenter,
        PointData rightCenter,
        Vector3d faceNormal
    ) {
        Vector3d mid = new Vector3d(leftCenter.position()).add(rightCenter.position()).mul(0.5d);
        Vector3d normal = new Vector3d(faceNormal);
        if (normal.lengthSquared() > 1.0e-12d) {
            normal.normalize();
            mid.fma(-0.25d, normal);
        }
        BlockPos pier = blockAt(mid);
        assertTrue(solid.contains(pier),
            "pier between windows should remain solid at " + pier);
    }

    public static void assertMarginRegionsSolid(
        Set<BlockPos> solid,
        PointData leftmostCenter,
        PointData rightmostCenter,
        Vector3d faceXAxis,
        double marginOffset
    ) {
        Vector3d xAxis = new Vector3d(faceXAxis).normalize();
        Vector3d leftMargin = new Vector3d(leftmostCenter.position()).fma(-marginOffset, xAxis);
        Vector3d rightMargin = new Vector3d(rightmostCenter.position()).fma(marginOffset, xAxis);
        assertTrue(solid.contains(blockAt(leftMargin)), "left margin should remain solid at " + blockAt(leftMargin));
        assertTrue(solid.contains(blockAt(rightMargin)), "right margin should remain solid at " + blockAt(rightMargin));
    }

    public static void assertMarginRegionsSolid(
        Set<BlockPos> solid,
        PointData leftmostCenter,
        PointData rightmostCenter,
        Vector3d faceXAxis,
        Vector3d faceNormal,
        double marginOffset
    ) {
        Vector3d xAxis = new Vector3d(faceXAxis).normalize();
        Vector3d inward = new Vector3d(faceNormal);
        if (inward.lengthSquared() > 1.0e-12d) {
            inward.normalize().mul(-0.25d);
        } else {
            inward.zero();
        }
        Vector3d leftMargin = new Vector3d(leftmostCenter.position()).fma(-marginOffset, xAxis).add(inward);
        Vector3d rightMargin = new Vector3d(rightmostCenter.position()).fma(marginOffset, xAxis).add(inward);
        assertTrue(solid.contains(blockAt(leftMargin)), "left margin should remain solid at " + blockAt(leftMargin));
        assertTrue(solid.contains(blockAt(rightMargin)), "right margin should remain solid at " + blockAt(rightMargin));
    }

    public static void assertSillAndLintelSolid(
        Set<BlockPos> solid,
        PointData center,
        Vector3d faceYAxis,
        Vector3d faceNormal,
        double halfOpeningHeight,
        double sampleOffset
    ) {
        Vector3d yAxis = new Vector3d(faceYAxis).normalize();
        Vector3d normal = new Vector3d(faceNormal);
        if (normal.lengthSquared() > 1.0e-12d) {
            normal.normalize();
        } else {
            normal.zero();
        }
        Vector3d sillBase = new Vector3d(center.position()).fma(-(halfOpeningHeight + sampleOffset), yAxis);
        Vector3d lintelBase = new Vector3d(center.position()).fma(halfOpeningHeight + sampleOffset, yAxis);
        assertTrue(hasSolidAlongInward(solid, sillBase, normal),
            "sill below opening should remain solid near " + blockAt(sillBase));
        assertTrue(hasSolidAlongInward(solid, lintelBase, normal),
            "lintel above opening should remain solid near " + blockAt(lintelBase));
    }

    private static boolean hasSolidAlongInward(Set<BlockPos> solid, Vector3d base, Vector3d outwardNormal) {
        for (double depth : new double[] {0.05d, 0.15d, 0.25d, 0.35d, 0.45d}) {
            Vector3d sample = new Vector3d(base).fma(-depth, outwardNormal);
            if (solid.contains(blockAt(sample))) {
                return true;
            }
        }
        return solid.contains(blockAt(base));
    }

    public static void assertOpeningCutsThroughThickness(
        Set<BlockPos> solid,
        Vector3d openingCenterOnFace,
        Vector3d outwardNormal,
        double halfDepth
    ) {
        Vector3d normal = new Vector3d(outwardNormal);
        if (normal.lengthSquared() > 1.0e-12d) {
            normal.normalize();
        } else {
            normal.zero();
        }
        double[] offsets = {halfDepth * 0.5d, 0.0d, -halfDepth * 0.5d};
        String[] labels = {"outward half", "face plane", "inward half"};
        for (int i = 0; i < offsets.length; i++) {
            Vector3d sample = new Vector3d(openingCenterOnFace).fma(offsets[i], normal);
            BlockPos pos = blockAt(sample);
            assertFalse(solid.contains(pos), labels[i] + " of opening should be air at " + pos);
        }
    }

    public static void assertFewerBlocksThan(Set<BlockPos> cutWall, Set<BlockPos> solidWall) {
        assertTrue(cutWall.size() < solidWall.size(),
            "cut wall should have fewer voxels than uncut wall: cut=" + cutWall.size()
                + " solid=" + solidWall.size());
    }

    public static BlockPos blockAt(Vector3d point) {
        return new BlockPos(
            (int) Math.floor(point.x),
            (int) Math.floor(point.y),
            (int) Math.floor(point.z)
        );
    }
}
