package com.nodecraft.nodesystem.nodes.output.debug;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.DebugValueFormatter;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Print To Chat: sends a budgeted debug message to the player chat on an EXEC pulse.
 */
@NodeInfo(
    effect = NodeEffect.UI_EFFECT,
    id = "output.debug.print_to_chat",
    displayName = "Print To Chat",
    description = "Prints the connected value to Minecraft chat on an EXEC trigger.",
    category = "output.debug",
    order = 1
)
public class PrintToChatNode extends BaseCustomUINode {

    private static final int MAX_MESSAGE_CHARS = 512;

    @NodeProperty(displayName = "Prefix", category = "Format", order = 1)
    private String prefix = "[Debug] ";

    @NodeProperty(displayName = "Include Node Name", category = "Format", order = 2)
    private boolean includeNodeName = true;

    @NodeProperty(displayName = "Include Data Type", category = "Format", order = 3)
    private boolean includeDataType = true;

    @NodeProperty(displayName = "Auto Format", category = "Format", order = 4)
    private boolean autoFormat = true;

    @NodeProperty(displayName = "Text Color", category = "Format", order = 5)
    private String textColor = "gold";

    private static final String INPUT_DATA_ID = "input_data";
    private static final String INPUT_PREFIX_ID = "input_prefix";
    private static final String INPUT_COLOR_ID = "input_color";
    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_MESSAGE_ID = "output_message";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public PrintToChatNode() {
        super(UUID.randomUUID(), "output.debug.print_to_chat");
        addInputPort(new BasePort(INPUT_DATA_ID, "Data", "Value to print", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_PREFIX_ID, "Prefix", "Message prefix", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_COLOR_ID, "Color", "Text color name", NodeDataType.STRING, this));
        addInputPort(new BasePort(
            INPUT_TRIGGER_ID,
            "Trigger",
            "EXEC pulse to print",
            NodeDataType.EXEC,
            this,
            false,
            false
        ));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the message was sent", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_MESSAGE_ID, "Message", "Message that was printed (or would be)", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why printing failed (empty when idle or success)", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        String message = "";
        String error = "";

        if (!Boolean.TRUE.equals(inputValues.get(INPUT_TRIGGER_ID))) {
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_MESSAGE_ID, "");
            outputValues.put(OUTPUT_ERROR_ID, "");
            return;
        }

