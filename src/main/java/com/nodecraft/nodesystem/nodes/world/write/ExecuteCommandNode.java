package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.minecraft.command.CommandValidator;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.execute_command",
    displayName = "Execute Command",
    description = "Executes a Minecraft command on the server",
    category = "world.write"
)
public class ExecuteCommandNode extends BaseNode {

    private static final String INPUT_COMMAND_ID = "input_command";
    private static final String INPUT_EXECUTOR_ID = "input_executor";
    private static final String INPUT_PARSE_RESULT_ID = "input_parse_result";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_RESULT_CODE_ID = "output_result_code";
    private static final String OUTPUT_RESULT_TEXT_ID = "output_result_text";
    private static final String OUTPUT_PARSED_RESULT_ID = "output_parsed_result";

    private boolean parseResult = false;

    public ExecuteCommandNode() {
        super(UUID.randomUUID(), "world.write.execute_command");

        addInputPort(new BasePort(INPUT_COMMAND_ID, "Command", "Command string to execute", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_EXECUTOR_ID, "Executor", "Optional player command source", NodeDataType.PLAYER, this));
        addInputPort(new BasePort(INPUT_PARSE_RESULT_ID, "Parse Result", "Whether to return captured output lines", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether command execution succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_CODE_ID, "Result Code", "Command return code", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_TEXT_ID, "Result Text", "Captured command output", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_PARSED_RESULT_ID, "Parsed Result", "Optional captured output lines", NodeDataType.ANY, this));
    }

    @Override
    public String getDescription() {
        return "Executes a Minecraft command on the server";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        int resultCode = 0;
        String resultText = "";
        Object parsedResult = null;

        Object commandObj = inputValues.get(INPUT_COMMAND_ID);
        Object executorObj = inputValues.get(INPUT_EXECUTOR_ID);
        boolean parseOutput = inputValues.get(INPUT_PARSE_RESULT_ID) instanceof Boolean value ? value : parseResult;

        if (context != null && context.getWorld() instanceof ServerWorld world && commandObj instanceof String rawCommand && !rawCommand.isBlank()) {
            try {
                String normalizedCommand = normalizeCommand(rawCommand);
                if (!CommandValidator.getInstance().validateCommand("/" + normalizedCommand)) {
                    resultText = CommandValidator.getInstance().getErrorMessage("/" + normalizedCommand);
                    publish(success, resultCode, resultText, parsedResult);
                    return;
                }

                CapturingCommandOutput output = new CapturingCommandOutput();
                ServerCommandSource source = resolveSource(context, world, executorObj, output);
                var dispatcher = world.getServer().getCommandManager().getDispatcher();
                var parseResults = dispatcher.parse(new StringReader(normalizedCommand), source);
                resultCode = dispatcher.execute(parseResults);
                success = resultCode >= 0;
                resultText = output.joined();
                if (resultText.isBlank()) {
                    resultText = "Command executed with result " + resultCode;
                }
                if (parseOutput) {
                    parsedResult = output.messages();
                }
            } catch (Exception e) {
                resultCode = -1;
                resultText = "Error executing command: " + e.getMessage();
            }
        }

        publish(success, resultCode, resultText, parsedResult);
    }

    private ServerCommandSource resolveSource(
        ExecutionContext context,
        ServerWorld world,
        Object executorObj,
        CapturingCommandOutput output
    ) {
        if (executorObj instanceof ServerPlayerEntity player) {
            return player.getCommandSource().withOutput(output);
        }
        if (context.getPlayer() != null) {
            return context.getPlayer().getCommandSource().withOutput(output);
        }
        return new ServerCommandSource(
            output,
            new Vec3d(0.0, 0.0, 0.0),
            Vec2f.ZERO,
            world,
            PermissionPredicate.ALL,
            "NodeCraft",
            Text.literal("NodeCraft"),
            world.getServer(),
            null
        );
    }

    private String normalizeCommand(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private void publish(boolean success, int resultCode, String resultText, Object parsedResult) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_RESULT_CODE_ID, resultCode);
        outputValues.put(OUTPUT_RESULT_TEXT_ID, resultText);
        outputValues.put(OUTPUT_PARSED_RESULT_ID, parsedResult);
    }

    public boolean isParseResult() {
        return parseResult;
    }

    public void setParseResult(boolean parseResult) {
        this.parseResult = parseResult;
        markDirty();
    }

    @Override
    public @Nullable Object getNodeState() {
        return parseResult;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Boolean value) {
            parseResult = value;
        }
    }

    private record CapturedMessage(String text) {
    }

    private static final class CapturingCommandOutput implements CommandOutput {
        private final List<CapturedMessage> messages = new ArrayList<>();

        @Override
        public void sendMessage(Text message) {
            messages.add(new CapturedMessage(message.getString()));
        }

        @Override
        public boolean shouldReceiveFeedback() {
            return true;
        }

        @Override
        public boolean shouldTrackOutput() {
            return true;
        }

        @Override
        public boolean shouldBroadcastConsoleToOps() {
            return false;
        }

        public String joined() {
            return messages.stream().map(CapturedMessage::text).reduce((a, b) -> a + "\n" + b).orElse("");
        }

        public List<String> messages() {
            return messages.stream().map(CapturedMessage::text).toList();
        }
    }
}
