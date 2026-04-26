package com.nodecraft.nodesystem.api;

import com.nodecraft.nodesystem.datatypes.*;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

/**
 * Declares the data types supported by the node system.
 *
 * Connectability rules are intentionally delegated to {@link TypeConversionRegistry}
 * so that type compatibility and conversion policy do not keep growing inside this enum.
 */
public enum NodeDataType {
    ANY("any", "Any", Object.class),
    STRING("string", "String", String.class),
    INTEGER("integer", "Integer", Integer.class),
    DOUBLE("double", "Double", Double.class),
    BOOLEAN("boolean", "Boolean", Boolean.class),

    FLOAT("float", "Float", Float.class),
    NUMERIC_RANGE("numeric_range", "Numeric Range", NumericRangeData.class),

    POINT("point", "Point", PointData.class),
    VECTOR("vector", "Vector", Vector3d.class),
    COORDINATE("coordinate", "Coordinate", BlockPos.class),
    POSITION("position", "Position", Vector3d.class),
    PLANE("plane", "Plane", PlaneData.class),
    BOUNDING_BOX("bounding_box", "Bounding Box", BoundingBoxData.class),
    GEOMETRY("geometry", "Geometry", GeometryData.class),
    SDF("sdf", "SDF", SignedDistanceFieldData.class),
    BOX_GEOMETRY("box_geometry", "Box Geometry", BoxGeometryData.class),
    BOX_FACE("box_face", "Box Face", BoxFaceData.class),
    CONE_GEOMETRY("cone_geometry", "Cone Geometry", ConeGeometryData.class),
    CYLINDER_GEOMETRY("cylinder_geometry", "Cylinder Geometry", CylinderGeometryData.class),
    ELLIPSOID_GEOMETRY("ellipsoid_geometry", "Ellipsoid Geometry", EllipsoidGeometryData.class),
    OCTAHEDRON_GEOMETRY("octahedron_geometry", "Octahedron Geometry", OctahedronGeometryData.class),
    POLYGON_PROFILE("polygon_profile", "Polygon Profile", PolygonProfileData.class),
    PRISM_GEOMETRY("prism_geometry", "Prism Geometry", PrismGeometryData.class),
    TETRAHEDRON_GEOMETRY("tetrahedron_geometry", "Tetrahedron Geometry", TetrahedronGeometryData.class),
    TORUS_GEOMETRY("torus_geometry", "Torus Geometry", TorusGeometryData.class),
    SPHERE("sphere", "Sphere", SphereData.class),
    SURFACE_STRIP("surface_strip", "Surface Strip", SurfaceStripData.class),
    LINE("line", "Line", LineData.class),
    POLYLINE("polyline", "Polyline", PolylineData.class),
    CURVE("curve", "Curve", Curve.class),
    REGION("region", "Region", RegionData.class),

    COLOR("color", "Color", ColorData.class),

    BLOCK_POS("block_pos", "Block Position", BlockPos.class),
    BLOCK_LIST("block_list", "Block List", BlockPosList.class),
    BLOCK_INFO("block_info", "Block Info", Object.class),
    BLOCK_STATE_DATA("block_state_data", "Block State Data", com.nodecraft.nodesystem.util.BlockStateData.class),
    BLOCK_TYPE("block_type", "Block Type", String.class),
    ITEM_TYPE("item_type", "Item Type", String.class),
    ITEM_STACK("item_stack", "Item Stack", Object.class),
    ENTITY_TYPE("entity_type", "Entity Type", String.class),
    ENTITY_INFO("entity_info", "Entity Info", Object.class),
    MINECRAFT_ENTITY("minecraft_entity", "Minecraft Entity", Object.class),
    MINECRAFT_BLOCK("minecraft_block", "Minecraft Block", Object.class),
    BIOME("biome", "Biome", String.class),
    WORLD("world", "World", Object.class),
    DIMENSION("dimension", "Dimension", Object.class),
    PLAYER("player", "Player", Object.class),
    SOUND_EVENT("sound_event", "Sound Event", String.class),
    EFFECT_TYPE("effect_type", "Effect Type", String.class),

    NBT_COMPOUND("nbt_compound", "NBT Compound", Object.class),
    NBT_LIST("nbt_list", "NBT List", Object.class),
    NBT("nbt", "NBT", Object.class),

