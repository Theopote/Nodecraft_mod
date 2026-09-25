package com.nodecraft.nodesystem.api;

import com.nodecraft.nodesystem.datatypes.*;
import com.nodecraft.nodesystem.util.*;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Declares the data types supported by the node system.
 * <p>
 * Connectability rules are intentionally delegated to {@link TypeConversionRegistry}
 * so that type compatibility and conversion policy do not keep growing inside this enum.
 */
public enum NodeDataType {
    ANY("any", "Any", Object.class),
    STRING("string", "String", String.class),
    INTEGER("integer", "Integer", Integer.class),
    DOUBLE("double", "Double", Double.class),
    MATRIX3("matrix3", "Matrix3", Matrix3d.class),
    BOOLEAN("boolean", "Boolean", Boolean.class),
    EXEC("exec", "Execution", Void.class),

    FLOAT("float", "Float", Float.class),
    NUMERIC_RANGE("numeric_range", "Numeric Range", NumericRangeData.class),

    POINT("point", "Point", PointData.class),
    VECTOR("vector", "Vector", Vector3d.class),
    PLANE("plane", "Plane", PlaneData.class),
    /** Origin + X/Y/Z axes. Distinct from {@link #PLANE} (origin + normal only). */
    FRAME("frame", "Frame", FrameData.class),
    BOUNDING_BOX("bounding_box", "Bounding Box", BoundingBoxData.class),
    GEOMETRY("geometry", "Geometry", GeometryData.class),
    SDF("sdf", "SDF", SignedDistanceFieldData.class),
    SCALAR_FIELD("scalar_field", "Scalar Field", ScalarFieldData.class),
    VECTOR_FIELD("vector_field", "Vector Field", VectorFieldData.class),
    BOX_GEOMETRY("box_geometry", "Box Geometry", BoxGeometryData.class),
    BOX_FACE("box_face", "Box Face", BoxFaceData.class),
    CONE_GEOMETRY("cone_geometry", "Cone Geometry", ConeGeometryData.class),
    FRUSTUM_CONE_GEOMETRY("frustum_cone_geometry", "Frustum Cone Geometry", FrustumConeGeometryData.class),
    CYLINDER_GEOMETRY("cylinder_geometry", "Cylinder Geometry", CylinderGeometryData.class),
    ELLIPSOID_GEOMETRY("ellipsoid_geometry", "Ellipsoid Geometry", EllipsoidGeometryData.class),
    HEMISPHERE_GEOMETRY("hemisphere_geometry", "Hemisphere Geometry", HemisphereGeometryData.class),
    OCTAHEDRON_GEOMETRY("octahedron_geometry", "Octahedron Geometry", OctahedronGeometryData.class),
    ICOSAHEDRON_GEOMETRY("icosahedron_geometry", "Icosahedron Geometry", IcosahedronGeometryData.class),
    DODECAHEDRON_GEOMETRY("dodecahedron_geometry", "Dodecahedron Geometry", DodecahedronGeometryData.class),
    POLYGON_PROFILE("polygon_profile", "Polygon Profile", PolygonProfileData.class),
    /** Ordered polygon profiles (loft sections, sweep section lists). */
    POLYGON_PROFILE_LIST("polygon_profile_list", "Polygon Profile List", List.class, ListElementKind.POLYGON_PROFILE),
    PRISM_GEOMETRY("prism_geometry", "Prism Geometry", PrismGeometryData.class),
    TETRAHEDRON_GEOMETRY("tetrahedron_geometry", "Tetrahedron Geometry", TetrahedronGeometryData.class),
    TORUS_GEOMETRY("torus_geometry", "Torus Geometry", TorusGeometryData.class),
    SPHERE("sphere", "Sphere", SphereData.class),
    SURFACE_STRIP("surface_strip", "Surface Strip", SurfaceStripData.class),
    LINE("line", "Line", LineData.class),
    POLYLINE("polyline", "Polyline", PolylineData.class),
    CURVE("curve", "Curve", Curve.class),
    /** Unified path input accepting LINE, POLYLINE, or CURVE via implicit connect. */
    PATH("path", "Path", PathData.class),
    REGION("region", "Region", RegionData.class),

    COLOR("color", "Color", ColorData.class),

