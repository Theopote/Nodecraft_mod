package com.nodecraft.nodesystem.api;

/**
 * Central classification of type relationships in the node system.
 * This registry does not perform runtime conversion. It only answers:
 * - which connections are safe to allow implicitly at the port layer
 * - which conversions require an explicit conversion node
 * - which type pairs are unsupported
 */
public final class TypeConversionRegistry {

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

    private static boolean isExplicitConversionPair(NodeDataType outputType, NodeDataType inputType) {
        return isPointToBlockCoordinateConversion(outputType, inputType)
                || isBlockFaceToPlaneConversion(outputType, inputType)
                || isSurfaceStripToGeometryConversion(outputType, inputType)
                || isGeometryToPlacementConversion(outputType, inputType);
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
                || type == NodeDataType.CYLINDER_GEOMETRY
                || type == NodeDataType.ELLIPSOID_GEOMETRY
                || type == NodeDataType.OCTAHEDRON_GEOMETRY
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

    private static boolean isCoordinateListAlias(NodeDataType type) {
        return type == NodeDataType.COORDINATE_LIST || type == NodeDataType.BLOCK_LIST;
    }
}
