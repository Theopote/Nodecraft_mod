package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.trigonometry.rad_to_deg",
    displayName = "Radians To Degrees",
    description = "将角度从弧度转换为度数",
    category = "math.trigonometry",
    order = 4
)
public class RadiansToDegreesNode extends BaseNode {

    private static final String INPUT_RADIANS_ID = "input_radians";
    private static final String OUTPUT_DEGREES_ID = "output_degrees";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RadiansToDegreesNode() {
        super(UUID.randomUUID(), "math.trigonometry.rad_to_deg");

        addInputPort(new BasePort(INPUT_RADIANS_ID, "Radians", "Angle in radians", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_DEGREES_ID, "Degrees", "Angle in degrees", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object val = inputValues.get(INPUT_RADIANS_ID);
        if (!(val instanceof Number number)) {
            outputValues.put(OUTPUT_DEGREES_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double radians = number.doubleValue();
        if (!Double.isFinite(radians)) {
            outputValues.put(OUTPUT_DEGREES_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double degrees = Math.toDegrees(radians);
        outputValues.put(OUTPUT_DEGREES_ID, degrees);
        outputValues.put(OUTPUT_VALID_ID, Double.isFinite(degrees));
    }

    @Override
    public String getDescription() {
        return "Converts an angle from radians to degrees.";
    }

    @Override
    public String getDisplayName() {
        return "Radians to Degrees";
    }
}
