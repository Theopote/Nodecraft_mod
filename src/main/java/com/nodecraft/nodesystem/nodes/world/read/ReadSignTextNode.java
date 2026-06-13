package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.read.read_sign_text",
    displayName = "Read Sign Text",
    description = "Reads text from a sign block entity",
    category = "world.read"
)
public class ReadSignTextNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_INCLUDE_FORMATTING_ID = "input_include_formatting";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_TEXT_LINES_ID = "output_text_lines";
    private static final String OUTPUT_COMBINED_TEXT_ID = "output_combined_text";
    private static final String OUTPUT_IS_SIGN_ID = "output_is_sign";
    private static final String OUTPUT_SIGN_TYPE_ID = "output_sign_type";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ReadSignTextNode() {
        super(UUID.randomUUID(), "world.read.read_sign_text");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Sign position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_INCLUDE_FORMATTING_ID, "Include Formatting", "Reserved for future styled text output; current output is plain text", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether sign text was read successfully", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_TEXT_LINES_ID, "Text Lines", "List of sign lines", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COMBINED_TEXT_ID, "Combined Text", "Combined non-empty sign text", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_SIGN_ID, "Is Sign", "Whether the target block entity is a sign", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_SIGN_TYPE_ID, "Sign Type", "Registry id of the sign block", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether sign read inputs and context were valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when sign read fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reads text from a sign block entity";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        List<String> textLines = new ArrayList<>();
        String combinedText = "";
        boolean isSign = false;
        String signType = "";
        boolean valid = false;
        String error = "";

        BlockPos pos = WorldReadUtils.resolveBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (context == null || context.getWorld() == null) {
            error = "Execution context or world is missing.";
        } else if (pos == null) {
            error = "Coordinate input must resolve to a block position.";
        } else {
            try {
                valid = true;
                var blockEntity = context.getWorld().getBlockEntity(pos);
                if (blockEntity instanceof SignBlockEntity sign) {
                    isSign = true;
                    signType = Registries.BLOCK.getId(context.getWorld().getBlockState(pos).getBlock()).toString();

                    var text = sign.getFrontText();
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < 4; i++) {
                        String line = text.getMessage(i, false).getString();
                        textLines.add(line);
                        if (!line.isBlank()) {
                            if (builder.length() > 0) {
                                builder.append(' ');
                            }
                            builder.append(line);
                        }
                    }
                    combinedText = builder.toString();
                    success = true;
                }
            } catch (Exception e) {
                error = "Error reading sign text at " + pos + ": " + e.getMessage();
                NodeCraft.LOGGER.warn(error);
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_TEXT_LINES_ID, textLines);
        outputValues.put(OUTPUT_COMBINED_TEXT_ID, combinedText);
        outputValues.put(OUTPUT_IS_SIGN_ID, isSign);
        outputValues.put(OUTPUT_SIGN_TYPE_ID, signType);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}