    L_SYSTEM_RULE("l_system_rule", "L-System Rule", LSystemRule.class),
    L_SYSTEM_RULE_LIST("l_system_rule_list", "L-System Rule List", java.util.List.class),
    PLANT_STRUCTURE("plant_structure", "Plant Structure", PlantStructure.class),
    PLANT_BLOCK("plant_block", "Plant Block", PlantStructure.PlantBlock.class),
    PLANT_BLOCK_LIST("plant_block_list", "Plant Block List", java.util.List.class),
    TREE_TYPE("tree_type", "Tree Type", String.class),
    BUSH_TYPE("bush_type", "Bush Type", String.class),
    FLOWER_TYPE("flower_type", "Flower Type", String.class),
    PLANT_PART("plant_part", "Plant Part", String.class),

    FILE_PATH("file_path", "File Path", String.class),

    LIST("list", "List", java.util.List.class),
    COORDINATE_LIST("coordinate_list", "Coordinate List", java.util.List.class),
    BLOCK_INFO_LIST("block_info_list", "Block Info List", java.util.List.class),
    BLOCK_PLACEMENT_LIST("block_placement_list", "Block Placement List", java.util.List.class),
    VECTOR_LIST("vector_list", "Vector List", java.util.List.class),
    REGION_LIST("region_list", "Region List", java.util.List.class),
    PLANT_STRUCTURE_LIST("plant_structure_list", "Plant Structure List", java.util.List.class);

    private final String id;
    private final String displayName;
    private final Class<?> javaClass;

    NodeDataType(String id, String displayName, Class<?> javaClass) {
        this.id = id;
        this.displayName = displayName;
        this.javaClass = javaClass;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Class<?> getJavaClass() {
        return javaClass;
    }

    public boolean isCompatible(Object value) {
        if (value == null || this == ANY) {
            return true;
        }

        if (isListType(this) && value instanceof java.util.List) {
            return true;
        }

        if (this == DOUBLE && value instanceof Number) {
            return true;
        }

        if (this == INTEGER && value instanceof Integer) {
            return true;
        }

        if (this == FLOAT && value instanceof Float) {
            return true;
        }

        if (this == NBT) {
            String simpleName = value.getClass().getSimpleName();
            return simpleName.contains("Tag") || simpleName.contains("NBT");
        }

        if (this == GEOMETRY && value instanceof GeometryData) {
            return true;
        }
        if (this == SDF && value instanceof SignedDistanceFieldData) {
            return true;
        }

        return javaClass != null && javaClass.isInstance(value);
    }

    public static boolean isConnectableTo(NodeDataType outputType, NodeDataType inputType) {
        return TypeConversionRegistry.isImplicitlyConnectable(outputType, inputType);
    }

    public static String getConnectabilityRejectionReason(NodeDataType outputType, NodeDataType inputType) {
        NodeDataType normalizedOutput = outputType == null ? ANY : outputType;
        NodeDataType normalizedInput = inputType == null ? ANY : inputType;

        if (isConnectableTo(normalizedOutput, normalizedInput)) {
            return null;
        }

        return TypeConversionRegistry.describeRelation(normalizedOutput, normalizedInput) + ": "
                + normalizedOutput.getId() + " (" + normalizedOutput.getDisplayName() + ") -> "
                + normalizedInput.getId() + " (" + normalizedInput.getDisplayName() + ")";
    }

    public static NodeDataType fromId(String id) {
        if (id == null) {
            return ANY;
        }
        for (NodeDataType type : values()) {
            if (id.equals(type.id)) {
                return type;
            }
        }
        System.err.println("Warning: NodeDataType not found for id: " + id + ". Returning ANY.");
        return ANY;
    }

    private static boolean isListType(NodeDataType type) {
        return type == LIST
                || type == COORDINATE_LIST
                || type == BLOCK_INFO_LIST
                || type == BLOCK_PLACEMENT_LIST
                || type == VECTOR_LIST
                || type == REGION_LIST
                || type == PLANT_STRUCTURE_LIST
                || type == L_SYSTEM_RULE_LIST
                || type == PLANT_BLOCK_LIST;
    }
}
