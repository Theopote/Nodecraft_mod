package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.visual.SelectionVisualFeedback;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.CONTEXT_READ,
    id = "world.selection.selected_entity",
    displayName = "Selected Entity",
    description = "Editor selection source for a picked entity (UUID, type, exact POINT, block cell, live Entity).",
    category = "world.selection",
    order = 7
)
public class SelectedEntityNode extends BaseCustomUINode implements NodeEditorInteractionManager.IEntityPickerCallback {

    @NodeProperty(
        displayName = "Max Distance",
        category = "Picking",
        order = 1,
        description = "Maximum distance used when picking an entity."
    )
    private float maxDistance = 100.0f;

    @NodeProperty(
        displayName = "Show Highlight",
        category = "Picking",
        order = 2,
        description = "Whether the selected entity should show visual feedback in the world."
    )
    private boolean showHighlight = true;

    private static final String OUTPUT_ENTITY_UUID = "output_entity_uuid";
    private static final String OUTPUT_ENTITY_TYPE = "output_entity_type";
    private static final String OUTPUT_ENTITY = "output_entity";
    private static final String OUTPUT_ENTITY_POSITION = "output_entity_position";
    private static final String OUTPUT_EXACT_POSITION = "output_exact_position";
    private static final String OUTPUT_DISTANCE_TO_PLAYER = "output_distance_to_player";
    private static final String OUTPUT_HAS_ENTITY = "output_has_entity";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    /** Selection identity only — position/entity are resolved live each evaluation. */
    private volatile @Nullable String pickedEntityUuid;
    private volatile String lastKnownType = "";

    public SelectedEntityNode() {
        super(UUID.randomUUID(), "world.selection.selected_entity");

        addOutputPort(new BasePort(OUTPUT_ENTITY_UUID, "UUID", "Entity UUID string", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_TYPE, "Entity Type", "Entity type registry id", NodeDataType.ENTITY_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY, "Entity", "Live Minecraft entity when available", NodeDataType.MINECRAFT_ENTITY, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_POSITION, "Block Position", "Containing block cell of the exact position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_EXACT_POSITION, "Exact Position", "Continuous entity position", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_TO_PLAYER, "Distance To Player", "Distance from the current player to the selected entity", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_HAS_ENTITY, "Has Entity", "Whether a valid entity is selected", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether selection outputs are valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when selection is invalid", NodeDataType.STRING, this));

        resetOutputs();
    }

    @Override
    public String getDescription() {
        return "Editor selection source for a picked entity (UUID, type, exact POINT, block cell, live Entity).";
    }

    @Override
    public String getDisplayName() {
        return "Selected Entity";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutputsFromLiveEntity(context);
    }

    private void updateOutputsFromLiveEntity(@Nullable ExecutionContext context) {
        UUID uuid = parseUuid(pickedEntityUuid);
        if (uuid == null) {
            resetOutputs();
            return;
        }

        Entity entity = resolveLiveEntity(context, uuid);
        if (entity == null) {
            // Lost selection is not a graph error.
            publishAbsent(uuid.toString(), lastKnownType);
            SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
            return;
        }

        Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        Vector3d exact = new Vector3d(pos.x, pos.y, pos.z);
        BlockPos blockPos = BlockSpace.pointToBlockFloor(exact);
        String typeId = resolveEntityTypeId(entity);
        lastKnownType = typeId;

        outputValues.put(OUTPUT_HAS_ENTITY, true);
        outputValues.put(OUTPUT_ENTITY_UUID, uuid.toString());
        outputValues.put(OUTPUT_ENTITY_TYPE, typeId);
        outputValues.put(OUTPUT_ENTITY, entity);
        outputValues.put(OUTPUT_ENTITY_POSITION, blockPos);
        outputValues.put(OUTPUT_EXACT_POSITION, new PointData(exact.x, exact.y, exact.z));
        outputValues.put(OUTPUT_DISTANCE_TO_PLAYER, distanceToPlayer(context, pos));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");

        if (showHighlight) {
            SelectionVisualFeedback.getInstance().showEntitySelection(
                getId().toString(),
                pos,
                SelectionVisualFeedback.EntitySelectionState.SELECTED
            );
        }
    }