    BLOCK_POS("block_pos", "Block Position", BlockPos.class),
    BLOCK_LIST("block_list", "Block List", BlockPosList.class, ListElementKind.BLOCK_POS),
    BLOCK_INFO("block_info", "Block Info", Object.class),
    BLOCK_STATE_DATA("block_state_data", "Block State Data", BlockStateData.class),
    BLOCK_TYPE("block_type", "Block Type", String.class),
    /** Ordered weighted block ids for Material palette mapping. */
    BLOCK_PALETTE("block_palette", "Block Palette", BlockPaletteData.class),
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
    L_SYSTEM_RULE_LIST("l_system_rule_list", "L-System Rule List", List.class, ListElementKind.L_SYSTEM_RULE),
    PLANT_STRUCTURE("plant_structure", "Plant Structure", PlantStructure.class),
    PLANT_BLOCK("plant_block", "Plant Block", PlantStructure.PlantBlock.class),
    PLANT_BLOCK_LIST("plant_block_list", "Plant Block List", List.class, ListElementKind.PLANT_BLOCK),
    TREE_TYPE("tree_type", "Tree Type", String.class),
    BUSH_TYPE("bush_type", "Bush Type", String.class),
    FLOWER_TYPE("flower_type", "Flower Type", String.class),
    PLANT_PART("plant_part", "Plant Part", String.class),

    FILE_PATH("file_path", "File Path", String.class),

