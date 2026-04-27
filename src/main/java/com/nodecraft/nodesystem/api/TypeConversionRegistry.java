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

        if (input == NodeDataType.ANY || output == NodeDataType.ANY || output == input) {
            return ConversionPolicy.IMPLICIT_SAFE;
        }

        if (isSemanticAliasCompatible(output, input)) {
            return ConversionPolicy.IMPLICIT_SAFE;
        }

        if (isCoordinateToPositionCompatible(output, input)) {
            return ConversionPolicy.IMPLICIT_SAFE;
        }

        if (isNumericType(output) && isNumericType(input)) {
            return ConversionPolicy.IMPLICIT_SAFE;
        }

        if (output.getJavaClass() == java.util.List.class && input.getJavaClass() == java.util.List.class) {
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
        if (isBlockCoordinateToVectorConversion(output, input)) {
            return new ConversionSuggestion("reference.points.block_to_vector", "Block To Vector");
        }
        if (isPointToBlockCoordinateConversion(output, input)) {
            return new ConversionSuggestion("world.selection.snap_point_to_block", "Snap Point To Block");
        }
        if (isBlockFaceToPlaneConversion(output, input)) {
            return new ConversionSuggestion("reference.planes.block_face_plane", "Box Face To Plane");
        }
        if (isSurfaceStripToGeometryConversion(output, input)) {
            return new ConversionSuggestion("geometry.solids.surface_strip_to_geometry", "Surface Strip To Geometry");
        }
        return null;
    }

    private static boolean isExplicitConversionPair(NodeDataType outputType, NodeDataType inputType) {
        return isBlockCoordinateToPointConversion(outputType, inputType)
                || isBlockCoordinateToVectorConversion(outputType, inputType)
                || isPointToBlockCoordinateConversion(outputType, inputType)
                || isBlockFaceToPlaneConversion(outputType, inputType)
                || isSurfaceStripToGeometryConversion(outputType, inputType)
                || isGeometryToPlacementConversion(outputType, inputType);
    }

    private static boolean isBlockCoordinateToPointConversion(NodeDataType outputType, NodeDataType inputType) {
        return (outputType == NodeDataType.BLOCK_POS || outputType == NodeDataType.COORDINATE)
                && inputType == NodeDataType.POINT;
    }

    private static boolean isBlockCoordinateToVectorConversion(NodeDataType outputType, NodeDataType inputType) {
        return (outputType == NodeDataType.BLOCK_POS || outputType == NodeDataType.COORDINATE)
                && (inputType == NodeDataType.VECTOR || inputType == NodeDataType.POSITION);
    }

    private static boolean isPointToBlockCoordinateConversion(NodeDataType outputType, NodeDataType inputType) {
        return outputType == NodeDataType.POINT
                && (inputType == NodeDataType.COORDINATE || inputType == NodeDataType.BLOCK_POS);
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

    private static boolean isSemanticAliasCompatible(NodeDataType outputType, NodeDataType inputType) {
        boolean coordinateAlias = isCoordinateAlias(outputType) && isCoordinateAlias(inputType);
        boolean vectorAlias = isVectorAlias(outputType) && isVectorAlias(inputType);
        boolean coordinateListAlias = isCoordinateListAlias(outputType) && isCoordinateListAlias(inputType);
        return coordinateAlias || vectorAlias || coordinateListAlias;
    }

    private static boolean isCoordinateAlias(NodeDataType type) {
        return type == NodeDataType.COORDINATE || type == NodeDataType.BLOCK_POS;
    }

    private static boolean isVectorAlias(NodeDataType type) {
        return type == NodeDataType.VECTOR || type == NodeDataType.POSITION;
    }

    private static boolean isCoordinateToPositionCompatible(NodeDataType outputType, NodeDataType inputType) {
        return isCoordinateAlias(outputType)
                && (inputType == NodeDataType.POINT || isVectorAlias(inputType));
    }

    private static boolean isCoordinateListAlias(NodeDataType type) {
        return type == NodeDataType.COORDINATE_LIST || type == NodeDataType.BLOCK_LIST;
    }
}
