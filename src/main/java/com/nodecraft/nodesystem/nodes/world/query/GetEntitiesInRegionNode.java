package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.query.get_entities_in_region",
    displayName = "Get Entities In Region",
    description = "Gets entities inside a region with optional filtering",
    category = "world.query"
)
public class GetEntitiesInRegionNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_ENTITY_TYPE_ID = "input_entity_type";
    private static final String INPUT_EXCLUDE_PLAYERS_ID = "input_exclude_players";
    private static final String INPUT_INCLUDE_ITEMS_ID = "input_include_items";

    private static final String OUTPUT_ENTITIES_LIST_ID = "output_entities_list";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_PLAYER_COUNT_ID = "output_player_count";
    private static final String OUTPUT_NEAREST_ENTITY_ID = "output_nearest_entity";

    private boolean excludePlayers = false;
    private boolean includeItems = true;

    public GetEntitiesInRegionNode() {
        super(UUID.randomUUID(), "world.query.get_entities_in_region");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to scan", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_ENTITY_TYPE_ID, "Entity Type", "Optional exact entity type filter", NodeDataType.ENTITY_TYPE, this));
        addInputPort(new BasePort(INPUT_EXCLUDE_PLAYERS_ID, "Exclude Players", "Whether players should be filtered out", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INCLUDE_ITEMS_ID, "Include Items", "Whether dropped item entities should be included", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_ENTITIES_LIST_ID, "Entities List", "Entities found inside the region", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Total number of included entities", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PLAYER_COUNT_ID, "Player Count", "Number of player entities in the result", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_NEAREST_ENTITY_ID, "Nearest Entity", "Nearest included entity to the current player", NodeDataType.MINECRAFT_ENTITY, this));
    }

    @Override
    public String getDescription() {
        return "Gets entities inside a region with optional filtering";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Object> entitiesList = new ArrayList<>();
        int count = 0;
        int playerCount = 0;
        Entity nearestEntity = null;
        double nearestDistance = Double.MAX_VALUE;

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object entityTypeObj = inputValues.get(INPUT_ENTITY_TYPE_ID);

        boolean excludePlayersValue = inputValues.get(INPUT_EXCLUDE_PLAYERS_ID) instanceof Boolean value ? value : excludePlayers;
        boolean includeItemsValue = inputValues.get(INPUT_INCLUDE_ITEMS_ID) instanceof Boolean value ? value : includeItems;
        String entityTypeFilter = entityTypeObj instanceof String value && !value.isBlank() ? value : null;

        if (context != null && context.getWorld() != null && regionObj instanceof RegionData region && region.isComplete()) {
            Box box = region.toBox();
            if (box != null) {
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
            }
        }

        outputValues.put(OUTPUT_ENTITIES_LIST_ID, entitiesList);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_PLAYER_COUNT_ID, playerCount);
        outputValues.put(OUTPUT_NEAREST_ENTITY_ID, nearestEntity);
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
}
