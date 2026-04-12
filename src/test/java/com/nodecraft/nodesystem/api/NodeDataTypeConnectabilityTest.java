package com.nodecraft.nodesystem.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeDataTypeConnectabilityTest {

    @Test
    void numericTypesAreMutuallyConnectable() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.INTEGER, NodeDataType.FLOAT));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.FLOAT, NodeDataType.DOUBLE));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.DOUBLE, NodeDataType.INTEGER));
    }

    @Test
    void coordinateAliasesAreMutuallyConnectable() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.COORDINATE, NodeDataType.BLOCK_POS));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.BLOCK_POS, NodeDataType.COORDINATE));
    }

    @Test
    void vectorAliasesAreMutuallyConnectable() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.VECTOR, NodeDataType.POSITION));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.POSITION, NodeDataType.VECTOR));
    }

    @Test
    void coordinateListAliasesAreMutuallyConnectable() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.COORDINATE_LIST, NodeDataType.BLOCK_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.BLOCK_LIST, NodeDataType.COORDINATE_LIST));
    }

    @Test
    void geometrySubtypesCanConnectToGeometry() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.PRISM_GEOMETRY, NodeDataType.GEOMETRY));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.SPHERE, NodeDataType.GEOMETRY));
    }

    @Test
    void unrelatedStringSemanticTypesStaySeparated() {
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.BIOME, NodeDataType.ITEM_TYPE));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.BLOCK_TYPE, NodeDataType.SOUND_EVENT));
    }

    @Test
    void rejectionReasonIsAvailableForIncompatibleTypes() {
        String incompatibleReason = NodeDataType.getConnectabilityRejectionReason(NodeDataType.BIOME, NodeDataType.ITEM_TYPE);
        assertNotNull(incompatibleReason);

        String compatibleReason = NodeDataType.getConnectabilityRejectionReason(NodeDataType.COORDINATE, NodeDataType.BLOCK_POS);
        assertNull(compatibleReason);
    }

    @Test
    void pointToBlockCoordinateRequiresExplicitConversionNode() {
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.POINT, NodeDataType.BLOCK_POS));
        assertEquals(
                TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
                TypeConversionRegistry.classify(NodeDataType.POINT, NodeDataType.BLOCK_POS)
        );
    }

    @Test
    void geometryToPlacementRequiresExplicitConversionNode() {
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.GEOMETRY, NodeDataType.BLOCK_PLACEMENT_LIST));
        assertEquals(
                TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
                TypeConversionRegistry.classify(NodeDataType.GEOMETRY, NodeDataType.BLOCK_PLACEMENT_LIST)
        );
    }

    @Test
    void explicitConversionReasonIsReturnedForSupportedButNonImplicitPairs() {
        String reason = NodeDataType.getConnectabilityRejectionReason(NodeDataType.BOX_FACE, NodeDataType.PLANE);
        assertNotNull(reason);
        assertTrue(reason.contains("explicit conversion node required"));
    }
}
