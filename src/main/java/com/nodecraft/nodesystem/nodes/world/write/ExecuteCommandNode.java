package com.nodecraft.nodesystem.nodes.world.write;

import com.mojang.brigadier.StringReader;
import com.nodecraft.minecraft.command.CommandValidator;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.WorldWriteCommandPolicy;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.execute_command",
    displayName = "Execute Command",
    description = "Privileged escape hatch: executes a Minecraft command when AllowCommandNodes is enabled",
    category = "world.write",
    order = 13
)
public class ExecuteCommandNode extends BaseNode {

    private static final String INPUT_COMMAND_ID = "input_command";
    private static final String INPUT_EXECUTOR_ID = "input_executor";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_PARSE_RESULT_ID = "input_parse_result";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_RESULT_CODE_ID = "output_result_code";
    private static final String OUTPUT_RESULT_TEXT_ID = "output_result_text";
    private static final String OUTPUT_PARSED_RESULT_ID = "output_parsed_result";
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private boolean parseResult = false;

    public ExecuteCommandNode() {
        super(UUID.randomUUID(), "world.write.execute_command");

        addInputPort(new BasePort(INPUT_COMMAND_ID, "Command", "Command string (with or without leading /)", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_EXECUTOR_ID, "Executor", "Optional player command source", NodeDataType.PLAYER, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_PARSE_RESULT_ID, "Parse Result", "Whether to return captured output lines", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether command execution succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_CODE_ID, "Result Code", "Command return code", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_TEXT_ID, "Result Text", "Captured command output", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_PARSED_RESULT_ID, "Parsed Result", "Optional captured output lines", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why command did not run or failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(false, 0, "", null, false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(false, 0, "", null, true, "Not triggered");
            return;
        }

        if (!WorldWriteCommandPolicy.isAllowed()) {
            publish(false, 0, "", null, false, "Execute Command is disabled (AllowCommandNodes=false).");
            return;
        }

        Boolean parseOutput = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_PARSE_RESULT_ID, parseResult);
        if (parseOutput == null) {
            publish(false, 0, "", null, false, "Parse Result is connected but null or invalid.");
            return;
        }

        if (!(inputValues.get(INPUT_COMMAND_ID) instanceof String rawCommand) || rawCommand.isBlank()) {
            publish(false, 0, "", null, false, "Command must be a non-empty STRING.");
            return;
        }
        String normalized = normalizeCommand(rawCommand);
        if (normalized.length() > GenerationLimits.MAX_WORLD_WRITE_COMMAND_CHARS) {
            publish(false, 0, "", null, false,
                "Command exceeds MAX_WORLD_WRITE_COMMAND_CHARS (" + GenerationLimits.MAX_WORLD_WRITE_COMMAND_CHARS + ").");
            return;
        }
        if (context == null || !(context.getWorld() instanceof ServerWorld world)) {
            publish(false, 0, "", null, false, "Missing execution world");
            return;
        }
        ServerPlayerEntity executorPlayer = inputValues.get(INPUT_EXECUTOR_ID) instanceof ServerPlayerEntity provided
            ? provided
            : context.getPlayer();
        if (executorPlayer != null && !WorldWriteUtils.isChunkLoaded(context, executorPlayer.getBlockPos())) {
            publish(false, 0, "", null, false, WorldWriteUtils.UNLOADED_CHUNK_ERROR);
            return;
        }

        try {
            if (!CommandValidator.getInstance().validateCommand("/" + normalized)) {
                String msg = CommandValidator.getInstance().getErrorMessage("/" + normalized);
                publish(false, 0, msg, null, true, msg);
                return;
            }

            CapturingCommandOutput output = new CapturingCommandOutput();
            ServerCommandSource source = resolveSource(context, world, inputValues.get(INPUT_EXECUTOR_ID), output);
            if (source == null) {
                publish(false, 0, "", null, false, "Server-level commands require a player executor when AllowCommandNodes is enabled.");
                return;
            }
            var dispatcher = world.getServer().getCommandManager().getDispatcher();
            var parseResults = dispatcher.parse(new StringReader(normalized), source);
            int resultCode = dispatcher.execute(parseResults);
            boolean success = resultCode >= 0;
            String resultText = output.joined();
            if (resultText.isBlank()) {
                resultText = "Command executed with result " + resultCode;
            }
            Object parsed = parseOutput ? output.messages() : null;
            publish(success, resultCode, resultText, parsed, true, success ? "" : "Command returned " + resultCode);
        } catch (Exception e) {
            publish(false, -1, "Error executing command", null, true, "Command execution failed");
        }
    }

    private @Nullable ServerCommandSource resolveSource(
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
        // No PermissionPredicate.ALL fallback — require an explicit player executor.
        return null;
    }

    private String normalizeCommand(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private void publish(
        boolean success,
        int resultCode,
        String resultText,
        Object parsedResult,
        boolean valid,
        String error
    ) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_RESULT_CODE_ID, resultCode);
        outputValues.put(OUTPUT_RESULT_TEXT_ID, resultText == null ? "" : resultText);
        outputValues.put(OUTPUT_PARSED_RESULT_ID, parsedResult);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isParseResult() { return parseResult; }
    public void setParseResult(boolean parseResult) { this.parseResult = parseResult; markDirty(); }
    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }

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
