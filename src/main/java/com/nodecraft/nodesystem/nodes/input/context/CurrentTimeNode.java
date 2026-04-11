package com.nodecraft.nodesystem.nodes.input.context;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Reads the current world time state from the active player context.
 */
@NodeInfo(
    id = "input.context.current_time",
    displayName = "Current Time",
    description = "Gets the current time and weather state from the active Minecraft world.",
    category = "input.context"
)
public class CurrentTimeNode extends BaseNode {

    private static final String OUTPUT_TIME_TICKS_ID = "output_time_ticks";
    private static final String OUTPUT_DAY_ID = "output_day";
    private static final String OUTPUT_DAY_TIME_ID = "output_day_time";
    private static final String OUTPUT_HOUR_ID = "output_hour";
    private static final String OUTPUT_MINUTE_ID = "output_minute";
    private static final String OUTPUT_IS_DAY_ID = "output_is_day";
    private static final String OUTPUT_IS_NIGHT_ID = "output_is_night";
    private static final String OUTPUT_IS_RAINING_ID = "output_is_raining";
    private static final String OUTPUT_IS_THUNDERING_ID = "output_is_thundering";

    private final String description = "Gets the current time in the Minecraft world.";

    public CurrentTimeNode() {
        super(UUID.randomUUID(), "input.context.current_time");

        IPort timeTicksOutput = new BasePort(
            OUTPUT_TIME_TICKS_ID,
            "Time (Ticks)",
            "The current world time in ticks",
            NodeDataType.INTEGER,
            this
        );
        addOutputPort(timeTicksOutput);

        IPort dayOutput = new BasePort(
            OUTPUT_DAY_ID,
            "Day",
            "The current world day",
            NodeDataType.INTEGER,
            this
        );
        addOutputPort(dayOutput);

        IPort dayTimeOutput = new BasePort(
            OUTPUT_DAY_TIME_ID,
            "Day Time",
            "The time of day in ticks (0-24000)",
            NodeDataType.INTEGER,
            this
        );
        addOutputPort(dayTimeOutput);

        IPort hourOutput = new BasePort(
            OUTPUT_HOUR_ID,
            "Hour",
            "The current hour (0-23)",
            NodeDataType.INTEGER,
            this
        );
        addOutputPort(hourOutput);

        IPort minuteOutput = new BasePort(
            OUTPUT_MINUTE_ID,
            "Minute",
            "The current minute (0-59)",
            NodeDataType.INTEGER,
            this
        );
        addOutputPort(minuteOutput);

        IPort isDayOutput = new BasePort(
            OUTPUT_IS_DAY_ID,
            "Is Day",
            "Whether it is currently daytime",
            NodeDataType.BOOLEAN,
            this
        );
        addOutputPort(isDayOutput);

        IPort isNightOutput = new BasePort(
            OUTPUT_IS_NIGHT_ID,
            "Is Night",
            "Whether it is currently nighttime",
            NodeDataType.BOOLEAN,
            this
        );
        addOutputPort(isNightOutput);

        IPort isRainingOutput = new BasePort(
            OUTPUT_IS_RAINING_ID,
            "Is Raining",
            "Whether it is currently raining",
            NodeDataType.BOOLEAN,
            this
        );
        addOutputPort(isRainingOutput);

        IPort isThunderingOutput = new BasePort(
            OUTPUT_IS_THUNDERING_ID,
            "Is Thundering",
            "Whether it is currently thundering",
            NodeDataType.BOOLEAN,
            this
        );
        addOutputPort(isThunderingOutput);

        resetOutputs();
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) {
            resetOutputs();
            return;
        }

        PlayerAccessor playerAccessor = context.getPlayerAccessor();
        if (playerAccessor == null) {
            resetOutputs();
            return;
        }

        updateOutputsFromAccessor(playerAccessor);
    }

    private void updateOutputsFromAccessor(PlayerAccessor playerAccessor) {
        long worldTimeTicks = playerAccessor.getWorldTime();
        int worldDay = playerAccessor.getWorldDay();
        int dayTime = (int) (worldTimeTicks % 24000);

        // Minecraft time zero is 06:00 local time.
        int adjustedTime = (dayTime + 6000) % 24000;
        int hour = adjustedTime / 1000;
        int minute = (int) ((adjustedTime % 1000) / (1000.0f / 60.0f));

        boolean isDay = playerAccessor.isDaytime();
        boolean isNight = !isDay;
        boolean isRaining = playerAccessor.isRaining();
        boolean isThundering = playerAccessor.isThundering();

        outputValues.put(OUTPUT_TIME_TICKS_ID, worldTimeTicks);
        outputValues.put(OUTPUT_DAY_ID, worldDay);
        outputValues.put(OUTPUT_DAY_TIME_ID, dayTime);
        outputValues.put(OUTPUT_HOUR_ID, hour);
        outputValues.put(OUTPUT_MINUTE_ID, minute);
        outputValues.put(OUTPUT_IS_DAY_ID, isDay);
        outputValues.put(OUTPUT_IS_NIGHT_ID, isNight);
        outputValues.put(OUTPUT_IS_RAINING_ID, isRaining);
        outputValues.put(OUTPUT_IS_THUNDERING_ID, isThundering);
    }

    private void resetOutputs() {
        outputValues.put(OUTPUT_TIME_TICKS_ID, 0);
        outputValues.put(OUTPUT_DAY_ID, 0);
        outputValues.put(OUTPUT_DAY_TIME_ID, 0);
        outputValues.put(OUTPUT_HOUR_ID, 6);
        outputValues.put(OUTPUT_MINUTE_ID, 0);
        outputValues.put(OUTPUT_IS_DAY_ID, true);
        outputValues.put(OUTPUT_IS_NIGHT_ID, false);
        outputValues.put(OUTPUT_IS_RAINING_ID, false);
        outputValues.put(OUTPUT_IS_THUNDERING_ID, false);
    }

    @Override
    public Object getNodeState() {
        return null;
    }

    @Override
    public void setNodeState(Object state) {
        // Stateless.
    }
}
