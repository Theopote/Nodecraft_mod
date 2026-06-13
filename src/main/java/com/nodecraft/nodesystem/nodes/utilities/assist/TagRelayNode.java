package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "utilities.assist.tag_relay",
    displayName = "Tag Relay",
    description = "Passes a signal through while adding a visual semantic tag.",
    category = "utilities.assist"
)
public class TagRelayNode extends BaseNode {

    private static final String INPUT_SIGNAL_ID = "input_signal";
    private static final String OUTPUT_SIGNAL_ID = "output_signal";

    private static final String DEFAULT_COLOR_HEX = "#8BC34A";
    private static final int MAX_SHORT_LABEL_LENGTH = 10;

    private static final Map<String, String> NAMED_COLOR_RULES = new LinkedHashMap<>();

    static {
        NAMED_COLOR_RULES.put("danger", "#E53935");
        NAMED_COLOR_RULES.put("error", "#E53935");
        NAMED_COLOR_RULES.put("warn", "#FFB300");
        NAMED_COLOR_RULES.put("warning", "#FFB300");
        NAMED_COLOR_RULES.put("ok", "#43A047");
        NAMED_COLOR_RULES.put("success", "#43A047");
        NAMED_COLOR_RULES.put("io", "#1E88E5");
        NAMED_COLOR_RULES.put("input", "#1E88E5");
        NAMED_COLOR_RULES.put("math", "#00897B");
        NAMED_COLOR_RULES.put("flow", "#00ACC1");
        NAMED_COLOR_RULES.put("debug", "#6D4C41");
        NAMED_COLOR_RULES.put("note", "#7CB342");
    }

    @NodeProperty(displayName = "Tag", category = "Relay", order = 1)
    private String tag = "Tag";

    @NodeProperty(displayName = "Color", category = "Relay", order = 2)
    private String color = DEFAULT_COLOR_HEX;

    public TagRelayNode() {
        super(UUID.randomUUID(), "utilities.assist.tag_relay");

        addInputPort(new BasePort(INPUT_SIGNAL_ID, "Input", "Signal to pass through", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_SIGNAL_ID, "Output", "Passed-through signal", NodeDataType.ANY, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        outputValues.put(OUTPUT_SIGNAL_ID, inputValues.get(INPUT_SIGNAL_ID));
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        String safe = tag == null ? "" : tag;
        if (!safe.equals(this.tag)) {
            this.tag = safe;
            markDirty();
        }
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        String safe = color == null ? DEFAULT_COLOR_HEX : color.trim();
        if (!safe.equals(this.color)) {
            this.color = safe;
            markDirty();
        }
    }

    public String getShortTagLabel() {
        String normalized = tag == null ? "" : tag.trim();
        if (normalized.isEmpty()) {
            return "TAG";
        }
        if (normalized.length() <= MAX_SHORT_LABEL_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SHORT_LABEL_LENGTH);
    }

    public String getResolvedColorHex() {
        String colorToken = color == null ? "" : color.trim();
        if (colorToken.isEmpty() || "auto".equalsIgnoreCase(colorToken)) {
            return mapColorByTag(tag);
        }
        if (isValidHexColor(colorToken)) {
            return normalizeHexColor(colorToken);
        }
        String mappedNamedColor = mapColorByKeyword(colorToken.toLowerCase());
        return mappedNamedColor != null ? mappedNamedColor : mapColorByTag(tag);
    }

    private static String mapColorByTag(String tagText) {
        if (tagText == null || tagText.isBlank()) {
            return DEFAULT_COLOR_HEX;
        }
        String mapped = mapColorByKeyword(tagText.toLowerCase());
        return mapped != null ? mapped : DEFAULT_COLOR_HEX;
    }

    private static @Nullable String mapColorByKeyword(String lowerText) {
        for (Map.Entry<String, String> entry : NAMED_COLOR_RULES.entrySet()) {
            if (lowerText.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean isValidHexColor(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("#")) {
            return false;
        }
        int length = trimmed.length();
        if (length != 7 && length != 9) {
            return false;
        }
        for (int i = 1; i < length; i++) {
            char c = trimmed.charAt(i);
            boolean isHexDigit = (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
            if (!isHexDigit) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeHexColor(String value) {
        return value.trim().toUpperCase();
    }

    @Override
    public @Nullable Object getNodeState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("tag", tag);
        state.put("color", color);
        return state;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Map<?, ?> values) {
            if (values.get("tag") instanceof String tagText) {
                setTag(tagText);
            }
            if (values.get("color") instanceof String colorText) {
                setColor(colorText);
            }
            return;
        }
        if (state instanceof Object[] values && values.length >= 2) {
            if (values[0] instanceof String valueTag) {
                setTag(valueTag);
            }
            if (values[1] instanceof String valueColor) {
                setColor(valueColor);
            }
        }
    }
}
