package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.write.write_sign_text",
    displayName = "Write Sign Text",
    description = "Writes text to a sign block entity",
    category = "world.write"
)
public class WriteSignTextNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
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

    private String[] defaultLines = new String[]{"", "", "", ""};
    private boolean allowFormatting = true;
    private String textColor = "black";

    public WriteSignTextNode() {
        super(UUID.randomUUID(), "world.write.write_sign_text");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Sign position", NodeDataType.COORDINATE, this));
        addInputPort(new BasePort(INPUT_LINE_1_ID, "Line 1", "First line", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_LINE_2_ID, "Line 2", "Second line", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_LINE_3_ID, "Line 3", "Third line", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_LINE_4_ID, "Line 4", "Fourth line", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_LINES_LIST_ID, "Lines List", "Optional list input overriding the individual lines", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_TEXT_COLOR_ID, "Text Color", "Sign dye color id", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_GLOWING_ID, "Glowing", "Whether sign text should glow", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether sign text was updated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_SIGN_ID, "Is Sign", "Whether the target block entity is a sign", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_SIGN_TYPE_ID, "Sign Type", "Registry id of the sign block", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Writes text to a sign block entity";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        boolean isSign = false;
        String signType = "";

        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        if (context != null && context.getWorld() != null && coordinateObj instanceof BlockPos pos) {
            String[] lines = resolveLines();
            String colorId = inputValues.get(INPUT_TEXT_COLOR_ID) instanceof String value ? value : textColor;
            boolean glowing = inputValues.get(INPUT_GLOWING_ID) instanceof Boolean value ? value : false;

            try {
                var blockEntity = context.getWorld().getBlockEntity(pos);
                if (blockEntity instanceof SignBlockEntity sign) {
                    isSign = true;
                    signType = Registries.BLOCK.getId(context.getWorld().getBlockState(pos).getBlock()).toString();

                    var signText = sign.getFrontText()
                        .withColor(DyeColor.byId(colorId.toLowerCase(), DyeColor.BLACK))
                        .withGlowing(glowing);

                    for (int i = 0; i < 4; i++) {
                        String line = sanitizeLine(lines[i]);
                        signText = signText.withMessage(i, Text.literal(line));
                    }

                    success = sign.setText(signText, true);
                    if (success) {
                        sign.markDirty();
                        context.getWorld().updateListeners(pos, context.getWorld().getBlockState(pos), context.getWorld().getBlockState(pos), 3);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error writing sign text: " + e.getMessage());
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_IS_SIGN_ID, isSign);
        outputValues.put(OUTPUT_SIGN_TYPE_ID, signType);
    }

    private String[] resolveLines() {
        String[] lines = new String[4];
        System.arraycopy(defaultLines, 0, lines, 0, 4);

        Object linesListObj = inputValues.get(INPUT_LINES_LIST_ID);
        if (linesListObj instanceof List<?> linesList) {
            for (int i = 0; i < Math.min(linesList.size(), 4); i++) {
                Object lineObj = linesList.get(i);
                if (lineObj != null) {
                    lines[i] = String.valueOf(lineObj);
                }
            }
            return lines;
        }

        if (inputValues.get(INPUT_LINE_1_ID) instanceof String value) lines[0] = value;
        if (inputValues.get(INPUT_LINE_2_ID) instanceof String value) lines[1] = value;
        if (inputValues.get(INPUT_LINE_3_ID) instanceof String value) lines[2] = value;
        if (inputValues.get(INPUT_LINE_4_ID) instanceof String value) lines[3] = value;
        return lines;
    }

    private String sanitizeLine(String line) {
        String normalized = line != null ? line : "";
        if (allowFormatting) {
            return normalized;
        }
        return normalized.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    public String[] getDefaultLines() {
        return defaultLines;
    }

    public void setDefaultLines(String[] defaultLines) {
        if (defaultLines != null && defaultLines.length == 4) {
            this.defaultLines = defaultLines;
            markDirty();
        }
    }

    public boolean isAllowFormatting() {
        return allowFormatting;
    }

    public void setAllowFormatting(boolean allowFormatting) {
        this.allowFormatting = allowFormatting;
        markDirty();
    }

    public String getTextColor() {
        return textColor;
    }

    public void setTextColor(String textColor) {
        if (textColor != null && !textColor.isBlank()) {
            this.textColor = textColor;
            markDirty();
        }
    }

    @Override
    public @Nullable Object getNodeState() {
        return new Object[]{defaultLines, allowFormatting, textColor};
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Object[] values && values.length >= 3) {
            if (values[0] instanceof String[] lines && lines.length == 4) {
                defaultLines = lines;
            }
            if (values[1] instanceof Boolean formatting) {
                allowFormatting = formatting;
            }
            if (values[2] instanceof String color) {
                textColor = color;
            }
        }
    }
}
