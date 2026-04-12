package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.visual.SelectionVisualFeedback;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "world.selection.selected_entity",
    displayName = "Selected Entity",
    description = "Gets information about the entity selected by the player.",
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

    private static final String OUTPUT_ENTITY_ID = "output_entity_id";
    private static final String OUTPUT_ENTITY_TYPE = "output_entity_type";
    private static final String OUTPUT_ENTITY_POSITION = "output_entity_position";
    private static final String OUTPUT_ENTITY_X = "output_entity_x";
    private static final String OUTPUT_ENTITY_Y = "output_entity_y";
    private static final String OUTPUT_ENTITY_Z = "output_entity_z";
    private static final String OUTPUT_HAS_ENTITY = "output_has_entity";

    private volatile String pickedEntityId;
    private volatile String pickedEntityType = "minecraft:pig";
    private volatile Coordinate pickedEntityPosition;
    private volatile Vec3d pickedEntityExactPosition;
    private volatile boolean hasPickedEntity = false;

    public SelectedEntityNode() {
        super(UUID.randomUUID(), "world.selection.selected_entity");

        addOutputPort(new BasePort(OUTPUT_ENTITY_ID, "Entity ID", "The unique identifier of the entity", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_TYPE, "Entity Type", "The type of the entity", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_POSITION, "Entity Position", "The block coordinates of the entity", NodeDataType.COORDINATE, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_X, "Entity X", "The X coordinate of the entity", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_Y, "Entity Y", "The Y coordinate of the entity", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_Z, "Entity Z", "The Z coordinate of the entity", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HAS_ENTITY, "Has Entity", "Whether a valid entity is selected", NodeDataType.BOOLEAN, this));

        resetOutputs();
    }

    @Override
    public String getDescription() {
        return "Gets information about the entity selected by the player.";
    }

    @Override
    public String getDisplayName() {
        return "Selected Entity";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) {
            resetOutputs();
            return;
        }
        updateOutputsWithPickedEntity();
    }

    private void updateOutputsWithPickedEntity() {
        outputValues.put(OUTPUT_HAS_ENTITY, hasPickedEntity);
        outputValues.put(OUTPUT_ENTITY_ID, pickedEntityId != null ? pickedEntityId : "");
        outputValues.put(OUTPUT_ENTITY_TYPE, pickedEntityType);

        if (pickedEntityPosition != null) {
            outputValues.put(OUTPUT_ENTITY_POSITION, pickedEntityPosition);
            outputValues.put(OUTPUT_ENTITY_X, pickedEntityPosition.getX());
            outputValues.put(OUTPUT_ENTITY_Y, pickedEntityPosition.getY());
            outputValues.put(OUTPUT_ENTITY_Z, pickedEntityPosition.getZ());
        } else {
            outputValues.put(OUTPUT_ENTITY_POSITION, new Coordinate(0, 0, 0));
            outputValues.put(OUTPUT_ENTITY_X, 0);
            outputValues.put(OUTPUT_ENTITY_Y, 0);
            outputValues.put(OUTPUT_ENTITY_Z, 0);
        }
    }

    private void resetOutputs() {
        outputValues.put(OUTPUT_HAS_ENTITY, false);
        outputValues.put(OUTPUT_ENTITY_ID, "");
        outputValues.put(OUTPUT_ENTITY_TYPE, "minecraft:pig");
        outputValues.put(OUTPUT_ENTITY_POSITION, new Coordinate(0, 0, 0));
        outputValues.put(OUTPUT_ENTITY_X, 0);
        outputValues.put(OUTPUT_ENTITY_Y, 0);
        outputValues.put(OUTPUT_ENTITY_Z, 0);
    }

    @Override
    public void onEntityPicked(String entityId, String entityType, Coordinate position) {
        this.pickedEntityId = entityId;
        this.pickedEntityType = entityType;
        this.pickedEntityPosition = position;
        this.pickedEntityExactPosition = new Vec3d(position.getX(), position.getY(), position.getZ());
        this.hasPickedEntity = true;

        markDirty();

        if (showHighlight) {
            SelectionVisualFeedback.getInstance().showEntitySelection(
                getId().toString(),
                pickedEntityExactPosition,
                SelectionVisualFeedback.EntitySelectionState.SELECTED
            );
        }
    }

    @Override
    public void onInteractionCancelled() {
        // No-op.
    }

    public void clearPickedEntity() {
        hasPickedEntity = false;
        pickedEntityId = null;
        pickedEntityType = "minecraft:pig";
        pickedEntityPosition = null;
        pickedEntityExactPosition = null;

        SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
        markDirty();
    }

    @Override
    protected float calculateUIHeight() {
        float frame = ImGui.getFrameHeight();
        float small = getSmallPadding();

        float height = small;
        height += frame;
        if (hasPickedEntity) {
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

            if (hasPickedEntity) {
                addVerticalSpacing(getSmallPadding(), zoom);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                if (ImGui.button("Clear Selection##clearEntity", availableWidth, buttonHeight)) {
                    clearPickedEntity();
                    changed = true;
                }
            }

            addVerticalSpacing(getSmallPadding(), zoom);
        } catch (Exception e) {
            System.err.println("SelectedEntityNode UI render failed: " + e.getMessage());
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
            } else if (hasPickedEntity && pickedEntityExactPosition != null) {
                SelectionVisualFeedback.getInstance().showEntitySelection(
                    getId().toString(),
                    pickedEntityExactPosition,
                    SelectionVisualFeedback.EntitySelectionState.SELECTED
                );
            }

            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("maxDistance", maxDistance);
        state.put("showHighlight", showHighlight);

        if (hasPickedEntity && pickedEntityPosition != null) {
            Map<String, Object> pickedEntity = new HashMap<>();
            pickedEntity.put("entityId", pickedEntityId);
            pickedEntity.put("entityType", pickedEntityType);
            pickedEntity.put("x", pickedEntityPosition.getX());
            pickedEntity.put("y", pickedEntityPosition.getY());
            pickedEntity.put("z", pickedEntityPosition.getZ());
            state.put("pickedEntity", pickedEntity);
        }

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

        Object pickedEntityObj = stateMap.get("pickedEntity");
        if (pickedEntityObj instanceof Map<?, ?> pickedEntityMap) {
            String entityId = pickedEntityMap.get("entityId") instanceof String value ? value : null;
            String entityType = pickedEntityMap.get("entityType") instanceof String value ? value : null;
            Integer x = pickedEntityMap.get("x") instanceof Number value ? value.intValue() : null;
            Integer y = pickedEntityMap.get("y") instanceof Number value ? value.intValue() : null;
            Integer z = pickedEntityMap.get("z") instanceof Number value ? value.intValue() : null;

            if (entityType != null && x != null && y != null && z != null) {
                this.pickedEntityId = entityId;
                this.pickedEntityType = entityType;
                this.pickedEntityPosition = new Coordinate(x, y, z);
                this.pickedEntityExactPosition = new Vec3d(x, y, z);
                this.hasPickedEntity = true;
            }
        }

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
        if (hasPickedEntity && showHighlight && pickedEntityExactPosition != null) {
            SelectionVisualFeedback.getInstance().showEntitySelection(
                getId().toString(),
                pickedEntityExactPosition,
                SelectionVisualFeedback.EntitySelectionState.SELECTED
            );
        }
    }

    public void onNodeDeselected() {
        // Keep highlight active as part of the node behavior.
    }
}
