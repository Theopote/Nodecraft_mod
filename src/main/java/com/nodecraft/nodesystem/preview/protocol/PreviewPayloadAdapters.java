package com.nodecraft.nodesystem.preview.protocol;

import com.nodecraft.nodesystem.preview.GhostBlockPlacement;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase A compatibility: centralize conversions from legacy shapes to v1 payloads.
 */
public final class PreviewPayloadAdapters {

    private PreviewPayloadAdapters() {
    }

    public static PreviewBlocksPayload fromBlockPosList(BlockPosList list, String blockId) {
        if (list == null || list.isEmpty()) {
            return new PreviewBlocksPayload(List.of());
        }
        List<PreviewBlock> blocks = new ArrayList<>(list.size());
        for (BlockPos pos : list.getPositions()) {
            if (pos != null) {
                blocks.add(new PreviewBlock(pos.getX(), pos.getY(), pos.getZ(), blockId));
            }
        }
        return new PreviewBlocksPayload(blocks);
    }

    public static PreviewBlocksPayload fromGhostBlockPlacements(List<GhostBlockPlacement> placements) {
        if (placements == null || placements.isEmpty()) {
            return new PreviewBlocksPayload(List.of());
        }
        List<PreviewBlock> blocks = new ArrayList<>(placements.size());
        for (GhostBlockPlacement p : placements) {
            if (p == null) {
                continue;
            }
            var pos = p.position();
            blocks.add(new PreviewBlock(pos.x, pos.y, pos.z, p.blockId()));
        }
        return new PreviewBlocksPayload(blocks);
    }

    public static PreviewBlocksPayload fromCoordinates(List<Coordinate> coords, String blockId) {
        if (coords == null || coords.isEmpty()) {
            return new PreviewBlocksPayload(List.of());
        }
        List<PreviewBlock> blocks = new ArrayList<>(coords.size());
        for (Coordinate c : coords) {
            if (c != null) {
                blocks.add(new PreviewBlock(c.getX(), c.getY(), c.getZ(), blockId));
            }
        }
        return new PreviewBlocksPayload(blocks);
    }

    public static List<BlockPos> toBlockPosList(PreviewBlocksPayload payload) {
        List<BlockPos> out = new ArrayList<>(payload.getBlocks().size());
        for (PreviewBlock b : payload.getBlocks()) {
            out.add(BlockPos.ofFloored(b.x(), b.y(), b.z()));
        }
        return out;
    }

    /**
     * Same convention as {@code PointsElement} for {@link Coordinate}: cell center in world space.
     */
    public static PreviewPointsPayload previewPointsFromCoordinates(List<Coordinate> coords) {
        if (coords == null || coords.isEmpty()) {
            return new PreviewPointsPayload(List.of());
        }
        List<PreviewPoint> pts = new ArrayList<>(coords.size());
        for (Coordinate c : coords) {
            if (c != null) {
                pts.add(new PreviewPoint(c.getX() + 0.5d, c.getY() + 0.5d, c.getZ() + 0.5d));
            }
        }
        return new PreviewPointsPayload(pts);
    }

    public static PreviewVectorsPayload previewVectorsFromVecLists(List<Vec3d> directions, List<Vec3d> origins) {
        if (directions == null || origins == null) {
            return new PreviewVectorsPayload(List.of());
        }
        int n = Math.min(directions.size(), origins.size());
        List<PreviewVector> vectors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Vec3d d = directions.get(i);
            Vec3d s = origins.get(i);
            if (d != null && s != null) {
                vectors.add(new PreviewVector(s.x, s.y, s.z, d.x, d.y, d.z));
            }
        }
        return new PreviewVectorsPayload(vectors);
    }
}
