package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.world.WorldQueryAccess;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.read.read_sign_text",
    displayName = "Read Sign Text",
    description = "Reads plain text from the front face of a sign block entity",
    category = "world.read",
    order = 10
)
public class ReadSignTextNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReadSignTextNode.class);

    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    private static final String OUTPUT_TEXT_LINES_ID = "output_text_lines";
    private static final String OUTPUT_COMBINED_TEXT_ID = "output_combined_text";
    private static final String OUTPUT_IS_SIGN_ID = "output_is_sign";
    private static final String OUTPUT_SIGN_TYPE_ID = "output_sign_type";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ReadSignTextNode() {
        super(UUID.randomUUID(), "world.read.read_sign_text");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Sign position", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_TEXT_LINES_ID, "Text Lines", "List of sign lines", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COMBINED_TEXT_ID, "Combined Text", "Combined non-empty sign text", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_SIGN_ID, "Is Sign", "Whether the target block entity is a sign", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_SIGN_TYPE_ID, "Sign Type", "Registry id of the sign block", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether sign read inputs and context were valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when sign read fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reads plain text from the front face of a sign block entity";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPos pos = WorldReadUtils.requireBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (pos == null) {
            writeFailure("Coordinate input must be a block position.");
            return;
        }
        if (context == null || context.getWorld() == null) {
            writeFailure("Execution context or world is missing.");
            return;
        }

        WorldQueryAccess access = new WorldQueryAccess(context.getWorld());
        WorldQueryAccess.BlockEntityRead entityRead = access.getBlockEntity(pos);
        if (entityRead.status() != WorldQueryAccess.Status.OK) {
            writeFailure(entityRead.status() == WorldQueryAccess.Status.BUDGET
                    ? "World read budget exceeded."
                    : "Target chunk is not loaded");
            return;
        }

        if (!(entityRead.entity() instanceof SignBlockEntity sign)) {
            outputValues.put(OUTPUT_TEXT_LINES_ID, List.of());
            outputValues.put(OUTPUT_COMBINED_TEXT_ID, "");
            outputValues.put(OUTPUT_IS_SIGN_ID, false);
            outputValues.put(OUTPUT_SIGN_TYPE_ID, "");
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_ERROR_ID, "");
            return;
        }

        WorldQueryAccess.BlockRead blockRead = access.getBlockState(pos);
        if (blockRead.status() != WorldQueryAccess.Status.OK || blockRead.state() == null) {
            writeFailure(blockRead.status() == WorldQueryAccess.Status.UNLOADED
                    ? "Target chunk is not loaded"
                    : "World read budget exceeded.");
            return;
        }

        try {
            String signType = Registries.BLOCK.getId(blockRead.state().getBlock()).toString();
            var text = sign.getFrontText();
            List<String> textLines = new ArrayList<>(4);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                String line = text.getMessage(i, false).getString();
                textLines.add(line);
                if (!line.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append(' ');
                    }
                    builder.append(line);
                }
            }

            outputValues.put(OUTPUT_TEXT_LINES_ID, textLines);
            outputValues.put(OUTPUT_COMBINED_TEXT_ID, builder.toString());
            outputValues.put(OUTPUT_IS_SIGN_ID, true);
            outputValues.put(OUTPUT_SIGN_TYPE_ID, signType);
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_ERROR_ID, "");
        } catch (Exception e) {
            LOGGER.debug("World read failed", e);
            writeFailure("World read failed");
        }
    }

    private void writeFailure(String error) {
        outputValues.put(OUTPUT_TEXT_LINES_ID, List.of());
        outputValues.put(OUTPUT_COMBINED_TEXT_ID, "");
        outputValues.put(OUTPUT_IS_SIGN_ID, false);
        outputValues.put(OUTPUT_SIGN_TYPE_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
