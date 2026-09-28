package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.BoxBlockGenerator;
import com.nodecraft.nodesystem.util.GeometryBoundsResolver;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Lightweight deconstruct base: Valid + Error ports and shared invalid helpers (Graph V74).
 * Graph V90: continuous Bounding Box vs block-space Region dual-track bounds.
 */
abstract class AbstractPrimitiveDeconstructNode extends AbstractPrimitiveNode {

    protected static final String REGION_PORT_DESCRIPTION =
        "Block-space bounds derived from continuous AABB";
    protected static final String BOUNDING_BOX_PORT_DESCRIPTION =
        "Geometric axis-aligned bounds";

    protected AbstractPrimitiveDeconstructNode(String typeName) {
        super(UUID.randomUUID(), typeName);
    }

    protected record BoundsAndRegion(BoundingBoxData boundingBox, RegionData region) {
    }

    protected @Nullable BoundsAndRegion resolveContinuousBoundsAndRegion(GeometryData geometry) {
        BoundingBoxData box = GeometryBoundsResolver.resolve(geometry);
        if (box == null || !box.isValid()) {
            return null;
        }
        RegionData region = BoxBlockGenerator.regionFromBoundingBox(box);
        return new BoundsAndRegion(box, region);
    }
}
