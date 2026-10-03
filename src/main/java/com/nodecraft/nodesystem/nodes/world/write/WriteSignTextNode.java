package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.write_sign_text",
    displayName = "Write Sign Text",
    description = "Writes text to the front side of a sign block entity",
    category = "world.write",
    order = 10
)
public class WriteSignTextNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_LINE_1_ID = "input_line_1";
    private static final String INPUT_LINE_2_ID = "input_line_2";
    private static final String INPUT_LINE_3_ID = "input_line_3";
    private static final String INPUT_LINE_4_ID = "input_line_4";
    private static final String INPUT_LINES_LIST_ID = "input_lines_list";
    private static final String INPUT_TEXT_COLOR_ID = "input_text_color";
    private static final String INPUT_GLOWING_ID = "input_glowing";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_IS_SIGN_ID = "output_is_sign";
    private static final String OUTPUT_SIGN_TYPE_ID = "output_sign_type";
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private String[] defaultLines = new String[]{"", "", "", ""};
    private boolean allowFormatting = true;
    private String textColor = "black";
    private boolean glowingProperty = false;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public WriteSignTextNode() {
        super(UUID.randomUUID(), "world.write.write_sign_text");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Sign position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_LINE_1_ID, "Line 1", "First line", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_LINE_2_ID, "Line 2", "Second line", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_LINE_3_ID, "Line 3", "Third line", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_LINE_4_ID, "Line 4", "Fourth line", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_LINES_LIST_ID, "Lines List", "Optional STRING_LIST overriding individual lines (front side)", NodeDataType.STRING_LIST, this));
        addInputPort(new BasePort(INPUT_TEXT_COLOR_ID, "Text Color", "Sign dye color id", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_GLOWING_ID, "Glowing", "Whether sign text should glow", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether sign text was updated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_SIGN_ID, "Is Sign", "Whether the target block entity is a sign", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_SIGN_TYPE_ID, "Sign Type", "Registry id of the sign block", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why sign text was not updated", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(false, false, "", false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(false, false, "", true, "Not triggered");
            return;
        }

        BlockPos pos = WorldWriteUtils.requireBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (pos == null) {
            publish(false, false, "", false, "Invalid coordinate (BLOCK_POS required).");
            return;
        }

        String[] lines;
        try {
            lines = resolveLines();
        } catch (IllegalArgumentException e) {
            publish(false, false, "", false, e.getMessage());
            return;
        }

        String colorId;
        if (OptionalPortDrive.isConnected(this, INPUT_TEXT_COLOR_ID)) {
            Object raw = inputValues.get(INPUT_TEXT_COLOR_ID);
            if (!(raw instanceof String text) || text.isBlank()) {
                publish(false, false, "", false, "Text Color is connected but null or blank.");
                return;
            }
            colorId = text;
        } else {
            Object raw = inputValues.get(INPUT_TEXT_COLOR_ID);
            colorId = raw instanceof String text && !text.isBlank() ? text : textColor;
        }

        Boolean glowing = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_GLOWING_ID, glowingProperty);
        if (glowing == null) {
            publish(false, false, "", false, "Glowing is connected but null or invalid.");
            return;
        }

        if (context == null || context.getWorld() == null) {
            publish(false, false, "", false, "Missing execution world");
            return;
        }
        if (!WorldWriteUtils.isChunkLoaded(context, pos)) {
            publish(false, false, "", false, WorldWriteUtils.UNLOADED_CHUNK_ERROR);
            return;
        }

        BlockSnapshot before = null;
        try {
            var blockEntity = context.getWorld().getBlockEntity(pos);
            if (!(blockEntity instanceof SignBlockEntity sign)) {
                publish(false, false, "", true, "Target block entity is not a sign");
                return;
            }

            before = WorldWriteUndoJournal.captureCurrent(context, pos);
            if (before == null) {
                publish(false, false, "", false, WorldWriteUtils.UNLOADED_CHUNK_ERROR);
                return;
            }
            WorldWriteUndoJournal tx = new WorldWriteUndoJournal(WorldWriteUtils.worldKey(context.getWorld()));

            String signType = Registries.BLOCK.getId(before.state().getBlock()).toString();
            var signText = sign.getFrontText()
                .withColor(DyeColor.byId(colorId.toLowerCase(), DyeColor.BLACK))
                .withGlowing(glowing);

            for (int i = 0; i < 4; i++) {
                signText = signText.withMessage(i, Text.literal(sanitizeLine(lines[i])));
            }

            boolean success = sign.setText(signText, true);
            if (success) {
                tx.recordSuccess(before);
                sign.markDirty();
                context.getWorld().updateListeners(pos, before.state(), before.state(), 3);
                tx.pushIfNeeded(context, recordUndo);
                publish(true, true, signType, true, "");
            } else {
                WorldWriteUndoJournal.restore(context, before);
                publish(false, true, signType, true, "World rejected sign text update");
            }
        } catch (Exception e) {
            if (before != null) {
                WorldWriteUndoJournal.restore(context, before);
            }
            publish(false, false, "", true, "World write failed");
        }
    }

    private String[] resolveLines() {
        String[] lines = new String[4];
        System.arraycopy(defaultLines, 0, lines, 0, 4);

        if (OptionalPortDrive.isConnected(this, INPUT_LINES_LIST_ID)) {
            Object raw = inputValues.get(INPUT_LINES_LIST_ID);
            if (!(raw instanceof Collection<?> collection)) {
                throw new IllegalArgumentException("Lines List is connected but null or invalid.");
            }
            int i = 0;
            for (Object entry : collection) {
                if (i >= 4) {
                    break;
                }
                if (!(entry instanceof String text)) {
                    throw new IllegalArgumentException("Lines List must be STRING_LIST (all String members).");
                }
                lines[i++] = text;
            }
            return lines;
        }

        Object localList = inputValues.get(INPUT_LINES_LIST_ID);
        if (localList instanceof Collection<?> collection) {
            int i = 0;
            for (Object entry : collection) {
                if (i >= 4) {
                    break;
                }
                if (!(entry instanceof String text)) {
                    throw new IllegalArgumentException("Lines List must be STRING_LIST (all String members).");
                }
                lines[i++] = text;
            }
            return lines;
        }

        lines[0] = resolveOptionalLine(INPUT_LINE_1_ID, lines[0]);
        lines[1] = resolveOptionalLine(INPUT_LINE_2_ID, lines[1]);
        lines[2] = resolveOptionalLine(INPUT_LINE_3_ID, lines[2]);
        lines[3] = resolveOptionalLine(INPUT_LINE_4_ID, lines[3]);
        return lines;
    }

    private String resolveOptionalLine(String portId, String fallback) {
        if (OptionalPortDrive.isConnected(this, portId)) {
            Object raw = inputValues.get(portId);
            if (!(raw instanceof String text)) {
                throw new IllegalArgumentException(portId + " is connected but null or invalid.");
            }
            return text;
        }
        Object raw = inputValues.get(portId);
        return raw instanceof String text ? text : fallback;
    }

    private String sanitizeLine(String line) {
        String normalized = line != null ? line : "";
        if (allowFormatting) {
            return normalized;
        }
        return normalized.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private void publish(boolean success, boolean isSign, String signType, boolean valid, String error) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_IS_SIGN_ID, isSign);
        outputValues.put(OUTPUT_SIGN_TYPE_ID, signType == null ? "" : signType);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}
