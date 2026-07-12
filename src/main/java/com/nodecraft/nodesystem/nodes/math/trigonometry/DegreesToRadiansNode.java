package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.trigonometry.deg_to_rad",
    displayName = "Degrees To Radians",
    description = "将角度从度数转换为弧度",
    category = "math.trigonometry",
    order = 3
)
public class DegreesToRadiansNode extends BaseNode {

    private static final String INPUT_DEGREES_ID = "input_degrees";
    private static final String OUTPUT_RADIANS_ID = "output_radians";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DegreesToRadiansNode() {
        super(UUID.randomUUID(), "math.trigonometry.deg_to_rad");

        addInputPort(new BasePort(INPUT_DEGREES_ID, "Degrees", "Angle in degrees", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_RADIANS_ID, "Radians", "Angle in radians", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object val = inputValues.get(INPUT_DEGREES_ID);
        if (!(val instanceof Number number)) {
            outputValues.put(OUTPUT_RADIANS_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double degrees = number.doubleValue();
        if (!Double.isFinite(degrees)) {
            outputValues.put(OUTPUT_RADIANS_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double radians = Math.toRadians(degrees);
        outputValues.put(OUTPUT_RADIANS_ID, radians);
        outputValues.put(OUTPUT_VALID_ID, Double.isFinite(radians));
    }

    @Override
    public String getDescription() {
        return "Converts an angle from degrees to radians.";
    }

    @Override
    public String getDisplayName() {
        return "Degrees to Radians";
    }
}
