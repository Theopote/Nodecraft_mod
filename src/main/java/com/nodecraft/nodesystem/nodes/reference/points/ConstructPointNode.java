package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.construct_point",
    displayName = "Construct Point",
    description = "Constructs a geometric point from X, Y, and Z double components",
    category = "reference.points",
    order = 4
)
public class ConstructPointNode extends BaseNode {

    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ConstructPointNode() {
        super(UUID.randomUUID(), "reference.points.construct_point");

        addInputPort(new BasePort(INPUT_X_ID, "X", "X component input", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Y component input", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Z component input", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Point", "Constructed geometric point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether all resolved components are finite numbers",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Construct Point";
    }

    @Override
    public String getDescription() {
        return "Constructs a geometric point from X, Y, and Z double components";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double x = toDouble(inputValues.get(INPUT_X_ID));
        double y = toDouble(inputValues.get(INPUT_Y_ID));
        double z = toDouble(inputValues.get(INPUT_Z_ID));
        boolean valid = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);

        outputValues.put(OUTPUT_POINT_ID, valid ? new PointData(x, y, z) : null);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.NaN;
    }
}