    private void publishAbsent(String uuidText, String typeHint) {
        outputValues.put(OUTPUT_HAS_ENTITY, false);
        outputValues.put(OUTPUT_ENTITY_UUID, uuidText != null ? uuidText : "");
        outputValues.put(OUTPUT_ENTITY_TYPE, typeHint != null ? typeHint : "");
        outputValues.put(OUTPUT_ENTITY, null);
        outputValues.put(OUTPUT_ENTITY_POSITION, null);
        outputValues.put(OUTPUT_EXACT_POSITION, null);
        outputValues.put(OUTPUT_DISTANCE_TO_PLAYER, null);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void resetOutputs() {
        publishAbsent("", "");
    }

    static @Nullable Entity resolveLiveEntity(@Nullable ExecutionContext context, UUID uuid) {
        World contextWorld = context != null ? context.getWorld() : null;
        Entity fromContext = lookupEntity(contextWorld, uuid);
        if (isAcceptableEntity(fromContext, contextWorld)) {
            return fromContext;
        }

        // Editor sessions may evaluate without a server world; last-resort client world.
        World clientWorld = null;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                clientWorld = client.world;
            }
        } catch (Throwable ignored) {
            // Headless / dedicated paths.
        }
        if (clientWorld != null && clientWorld != contextWorld) {
            Entity fromClient = lookupEntity(clientWorld, uuid);
            if (isAcceptableEntity(fromClient, contextWorld != null ? contextWorld : clientWorld)) {
                return fromClient;
            }
        }
        return null;
    }

    private static @Nullable Entity lookupEntity(@Nullable World world, UUID uuid) {
        if (world == null) {
            return null;
        }
        try {
            return world.getEntity(uuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isAcceptableEntity(@Nullable Entity entity, @Nullable World expectedWorld) {
        if (entity == null) {
            return false;
        }
        if (!entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        World entityWorld = entity.getEntityWorld();
        return expectedWorld == null || entityWorld == null || entityWorld == expectedWorld;
    }

    private static @Nullable UUID parseUuid(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String resolveEntityTypeId(Entity entity) {
        try {
            Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
            return id.toString();
        } catch (Throwable ignored) {
            return entity.getType().toString();
        }
    }

    private static @Nullable Double distanceToPlayer(@Nullable ExecutionContext context, Vec3d entityPos) {
        if (context != null && context.getPlayer() != null) {
            var player = context.getPlayer();
            Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            return playerPos.distanceTo(entityPos);
        }
        // No ExecutionContext player — do not fall back to MinecraftClient.player.
        return null;
    }

    @Override
    public void onEntityPicked(String entityUuid, String entityType, Vec3d exactPosition, @Nullable Entity entity) {
        this.pickedEntityUuid = entityUuid;
        this.lastKnownType = entityType != null ? entityType : "";
        markDirty();

        if (showHighlight && exactPosition != null) {
            SelectionVisualFeedback.getInstance().showEntitySelection(
                getId().toString(),
                exactPosition,
                SelectionVisualFeedback.EntitySelectionState.SELECTED
            );
        }
    }

    @Override
    public void onInteractionCancelled() {
        // No-op.
    }

    @Override
    public float getEntityPickMaxDistance() {
        return maxDistance;
    }

    public void clearPickedEntity() {
        pickedEntityUuid = null;
        lastKnownType = "";
        SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
        markDirty();
    }

    /** Package-visible for contracts: whether a UUID identity is currently held. */
    public boolean hasPickedUuid() {
        return parseUuid(pickedEntityUuid) != null;
    }

    public @Nullable String getPickedEntityUuid() {
        return pickedEntityUuid;
    }

    @Override
    protected float calculateUIHeight() {
        float frame = ImGui.getFrameHeight();
        float small = getSmallPadding();

        float height = small;
        height += frame;
        if (hasPickedUuid()) {
            height += small;
            height += frame;
        }
        height += small;
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float labelWidth = Math.max(
            ImGui.calcTextSize("Pick Entity").x,
            Math.max(
                ImGui.calcTextSize("Cancel Picking").x,
                ImGui.calcTextSize("Clear Selection").x
            )
        );
        return Math.max(160.0f, labelWidth + 20.0f);
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        boolean changed = false;

        try {
            float edgeMargin = toPixels(getSmallPadding(), zoom);
            float availableWidth = Math.max(0.0f, toPixelsExact(width, zoom) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            addVerticalSpacing(getSmallPadding(), zoom);

            NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
            boolean isCurrentlyPicking = interactionManager.isCurrentInteractionNode(getId().toString());
            String pickButtonText = isCurrentlyPicking ? "Cancel Picking" : "Pick Entity";
            float buttonHeight = ImGui.getFrameHeight();

            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            if (ImGui.button(pickButtonText + "##pickEntity", availableWidth, buttonHeight)) {
                if (isCurrentlyPicking) {
                    interactionManager.cancelCurrentInteraction();
                } else {
                    if (!interactionManager.isInEditorMode()) {
                        interactionManager.enterEditorMode();
                    }
                    interactionManager.requestEntityPicking(getId().toString(), this);
                }
                changed = true;
            }

            if (hasPickedUuid()) {
                addVerticalSpacing(getSmallPadding(), zoom);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                if (ImGui.button("Clear Selection##clearEntity", availableWidth, buttonHeight)) {
                    clearPickedEntity();
                    changed = true;
                }
            }

            addVerticalSpacing(getSmallPadding(), zoom);
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("SelectedEntityNode UI render failed: {}", e.getMessage(), e);
        }

        return changed;
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(float maxDistance) {
        if (this.maxDistance != maxDistance) {
            this.maxDistance = Math.max(10.0f, Math.min(200.0f, maxDistance));
            markDirty();
        }
    }

    public boolean isShowHighlight() {
        return showHighlight;
    }

    public void setShowHighlight(boolean showHighlight) {
        if (this.showHighlight != showHighlight) {
            this.showHighlight = showHighlight;

            if (!showHighlight) {
                SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
            }
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("maxDistance", maxDistance);
        state.put("showHighlight", showHighlight);
        // Transient pick identity is not persisted.
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }

        if (stateMap.get("maxDistance") instanceof Number maxDist) {
            setMaxDistance(maxDist.floatValue());
        }

        if (stateMap.get("showHighlight") instanceof Boolean highlight) {
            setShowHighlight(highlight);
        }
        // Ignore legacy pickedEntity / UUID payloads — selection is session-only.
        pickedEntityUuid = null;
        lastKnownType = "";
        markDirty();
    }

    public void onNodeRemoved() {
        SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());

        NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
        if (interactionManager.isCurrentInteractionNode(getId().toString())) {
            interactionManager.cancelCurrentInteraction();
        }
    }

    public void onNodeSelected() {
        // Highlight refreshed on next processNode when entity still resolves.
    }

    public void onNodeDeselected() {
        // Keep highlight active as part of the node behavior.
    }
}
