package com.nodecraft.nodesystem.api;

/**
 * Central classification of type relationships in the node system.
 * This registry does not perform runtime conversion. It only answers:
 * - which connections are safe to allow implicitly at the port layer
 * - which conversions require an explicit conversion node
 * - which type pairs are unsupported
 */
public final class TypeConversionRegistry {

    public record ConversionSuggestion(String nodeId, String displayName) {
    }

    public enum ConversionPolicy {
        IMPLICIT_SAFE,
        EXPLICIT_REQUIRED,
        UNSUPPORTED
    }

    private TypeConversionRegistry() {
    }

    public static ConversionPolicy classify(NodeDataType outputType, NodeDataType inputType) {
        NodeDataType output = outputType == null ? NodeDataType.ANY : outputType;
        NodeDataType input = inputType == null ? NodeDataType.ANY : inputType;

        if (output == NodeDataType.EXEC || input == NodeDataType.EXEC) {
            return output == NodeDataType.EXEC && input == NodeDataType.EXEC
                    ? ConversionPolicy.IMPLICIT_SAFE
                    : ConversionPolicy.UNSUPPORTED;
        }

        if (input == NodeDataType.ANY || output == NodeDataType.ANY || output == input) {
            return ConversionPolicy.IMPLICIT_SAFE;
        }

        if (isNumericType(output) && isNumericType(input)) {
            return ConversionPolicy.IMPLICIT_SAFE;
        }

        if (isPathSourceCompatible(output, input)) {
            return ConversionPolicy.IMPLICIT_SAFE;
        }

        if (isListConnectable(output, input)) {
            return ConversionPolicy.IMPLICIT_SAFE;
        }

        if (input == NodeDataType.GEOMETRY && isSpecificGeometryType(output)) {
            return ConversionPolicy.IMPLICIT_SAFE;
        }

        if (isExplicitConversionPair(output, input)) {
            return ConversionPolicy.EXPLICIT_REQUIRED;
        }

        return ConversionPolicy.UNSUPPORTED;
    }

    public static boolean isImplicitlyConnectable(NodeDataType outputType, NodeDataType inputType) {
        return classify(outputType, inputType) == ConversionPolicy.IMPLICIT_SAFE;
    }

    public static boolean requiresExplicitConversion(NodeDataType outputType, NodeDataType inputType) {
        return classify(outputType, inputType) == ConversionPolicy.EXPLICIT_REQUIRED;
    }

    public static String describeRelation(NodeDataType outputType, NodeDataType inputType) {
        NodeDataType output = outputType == null ? NodeDataType.ANY : outputType;
        NodeDataType input = inputType == null ? NodeDataType.ANY : inputType;
        ConversionPolicy policy = classify(output, input);
        return switch (policy) {
            case IMPLICIT_SAFE -> "implicitly connectable";
            case EXPLICIT_REQUIRED -> "explicit conversion node required";
            case UNSUPPORTED -> "unsupported type relationship";
        };
    }

    public static ConversionSuggestion getSuggestedConversion(NodeDataType outputType, NodeDataType inputType) {
        NodeDataType output = outputType == null ? NodeDataType.ANY : outputType;
        NodeDataType input = inputType == null ? NodeDataType.ANY : inputType;

        if (isBlockCoordinateToPointConversion(output, input)) {
            return new ConversionSuggestion("reference.points.point_from_block", "Block To Point");
        }
        if (isPointToBlockCoordinateConversion(output, input)) {
            return new ConversionSuggestion("world.selection.snap_point_to_block", "Snap Point To Block");
        }
        if (isBlockFaceToPlaneConversion(output, input)) {
            return new ConversionSuggestion("reference.planes.box_face_plane", "Box Face To Plane");
        }
        if (isSurfaceStripToGeometryConversion(output, input)) {
            return new ConversionSuggestion("geometry.solids.surface_strip_to_lattice", "Surface Strip To Lattice");
        }
        if (isLegacyPaletteConversion(output, input)) {
            return new ConversionSuggestion("material.basic_assignment.create_block_palette", "Create Block Palette");
        }
        if (isListToDataTreeConversion(output, input)) {
            return new ConversionSuggestion("math.data_tree.graft_list", "Graft List");
        }
        if (isDataTreeToListConversion(output, input)) {
            return new ConversionSuggestion("math.data_tree.flatten", "Flatten Tree");
        }
        if (isSdfToScalarFieldConversion(output, input)) {
            return new ConversionSuggestion("math.fields.scalar_from_sdf", "Scalar Field From SDF");
        }
        if (isSdfToVectorFieldConversion(output, input)) {
            return new ConversionSuggestion("math.fields.vector_from_sdf_gradient", "Vector Field From SDF Gradient");
        }
        return null;
    }

