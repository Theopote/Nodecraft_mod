package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection-aware source resolution for Basic Assignment nodes.
 * Unconnected → skip; driven+valid → use; driven+invalid → fail (no fallback).
 */
public final class MaterialSourceResolver {

    public enum SourceKind {
        NONE,
        PLACEMENTS_TREE,
        BLOCKS_TREE,
        PLACEMENTS,
        COORDINATES,
        GEOMETRY
    }

    public record SourcePorts(
            @Nullable String placementsTreeId,
            @Nullable String blocksTreeId,
            String placementsId,
            String coordinatesId,
            String geometryId,
            String boxGeometryId,
            String cylinderGeometryId,
            String sphereGeometryId,
            String torusGeometryId
    ) {
    }

    public record SourceResolution(
            boolean valid,
            SourceKind kind,
            @Nullable DataTreeData tree,
            List<BlockPlacementData> placements,
            String error
    ) {
        public static SourceResolution none() {
            return new SourceResolution(true, SourceKind.NONE, null, List.of(), "");
        }

        public static SourceResolution fail(String error) {
            return new SourceResolution(false, SourceKind.NONE, null, List.of(), error == null ? "" : error);
        }

        public static SourceResolution tree(SourceKind kind, DataTreeData tree) {
            return new SourceResolution(true, kind, tree, List.of(), "");
        }

        public static SourceResolution flat(SourceKind kind, List<BlockPlacementData> placements) {
            return new SourceResolution(true, kind, null, placements != null ? placements : List.of(), "");
        }
    }

    private MaterialSourceResolver() {
    }

    /**
     * Resolve the first driven source in precedence order.
     *
     * @param geometryFallbackBlockId block id stamped onto coords/geometry placements
     *                                (Assign uses required type; palette nodes may pass {@code ""})
     */
    public static SourceResolution resolve(
            BaseNode node,
            SourcePorts ports,
            @Nullable String geometryFallbackBlockId
    ) {
        if (ports.placementsTreeId() != null && isDriven(node, ports.placementsTreeId())) {
            return resolveTree(node.getInput(ports.placementsTreeId()), SourceKind.PLACEMENTS_TREE, "Placements Tree");
        }
        if (ports.blocksTreeId() != null && isDriven(node, ports.blocksTreeId())) {
            return resolveTree(node.getInput(ports.blocksTreeId()), SourceKind.BLOCKS_TREE, "Blocks Tree");
        }
        if (isDriven(node, ports.placementsId())) {
            return resolvePlacements(node.getInput(ports.placementsId()));
        }
        if (isDriven(node, ports.coordinatesId())) {
            return resolveCoordinates(node.getInput(ports.coordinatesId()), geometryFallbackBlockId);
        }

        String geometryPort = firstDrivenGeometryPort(node, ports);
        if (geometryPort != null) {
            return resolveGeometry(node.getInput(geometryPort), geometryFallbackBlockId);
        }
        return SourceResolution.none();
    }

    /**
     * Driven when the port is wired, or a non-null value was injected (unit tests / direct setInput).
     * Connected + null still counts as driven and fails closed in parsers.
     */
    static boolean isDriven(BaseNode node, String portId) {
        return OptionalPortDrive.isConnected(node, portId) || node.getInput(portId) != null;
    }

    private static @Nullable String firstDrivenGeometryPort(BaseNode node, SourcePorts ports) {
        if (isDriven(node, ports.geometryId())) {
            return ports.geometryId();
        }
        if (isDriven(node, ports.boxGeometryId())) {
            return ports.boxGeometryId();
        }
        if (isDriven(node, ports.cylinderGeometryId())) {
            return ports.cylinderGeometryId();
        }
        if (isDriven(node, ports.sphereGeometryId())) {
            return ports.sphereGeometryId();
        }
        if (isDriven(node, ports.torusGeometryId())) {
            return ports.torusGeometryId();
        }
        return null;
    }

    private static SourceResolution resolveTree(@Nullable Object value, SourceKind kind, String portName) {
        if (!(value instanceof DataTreeData tree)) {
            return SourceResolution.fail(portName + " must be a DATA_TREE");
        }
        BasicAssignmentUtils.Validation itemsOk = validateTreeItems(tree);
        if (!itemsOk.valid()) {
            return SourceResolution.fail(itemsOk.message());
        }
        return SourceResolution.tree(kind, tree);
    }

    static BasicAssignmentUtils.Validation validateTreeItems(DataTreeData tree) {
        for (DataTreeData.Branch branch : tree.getBranches()) {
            for (Object item : branch.items()) {
                if (item instanceof BlockPos) {
                    continue;
                }
                if (item instanceof BlockPlacementData placement && placement.pos() != null) {
                    continue;
                }
                return BasicAssignmentUtils.Validation.fail(
                    "Tree items must be BlockPos or BlockPlacementData with non-null pos"
                );
            }
        }
        return BasicAssignmentUtils.Validation.ok();
    }

    private static SourceResolution resolvePlacements(@Nullable Object value) {
        if (!(value instanceof List<?> list)) {
            return SourceResolution.fail("Block Placements must be a BLOCK_PLACEMENT_LIST");
        }
        List<BlockPlacementData> out = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof BlockPlacementData placement) || placement.pos() == null) {
                return SourceResolution.fail(
                    "Block Placements must contain only BlockPlacementData with non-null pos"
                );
            }
            out.add(placement);
        }
        return SourceResolution.flat(SourceKind.PLACEMENTS, out);
    }

    private static SourceResolution resolveCoordinates(
            @Nullable Object value,
            @Nullable String fallbackBlockId
    ) {
        if (!(value instanceof BlockPosList positions)) {
            return SourceResolution.fail("Coordinates must be a BLOCK_LIST");
        }
        String blockId = fallbackBlockId == null ? "" : fallbackBlockId;
        List<BlockPlacementData> out = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (pos == null) {
                return SourceResolution.fail("Coordinates must not contain null positions");
            }
            out.add(new BlockPlacementData(pos, blockId));
        }
        return SourceResolution.flat(SourceKind.COORDINATES, out);
    }

    private static SourceResolution resolveGeometry(
            @Nullable Object value,
            @Nullable String fallbackBlockId
    ) {
        if (!(value instanceof GeometryData geometry)) {
            return SourceResolution.fail("Geometry input must be geometry data");
        }
        BlockPosList positions = GeometryVoxelizer.voxelize(geometry, true);
        if (positions.isEmpty()) {
            return SourceResolution.fail("No geometry or coordinates resolved");
        }
        String blockId = fallbackBlockId == null ? "" : fallbackBlockId;
        List<BlockPlacementData> out = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (pos == null) {
                return SourceResolution.fail("Geometry voxelization produced a null position");
            }
            out.add(new BlockPlacementData(pos, blockId));
        }
        return SourceResolution.flat(SourceKind.GEOMETRY, out);
    }
}
