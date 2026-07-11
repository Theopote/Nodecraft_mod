package com.nodecraft.nodesystem.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeConversionRegistryTest {

    @Test
    void execTypeOnlyConnectsToExecType() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.EXEC, NodeDataType.EXEC));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.EXEC, NodeDataType.BOOLEAN));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.ANY, NodeDataType.EXEC));
        assertEquals(TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
                TypeConversionRegistry.classify(NodeDataType.EXEC, NodeDataType.BOOLEAN));
    }

    @Test
    void anyTypeIsImplicitlyConnectable() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE,
            TypeConversionRegistry.classify(NodeDataType.ANY, NodeDataType.GEOMETRY));
        assertEquals(TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE,
            TypeConversionRegistry.classify(NodeDataType.SPHERE, NodeDataType.ANY));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.ANY, NodeDataType.STRING));
    }

    @Test
    void numericTypesAreImplicitlyConnectable() {
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.INTEGER, NodeDataType.FLOAT));
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.DOUBLE, NodeDataType.INTEGER));
    }

    @Test
    void coordinateAliasesAreImplicitlyConnectable() {
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.BLOCK_POS, NodeDataType.COORDINATE));
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.COORDINATE, NodeDataType.BLOCK_POS));
    }

    @Test
    void specificGeometryTypesConnectImplicitlyToGeometryInput() {
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.SPHERE, NodeDataType.GEOMETRY));
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.BOX_GEOMETRY, NodeDataType.GEOMETRY));
    }

    @Test
    void pointToBlockCoordinateRequiresExplicitConversion() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.POINT, NodeDataType.BLOCK_POS));
        assertTrue(TypeConversionRegistry.requiresExplicitConversion(NodeDataType.POINT, NodeDataType.COORDINATE));

        TypeConversionRegistry.ConversionSuggestion suggestion =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.POINT, NodeDataType.BLOCK_POS);
        assertNotNull(suggestion);
        assertEquals("world.selection.snap_point_to_block", suggestion.nodeId());
    }

    @Test
    void blockCoordinateToPointIsImplicitBecauseOfCoordinateCompatibility() {
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.BLOCK_POS, NodeDataType.POINT));
    }

    @Test
    void geometryToBlockListRequiresExplicitConversion() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.GEOMETRY, NodeDataType.BLOCK_LIST));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.GEOMETRY, NodeDataType.BLOCK_LIST));
    }

    @Test
    void unrelatedTypesAreUnsupported() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
            TypeConversionRegistry.classify(NodeDataType.STRING, NodeDataType.GEOMETRY));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.BOOLEAN, NodeDataType.BLOCK_LIST));
        assertNull(TypeConversionRegistry.getSuggestedConversion(NodeDataType.STRING, NodeDataType.GEOMETRY));
    }

    @Test
    void describeRelationMatchesPolicy() {
        assertEquals("implicitly connectable",
            TypeConversionRegistry.describeRelation(NodeDataType.FLOAT, NodeDataType.DOUBLE));
        assertEquals("explicit conversion node required",
            TypeConversionRegistry.describeRelation(NodeDataType.POINT, NodeDataType.BLOCK_POS));
        assertEquals("unsupported type relationship",
            TypeConversionRegistry.describeRelation(NodeDataType.STRING, NodeDataType.INTEGER));
    }

    @Test
    void typedListsWithDifferentElementKindsAreUnsupported() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
            TypeConversionRegistry.classify(NodeDataType.VECTOR_LIST, NodeDataType.REGION_LIST));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.VECTOR_LIST, NodeDataType.BLOCK_PLACEMENT_LIST));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.BLOCK_INFO_LIST, NodeDataType.PLANT_STRUCTURE_LIST));
    }

    @Test
    void typedListsWithSameElementKindAreConnectable() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.VECTOR_LIST, NodeDataType.VECTOR_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.REGION_LIST, NodeDataType.REGION_LIST));
    }

    @Test
    void coordinateListAliasesRemainConnectable() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.COORDINATE_LIST, NodeDataType.BLOCK_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.BLOCK_LIST, NodeDataType.COORDINATE_LIST));
    }

    @Test
    void genericListConnectsToTypedLists() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.VECTOR_LIST, NodeDataType.LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.LIST, NodeDataType.VECTOR_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.LIST, NodeDataType.LIST));
    }
}