    private static boolean isExplicitConversionPair(NodeDataType outputType, NodeDataType inputType) {
        return isBlockCoordinateToPointConversion(outputType, inputType)
                || isBlockCoordinateToVectorConversion(outputType, inputType)
                || isVectorToPointConversion(outputType, inputType)
                || isPointToBlockCoordinateConversion(outputType, inputType)
                || isBlockFaceToPlaneConversion(outputType, inputType)
                || isSurfaceStripToGeometryConversion(outputType, inputType)
                || isGeometryToPlacementConversion(outputType, inputType)
                || isLegacyPaletteConversion(outputType, inputType)
                || isListToDataTreeConversion(outputType, inputType)
                || isDataTreeToListConversion(outputType, inputType)
                || isSdfToScalarFieldConversion(outputType, inputType)
                || isSdfToVectorFieldConversion(outputType, inputType);
    }

    private static boolean isBlockCoordinateToPointConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.BLOCK_POS && inputType == NodeDataType.POINT;
    }

    private static boolean isBlockCoordinateToVectorConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.BLOCK_POS && inputType == NodeDataType.VECTOR;
    }

    private static boolean isPointToBlockCoordinateConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.POINT && inputType == NodeDataType.BLOCK_POS;
    }

    private static boolean isBlockFaceToPlaneConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.BOX_FACE && inputType == NodeDataType.PLANE;
    }

    private static boolean isSurfaceStripToGeometryConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.SURFACE_STRIP && inputType == NodeDataType.GEOMETRY;
    }

    private static boolean isGeometryToPlacementConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.GEOMETRY
                && (inputType == NodeDataType.BLOCK_LIST || inputType == NodeDataType.BLOCK_PLACEMENT_LIST);
    }

    /**
     * Legacy list/string block-id bags require Create Block Palette before typed palette ports.
     */
    private static boolean isLegacyPaletteConversion(NodeDataType outputType, NodeDataType inputType) {
        return inputType == NodeDataType.BLOCK_PALETTE
                && (outputType == NodeDataType.LIST || outputType == NodeDataType.BLOCK_TYPE || outputType == NodeDataType.STRING);
    }

    /**
     * Generic lists become data trees only through Graft List / Partition List.
     */
    private static boolean isListToDataTreeConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.LIST && inputType == NodeDataType.DATA_TREE;
    }

    /**
     * Data trees become flat lists only through Flatten Tree.
     */
    private static boolean isDataTreeToListConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.DATA_TREE && inputType == NodeDataType.LIST;
    }

    /** SDF distance values become scalar fields via Scalar Field From SDF. */
    private static boolean isSdfToScalarFieldConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.SDF && inputType == NodeDataType.SCALAR_FIELD;
    }

    /** SDF gradients become vector fields via Vector Field From SDF Gradient. */
    private static boolean isSdfToVectorFieldConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.SDF && inputType == NodeDataType.VECTOR_FIELD;
    }

    private static boolean isNumericType(NodeDataType type) {
        return type == NodeDataType.INTEGER || type == NodeDataType.FLOAT || type == NodeDataType.DOUBLE;
    }

    private static boolean isSpecificGeometryType(NodeDataType type) {
        return type == NodeDataType.BOX_GEOMETRY
                || type == NodeDataType.CONE_GEOMETRY
                || type == NodeDataType.FRUSTUM_CONE_GEOMETRY
                || type == NodeDataType.CYLINDER_GEOMETRY
                || type == NodeDataType.ELLIPSOID_GEOMETRY
                || type == NodeDataType.HEMISPHERE_GEOMETRY
                || type == NodeDataType.OCTAHEDRON_GEOMETRY
                || type == NodeDataType.ICOSAHEDRON_GEOMETRY
                || type == NodeDataType.DODECAHEDRON_GEOMETRY
                || type == NodeDataType.PRISM_GEOMETRY
                || type == NodeDataType.SPHERE
                || type == NodeDataType.TETRAHEDRON_GEOMETRY
                || type == NodeDataType.TORUS_GEOMETRY;
    }

    /**
     * Direction / displacement vectors must not silently become Points.
     */
    private static boolean isVectorToPointConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.VECTOR && inputType == NodeDataType.POINT;
    }

    private static boolean isPathSourceCompatible(NodeDataType outputType, NodeDataType inputType) {
        if (inputType != NodeDataType.PATH) {
            return false;
        }
        return outputType == NodeDataType.LINE
                || outputType == NodeDataType.POLYLINE
                || outputType == NodeDataType.CURVE;
    }

    private static boolean isListConnectable(NodeDataType outputType, NodeDataType inputType) {
        if (!outputType.isListType() || !inputType.isListType()) {
            return false;
        }

        ListElementKind outputKind = outputType.getListElementKind();
        ListElementKind inputKind = inputType.getListElementKind();

        // typed → LIST (safe widening)
        if (inputKind == ListElementKind.UNCONSTRAINED) {
            return true;
        }
        // LIST → typed (forbidden; needs explicit As/Validate)
        if (outputKind == ListElementKind.UNCONSTRAINED) {
            return false;
        }

        return outputKind == inputKind;
    }
}