    LIST("list", "List", List.class, ListElementKind.UNCONSTRAINED),
    /** Ordered integers (counts, branch sizes, index bags). */
    INTEGER_LIST("integer_list", "Integer List", List.class, ListElementKind.INTEGER),
    /** Ordered doubles (sequences, distances, numeric map results). */
    DOUBLE_LIST("double_list", "Double List", List.class, ListElementKind.DOUBLE),
    /** Ordered booleans (dispatch masks, per-item predicates). */
    BOOLEAN_LIST("boolean_list", "Boolean List", List.class, ListElementKind.BOOLEAN),
    /** Ordered strings (text sort / join sources). */
    STRING_LIST("string_list", "String List", List.class, ListElementKind.STRING),
    /** Data tree branch address (ordered integer path). */
    TREE_PATH("tree_path", "Tree Path", TreePathData.class),
    /** Ordered tree paths. */
    TREE_PATH_LIST("tree_path_list", "Tree Path List", List.class, ListElementKind.TREE_PATH),
    DATA_TREE("data_tree", "Data Tree", DataTreeData.class),
    BLOCK_INFO_LIST("block_info_list", "Block Info List", List.class, ListElementKind.BLOCK_INFO),
    BLOCK_PLACEMENT_LIST("block_placement_list", "Block Placement List", List.class, ListElementKind.BLOCK_PLACEMENT),
    VECTOR_LIST("vector_list", "Vector List", List.class, ListElementKind.VECTOR),
    /** Ordered continuous locations (corners, polyline samples, profile vertices). */
    POINT_LIST("point_list", "Point List", List.class, ListElementKind.POINT),
    /** Ordered planes (align / section / placement layouts). */
    PLANE_LIST("plane_list", "Plane List", List.class, ListElementKind.PLANE),
    /** Ordered frames (align / path / surface placement). */
    FRAME_LIST("frame_list", "Frame List", List.class, ListElementKind.FRAME),
    /** Ordered paths (beam centerlines, eave edges, multi-segment hosts). */
    PATH_LIST("path_list", "Path List", List.class, ListElementKind.PATH),
    REGION_LIST("region_list", "Region List", List.class, ListElementKind.REGION),
    PLANT_STRUCTURE_LIST("plant_structure_list", "Plant Structure List", List.class, ListElementKind.PLANT_STRUCTURE);

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeDataType.class);

    private final String id;
    private final String displayName;
    private final Class<?> javaClass;
    private final ListElementKind listElementKind;

    NodeDataType(String id, String displayName, Class<?> javaClass) {
        this(id, displayName, javaClass, ListElementKind.NONE);
    }

    NodeDataType(String id, String displayName, Class<?> javaClass, ListElementKind listElementKind) {
        this.id = id;
        this.displayName = displayName;
        this.javaClass = javaClass;
        this.listElementKind = listElementKind;
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

    public ListElementKind getListElementKind() {
        return listElementKind;
    }

    public boolean isListType() {
        return listElementKind != ListElementKind.NONE;
    }

    /**
     * Declared list type for a given element kind, or {@link #LIST} when unconstrained/none.
     */
    public static NodeDataType forListElementKind(ListElementKind kind) {
        if (kind == null || kind == ListElementKind.NONE || kind == ListElementKind.UNCONSTRAINED) {
            return LIST;
        }
        for (NodeDataType type : values()) {
            if (type.listElementKind == kind) {
                return type;
            }
        }
        return LIST;
    }

    /**
     * Scalar / element type corresponding to a list element kind.
     */
    public static NodeDataType elementTypeForKind(ListElementKind kind) {
        if (kind == null) {
            return ANY;
        }
        return switch (kind) {
            case NONE, UNCONSTRAINED, BLOCK_PLACEMENT -> ANY;
            case INTEGER -> INTEGER;
            case DOUBLE -> DOUBLE;
            case BOOLEAN -> BOOLEAN;
            case STRING -> STRING;
            case BLOCK_POS -> BLOCK_POS;
            case POINT -> POINT;
            case VECTOR -> VECTOR;
            case PLANE -> PLANE;
            case FRAME -> FRAME;
            case PATH -> PATH;
            case TREE_PATH -> TREE_PATH;
            case POLYGON_PROFILE -> POLYGON_PROFILE;
            case REGION -> REGION;
            case BLOCK_INFO -> BLOCK_INFO;
            case PLANT_STRUCTURE -> PLANT_STRUCTURE;
            case L_SYSTEM_RULE -> L_SYSTEM_RULE;
            case PLANT_BLOCK -> PLANT_BLOCK;
        };
    }

    public boolean isCompatible(Object value) {
        if (value == null || this == ANY) {
            return true;
        }

        if (isListType()) {
            if (this == BLOCK_LIST) {
                if (value instanceof BlockPosList blockPosList) {
                    return isCompatibleListElements(blockPosList, ListElementKind.BLOCK_POS);
                }
                if (!(value instanceof List<?> list)) {
                    return false;
                }
                return isCompatibleListElements(list, ListElementKind.BLOCK_POS);
            }
            if (!(value instanceof List<?> list)) {
                return false;
            }
            if (listElementKind == ListElementKind.UNCONSTRAINED) {
                return true;
            }
            return isCompatibleListElements(list, listElementKind);
        }

        return isCompatibleScalar(value);
    }

    private static boolean isCompatibleListElements(Iterable<?> values, ListElementKind kind) {
        for (Object item : values) {
            if (item == null) {
                continue;
            }
            if (!isCompatibleElement(kind, item)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCompatibleScalar(Object value) {
        if (this == DOUBLE && value instanceof Number) {
            return true;
        }

        if (this == INTEGER && value instanceof Integer) {
            return true;
        }

        if (this == FLOAT && value instanceof Float) {
            return true;
        }

        if (this == EXEC) {
            return value instanceof Boolean;
        }

        // VECTOR accepts legacy Vector3 wrappers in addition to Vector3d.
        if (this == VECTOR && value instanceof Vector3) {
            return true;
        }

        if (this == NBT) {
            String simpleName = value.getClass().getSimpleName();
            return simpleName.contains("Tag") || simpleName.contains("NBT");
        }

        if (this == PATH) {
            return value instanceof PathData
                    || value instanceof LineData
                    || value instanceof PolylineData
                    || value instanceof Curve;
        }

        if (this == BLOCK_PALETTE) {
            return value instanceof BlockPaletteData;
        }

        if (this == GEOMETRY && value instanceof GeometryData) {
            return true;
        }
        if (this == SDF && value instanceof SignedDistanceFieldData) {
            return true;
        }
        if (this == SCALAR_FIELD && value instanceof ScalarFieldData) {
            return true;
        }
        if (this == VECTOR_FIELD && value instanceof VectorFieldData) {
            return true;
        }

        return javaClass != null && javaClass.isInstance(value);
    }

    static boolean isCompatibleElement(ListElementKind kind, Object value) {
        if (kind == null) {
            return true;
        }
        return switch (kind) {
            case NONE, UNCONSTRAINED, BLOCK_INFO, BLOCK_PLACEMENT -> true;
            case INTEGER -> value instanceof Integer || value instanceof Long || value instanceof Short
                    || value instanceof Byte;
            case DOUBLE -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case STRING -> value instanceof String;
            case BLOCK_POS -> value instanceof BlockPos;
            case POINT -> value instanceof PointData;
            case VECTOR -> value instanceof Vector3d || value instanceof Vector3;
            case PLANE -> value instanceof PlaneData;
            case FRAME -> value instanceof FrameData;
            case PATH -> value instanceof PathData
                    || value instanceof LineData
                    || value instanceof PolylineData
                    || value instanceof Curve;
            case TREE_PATH -> value instanceof TreePathData;
            case POLYGON_PROFILE -> value instanceof PolygonProfileData;
            case REGION -> value instanceof RegionData;
            case PLANT_STRUCTURE -> value instanceof PlantStructure;
            case L_SYSTEM_RULE -> value instanceof LSystemRule;
            case PLANT_BLOCK -> value instanceof PlantStructure.PlantBlock;
        };
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
        LOGGER.warn("NodeDataType not found for id: {}. Returning ANY.", id);
        return ANY;
    }

}
