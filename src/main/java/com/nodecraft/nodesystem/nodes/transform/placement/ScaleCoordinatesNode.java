package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.scale_coordinates",
    displayName = "Scale Coordinates",
    description = "Scales a list of block coordinates relative to a center point",
    category = "transform.placement",
    order = 6
)
public class ScaleCoordinatesNode extends BaseNode {

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_SCALE_FACTOR_ID = "input_scale_factor";
    private static final String INPUT_SCALE_VECTOR_ID = "input_scale_vector";

    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_EFFECTIVE_SCALE_ID = "output_effective_scale";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Use Uniform Scaling", category = "Scale", order = 1)
    private boolean useUniformScaling = true;

    public ScaleCoordinatesNode() {
        super(UUID.randomUUID(), "transform.placement.scale_coordinates");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "The coordinates to scale", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Scaling center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_SCALE_FACTOR_ID, "Scale Factor", "Uniform scaling factor", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SCALE_VECTOR_ID, "Scale Vector", "Non-uniform scaling vector (XYZ)", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", "Scaled coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_SCALE_ID, "Effective Scale", "Scale vector actually applied", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output coordinates", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the coordinate scale succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Scales a list of coordinates relative to a center point";
    }

    @Override
    public String getDisplayName() {
        return "Scale Coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        BlockPosList result = new BlockPosList();
        if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            writeResult(result, false, null);
            return;
        }

        Vector3d center = OptionalPortDrive.resolveOptionalPoint(this, INPUT_CENTER_ID, new Vector3d());
        Vector3d scale = resolveScale();
        if (center == null || scale == null || !isStrictlyPositive(scale)) {
            writeResult(result, false, null);
            return;
        }

        for (BlockPos pos : coordinates) {
            Vector3d scaled = new Vector3d(pos.getX(), pos.getY(), pos.getZ())
                .sub(center)
                .mul(scale)
                .add(center);
            result.add(new BlockPos(
                (int) Math.round(scaled.x),
                (int) Math.round(scaled.y),
                (int) Math.round(scaled.z)
            ));
        }

        writeResult(result, true, scale);
    }

    private @Nullable Vector3d resolveScale() {
        if (useUniformScaling) {
            Double factor = OptionalPortDrive.resolveOptionalDouble(this, INPUT_SCALE_FACTOR_ID, 1.0d);
            if (factor == null) {
                return null;
            }
            return new Vector3d(factor, factor, factor);
        }
        return OptionalPortDrive.resolveOptionalVector(this, INPUT_SCALE_VECTOR_ID, new Vector3d(1.0d, 1.0d, 1.0d));
    }

    private static boolean isStrictlyPositive(Vector3d scale) {
        return Double.isFinite(scale.x) && scale.x > 0.0d
            && Double.isFinite(scale.y) && scale.y > 0.0d
            && Double.isFinite(scale.z) && scale.z > 0.0d;
    }

    private void writeResult(BlockPosList result, boolean valid, @Nullable Vector3d effectiveScale) {
        outputValues.put(OUTPUT_COORDINATES_ID, result);
        outputValues.put(OUTPUT_EFFECTIVE_SCALE_ID, VectorUtils.toVectorPort(effectiveScale));
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    public boolean isUseUniformScaling() {
        return useUniformScaling;
    }

    public void setUseUniformScaling(boolean useUniformScaling) {
        if (this.useUniformScaling != useUniformScaling) {
            this.useUniformScaling = useUniformScaling;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("useUniformScaling", useUniformScaling);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap && stateMap.get("useUniformScaling") instanceof Boolean value) {
            setUseUniformScaling(value);
        }
    }
}
