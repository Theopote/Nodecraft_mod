package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.query.get_entities_in_region",
    displayName = "Get Entities In Region",
    description = "Gets entities inside a region with optional filtering",
    category = "world.query",
    order = 9
)
public class GetEntitiesInRegionNode extends BaseNode {

    @NodeProperty(displayName = "Exclude Players", category = "Filter", order = 1)
    private boolean excludePlayers = false;

    @NodeProperty(displayName = "Include Items", category = "Filter", order = 2)
    private boolean includeItems = true;

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_ENTITY_TYPE_ID = "input_entity_type";
    private static final String INPUT_EXCLUDE_PLAYERS_ID = "input_exclude_players";
    private static final String INPUT_INCLUDE_ITEMS_ID = "input_include_items";

    private static final String OUTPUT_ENTITIES_LIST_ID = "output_entities_list";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_PLAYER_COUNT_ID = "output_player_count";
    private static final String OUTPUT_NEAREST_ENTITY_ID = "output_nearest_entity";
    private static final String OUTPUT_ENTITY_UUIDS_ID = "output_entity_uuids";
    private static final String OUTPUT_ENTITY_TYPE_IDS_ID = "output_entity_type_ids";
    private static final String OUTPUT_ENTITY_POSITIONS_ID = "output_entity_positions";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetEntitiesInRegionNode() {
        super(UUID.randomUUID(), "world.query.get_entities_in_region");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to scan", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_ENTITY_TYPE_ID, "Entity Type", "Optional exact entity type filter", NodeDataType.ENTITY_TYPE, this));
        addInputPort(new BasePort(INPUT_EXCLUDE_PLAYERS_ID, "Exclude Players", "Whether players should be filtered out", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INCLUDE_ITEMS_ID, "Include Items", "Whether dropped item entities should be included", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_ENTITIES_LIST_ID, "Entities List", "Entities found inside the region", NodeDataType.MINECRAFT_ENTITY_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Total number of included entities", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PLAYER_COUNT_ID, "Player Count", "Number of player entities in the result", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_NEAREST_ENTITY_ID, "Nearest Entity", "Nearest included entity to the current player", NodeDataType.MINECRAFT_ENTITY, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_UUIDS_ID, "Entity UUIDs", "UUID strings for included entities", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_TYPE_IDS_ID, "Entity Type IDs", "Registry ids for included entity types", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_POSITIONS_ID, "Entity Positions", "World positions for included entities", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Number of item entities in the result", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the region query was executed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when the region query fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Gets entities inside a region with optional filtering";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Boolean excludePlayersValue = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_EXCLUDE_PLAYERS_ID, excludePlayers);
        if (excludePlayersValue == null) {
            writeFailure("Exclude Players is connected but null or invalid.");
            return;
        }
        Boolean includeItemsValue = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_INCLUDE_ITEMS_ID, includeItems);
        if (includeItemsValue == null) {
            writeFailure("Include Items is connected but null or invalid.");
            return;
        }

        String entityTypeFilter = null;
        if (OptionalPortDrive.isConnected(this, INPUT_ENTITY_TYPE_ID)) {
            Object entityTypeObj = inputValues.get(INPUT_ENTITY_TYPE_ID);
            if (!(entityTypeObj instanceof String value) || value.isBlank()) {
                writeFailure("Entity Type is connected but null or invalid.");
                return;
            }
            entityTypeFilter = value;
        } else if (inputValues.get(INPUT_ENTITY_TYPE_ID) instanceof String localType && !localType.isBlank()) {
            entityTypeFilter = localType;
        }

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            writeFailure("Region input must be a complete region.");
            return;
        }

        Box box = region.toBox();
        if (box == null) {
            writeFailure("Region could not be converted to a bounding box.");
            return;
        }

        if (context == null || context.getWorld() == null) {
            writeFailure("Execution context or world is missing.");
            return;
        }

        List<Entity> entitiesList = new ArrayList<>();
        int count = 0;
        int playerCount = 0;
        int itemCount = 0;
        Entity nearestEntity = null;
        double nearestDistance = Double.MAX_VALUE;
        List<String> uuids = new ArrayList<>();
        List<String> typeIds = new ArrayList<>();
        List<PointData> positions = new ArrayList<>();

        List<Entity> entities = new ArrayList<>(context.getWorld().getOtherEntities(null, box));
        if (context.getPlayer() != null
            && box.contains(context.getPlayer().getX(), context.getPlayer().getY(), context.getPlayer().getZ())) {
            entities.add(context.getPlayer());
        }

        for (Entity entity : entities) {
            boolean isPlayer = entity instanceof PlayerEntity;
            boolean isItem = entity instanceof ItemEntity;

            if (excludePlayersValue && isPlayer) {
                continue;
            }
            if (!includeItemsValue && isItem) {
                continue;
            }

            if (entityTypeFilter != null) {
                String entityTypeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
                if (!entityTypeFilter.equals(entityTypeId)) {
                    continue;
                }
            }

            entitiesList.add(entity);
            count++;
            if (isPlayer) {
                playerCount++;
            }
            if (isItem) {
                itemCount++;
            }

            uuids.add(entity.getUuidAsString());
            typeIds.add(Registries.ENTITY_TYPE.getId(entity.getType()).toString());
            positions.add(new PointData(entity.getX(), entity.getY(), entity.getZ()));

            if (context.getPlayer() != null) {
                double distance = context.getPlayer().squaredDistanceTo(entity);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestEntity = entity;
                }
            } else if (nearestEntity == null) {
                nearestEntity = entity;
            }
        }

        outputValues.put(OUTPUT_ENTITIES_LIST_ID, entitiesList);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_PLAYER_COUNT_ID, playerCount);
        outputValues.put(OUTPUT_NEAREST_ENTITY_ID, nearestEntity);
        outputValues.put(OUTPUT_ENTITY_UUIDS_ID, uuids);
        outputValues.put(OUTPUT_ENTITY_TYPE_IDS_ID, typeIds);
        outputValues.put(OUTPUT_ENTITY_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_ITEM_COUNT_ID, itemCount);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeFailure(String error) {
        outputValues.put(OUTPUT_ENTITIES_LIST_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_PLAYER_COUNT_ID, 0);
        outputValues.put(OUTPUT_NEAREST_ENTITY_ID, null);
        outputValues.put(OUTPUT_ENTITY_UUIDS_ID, List.of());
        outputValues.put(OUTPUT_ENTITY_TYPE_IDS_ID, List.of());
        outputValues.put(OUTPUT_ENTITY_POSITIONS_ID, List.of());
        outputValues.put(OUTPUT_ITEM_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isExcludePlayers() {
        return excludePlayers;
    }

    public void setExcludePlayers(boolean excludePlayers) {
        this.excludePlayers = excludePlayers;
        markDirty();
    }

    public boolean isIncludeItems() {
        return includeItems;
    }

    public void setIncludeItems(boolean includeItems) {
        this.includeItems = includeItems;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("excludePlayers", excludePlayers);
        state.put("includeItems", includeItems);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object excludePlayersValue = map.get("excludePlayers");
        if (excludePlayersValue instanceof Boolean value) {
            excludePlayers = value;
        }
        Object includeItemsValue = map.get("includeItems");
        if (includeItemsValue instanceof Boolean value) {
            includeItems = value;
        }
    }
}