        try {
            String pfx;
            if (OptionalPortDrive.isConnected(this, INPUT_PREFIX_ID)) {
                Object raw = inputValues.get(INPUT_PREFIX_ID);
                if (!(raw instanceof String)) {
                    error = "Prefix must be STRING";
                    outputValues.put(OUTPUT_SUCCESS_ID, false);
                    outputValues.put(OUTPUT_MESSAGE_ID, "");
                    outputValues.put(OUTPUT_ERROR_ID, error);
                    return;
                }
                pfx = (String) raw;
            } else {
                pfx = this.prefix != null ? this.prefix : "";
            }

            String color;
            if (OptionalPortDrive.isConnected(this, INPUT_COLOR_ID)) {
                Object rawColor = inputValues.get(INPUT_COLOR_ID);
                if (!(rawColor instanceof String)) {
                    error = "Color must be STRING";
                    outputValues.put(OUTPUT_SUCCESS_ID, false);
                    outputValues.put(OUTPUT_MESSAGE_ID, "");
                    outputValues.put(OUTPUT_ERROR_ID, error);
                    return;
                }
                color = (String) rawColor;
            } else {
                color = this.textColor;
            }

            Object dataObj = inputValues.get(INPUT_DATA_ID);
            StringBuilder sb = new StringBuilder();
            appendBudgeted(sb, pfx == null ? "" : pfx, MAX_MESSAGE_CHARS);
            if (includeNodeName && sb.length() < MAX_MESSAGE_CHARS) {
                appendBudgeted(sb, getDisplayName() + ": ", MAX_MESSAGE_CHARS);
            }

            DebugValueFormatter.FormatOptions formatOptions = autoFormat
                ? DebugValueFormatter.CHAT_OPTIONS
                : new DebugValueFormatter.FormatOptions(256, 8, 2, false);
            DebugValueFormatter.FormatResult formatted = DebugValueFormatter.format(dataObj, formatOptions);

            if (includeDataType && dataObj != null && sb.length() < MAX_MESSAGE_CHARS) {
                appendBudgeted(sb, "(" + formatted.typeLabel() + ") ", MAX_MESSAGE_CHARS);
            }
            if (sb.length() < MAX_MESSAGE_CHARS) {
                appendBudgeted(sb, formatted.text(), MAX_MESSAGE_CHARS);
            }
            message = sb.toString();

            Formatting formatting = resolveFormatting(color);
            if (color != null && !color.isBlank() && formatting == null) {
                error = "Invalid color: " + color;
                outputValues.put(OUTPUT_SUCCESS_ID, false);
                outputValues.put(OUTPUT_MESSAGE_ID, message);
                outputValues.put(OUTPUT_ERROR_ID, error);
                return;
            }

            if (context == null || context.getPlayer() == null) {
                error = "No player in execution context";
                outputValues.put(OUTPUT_SUCCESS_ID, false);
                outputValues.put(OUTPUT_MESSAGE_ID, message);
                outputValues.put(OUTPUT_ERROR_ID, error);
                return;
            }

            context.getPlayer().sendMessage(toChatText(message, formatting), false);
            success = true;
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            com.nodecraft.core.NodeCraft.LOGGER.warn("Error printing to chat", e);
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_MESSAGE_ID, message);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private static void appendBudgeted(StringBuilder sb, String segment, int maxChars) {
        if (segment == null || segment.isEmpty() || sb.length() >= maxChars) {
            return;
        }
        int remaining = maxChars - sb.length();
        if (segment.length() <= remaining) {
            sb.append(segment);
        } else {
            if (remaining > 3) {
                sb.append(segment, 0, remaining - 3).append("...");
            } else {
                sb.append("...");
            }
        }
    }

    @Override
    protected float calculateUIHeight() {
        return 0f;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return false;
    }

    private Text toChatText(String message, @Nullable Formatting formatting) {
        MutableText text = Text.literal(message);
        return formatting != null ? text.formatted(formatting) : text;
    }

    private @Nullable Formatting resolveFormatting(@Nullable String colorName) {
        if (colorName == null) {
            return null;
        }
        String normalized = colorName.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return Formatting.byName(normalized);
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String v) {
        if (v != null && !v.equals(this.prefix)) {
            this.prefix = v;
            markDirty();
        }
    }

    public boolean isIncludeNodeName() {
        return includeNodeName;
    }

    public void setIncludeNodeName(boolean v) {
        if (this.includeNodeName != v) {
            this.includeNodeName = v;
            markDirty();
        }
    }

    public boolean isIncludeDataType() {
        return includeDataType;
    }

    public void setIncludeDataType(boolean v) {
        if (this.includeDataType != v) {
            this.includeDataType = v;
            markDirty();
        }
    }

    public boolean isAutoFormat() {
        return autoFormat;
    }

    public void setAutoFormat(boolean v) {
        if (this.autoFormat != v) {
            this.autoFormat = v;
            markDirty();
        }
    }

    public String getTextColor() {
        return textColor;
    }

    public void setTextColor(String v) {
        if (v != null) {
            this.textColor = v;
            markDirty();
        }
    }

    @Override
    public @Nullable Object getNodeState() {
        Map<String, Object> s = new HashMap<>();
        s.put("prefix", prefix);
        s.put("includeNodeName", includeNodeName);
        s.put("includeDataType", includeDataType);
        s.put("autoFormat", autoFormat);
        s.put("textColor", textColor);
        return s;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Map<?, ?> m) {
            if (m.get("prefix") instanceof String s) {
                setPrefix(s);
            }
            if (m.get("includeNodeName") instanceof Boolean b) {
                setIncludeNodeName(b);
            }
            if (m.get("includeDataType") instanceof Boolean b) {
                setIncludeDataType(b);
            }
            if (m.get("autoFormat") instanceof Boolean b) {
                setAutoFormat(b);
            }
            if (m.get("textColor") instanceof String s) {
                setTextColor(s);
            }
        }
    }
}
