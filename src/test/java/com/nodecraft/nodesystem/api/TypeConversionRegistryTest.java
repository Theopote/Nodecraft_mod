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
    void anyTypeWidensAsSinkButNotAsSource() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
            TypeConversionRegistry.classify(NodeDataType.ANY, NodeDataType.GEOMETRY));
        assertEquals(TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE,
            TypeConversionRegistry.classify(NodeDataType.SPHERE, NodeDataType.ANY));
        assertEquals(TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE,
            TypeConversionRegistry.classify(NodeDataType.ANY, NodeDataType.ANY));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.ANY, NodeDataType.STRING));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.STRING, NodeDataType.ANY));
    }

    @Test
    void numericTypesAreImplicitlyConnectable() {
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.INTEGER, NodeDataType.FLOAT));
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.DOUBLE, NodeDataType.INTEGER));
    }

    @Test
    void unknownLegacyTypeIdsMapToAny() {
        assertEquals(NodeDataType.ANY, NodeDataType.fromId("coordinate"));
        assertEquals(NodeDataType.ANY, NodeDataType.fromId("position"));
        assertEquals(NodeDataType.ANY, NodeDataType.fromId("coordinate_list"));
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
        assertTrue(TypeConversionRegistry.requiresExplicitConversion(
                NodeDataType.POINT, NodeDataType.BLOCK_POS));

        TypeConversionRegistry.ConversionSuggestion suggestion =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.POINT, NodeDataType.BLOCK_POS);
        assertNotNull(suggestion);
        assertEquals("world.selection.snap_point_to_block", suggestion.nodeId());
    }

    @Test
    void blockCoordinateToPointRequiresExplicitConversion() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.BLOCK_POS, NodeDataType.POINT));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.BLOCK_POS, NodeDataType.POINT));

        TypeConversionRegistry.ConversionSuggestion suggestion =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.BLOCK_POS, NodeDataType.POINT);
        assertNotNull(suggestion);
        assertEquals("reference.points.point_from_block", suggestion.nodeId());
        assertEquals("Block To Point", suggestion.displayName());
    }

    @Test
    void pointConnectsImplicitlyToPointButVectorRequiresExplicitConversion() {
        assertTrue(TypeConversionRegistry.isImplicitlyConnectable(
                NodeDataType.POINT, NodeDataType.POINT));
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.VECTOR, NodeDataType.POINT));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.VECTOR, NodeDataType.POINT));
    }

    @Test
    void geometryToBlockListRequiresExplicitConversion() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.GEOMETRY, NodeDataType.BLOCK_LIST));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.GEOMETRY, NodeDataType.BLOCK_LIST));
    }

    @Test
    void legacyListToBlockPaletteRequiresCreateBlockPalette() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.LIST, NodeDataType.BLOCK_PALETTE));
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.BLOCK_TYPE, NodeDataType.BLOCK_PALETTE));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.LIST, NodeDataType.BLOCK_PALETTE));

        TypeConversionRegistry.ConversionSuggestion suggestion =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.LIST, NodeDataType.BLOCK_PALETTE);
        assertNotNull(suggestion);
        assertEquals("material.basic_assignment.create_block_palette", suggestion.nodeId());
    }

    @Test
    void listAndDataTreeRequireExplicitGraftOrFlatten() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.LIST, NodeDataType.DATA_TREE));
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.DATA_TREE, NodeDataType.LIST));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.LIST, NodeDataType.DATA_TREE));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.DATA_TREE, NodeDataType.LIST));

        TypeConversionRegistry.ConversionSuggestion graft =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.LIST, NodeDataType.DATA_TREE);
        assertNotNull(graft);
        assertEquals("math.data_tree.graft_list", graft.nodeId());

        TypeConversionRegistry.ConversionSuggestion flatten =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.DATA_TREE, NodeDataType.LIST);
        assertNotNull(flatten);
        assertEquals("math.data_tree.flatten", flatten.nodeId());
    }

    @Test
    void sdfToFieldRequiresExplicitConversionNodes() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.SDF, NodeDataType.SCALAR_FIELD));
        assertEquals(TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.SDF, NodeDataType.VECTOR_FIELD));
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.SDF, NodeDataType.SCALAR_FIELD));

        TypeConversionRegistry.ConversionSuggestion scalar =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.SDF, NodeDataType.SCALAR_FIELD);
        assertNotNull(scalar);
        assertEquals("math.fields.scalar_from_sdf", scalar.nodeId());

        TypeConversionRegistry.ConversionSuggestion vector =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.SDF, NodeDataType.VECTOR_FIELD);
        assertNotNull(vector);
        assertEquals("math.fields.vector_from_sdf_gradient", vector.nodeId());
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
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.PATH_LIST, NodeDataType.PATH_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.FRAME_LIST, NodeDataType.FRAME_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.POINT_LIST, NodeDataType.POINT_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.DOUBLE_LIST, NodeDataType.DOUBLE_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.BOOLEAN_LIST, NodeDataType.BOOLEAN_LIST));
    }

    @Test
    void doubleAndBooleanListsDoNotSilentlyCrossConnect() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
            TypeConversionRegistry.classify(NodeDataType.DOUBLE_LIST, NodeDataType.BOOLEAN_LIST));
        assertEquals(TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
            TypeConversionRegistry.classify(NodeDataType.DOUBLE_LIST, NodeDataType.POINT_LIST));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.LIST, NodeDataType.DOUBLE_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.BOOLEAN_LIST, NodeDataType.LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.DOUBLE_LIST, NodeDataType.LIST));
    }

    @Test
    void typedListWidensToGenericListButNotTheReverse() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.VECTOR_LIST, NodeDataType.LIST));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.LIST, NodeDataType.VECTOR_LIST));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.LIST, NodeDataType.POINT_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.LIST, NodeDataType.LIST));
    }

    @Test
    void pathListDoesNotSilentlyConnectToOtherTypedLists() {
        assertEquals(TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
            TypeConversionRegistry.classify(NodeDataType.PATH_LIST, NodeDataType.POINT_LIST));
        assertEquals(TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
            TypeConversionRegistry.classify(NodeDataType.PATH_LIST, NodeDataType.FRAME_LIST));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.PATH_LIST, NodeDataType.VECTOR_LIST));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.PATH_LIST, NodeDataType.LIST));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.LIST, NodeDataType.PATH_LIST));
    }

    @Test
    void blockListConnectsToItself() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.BLOCK_LIST, NodeDataType.BLOCK_LIST));
    }
}
