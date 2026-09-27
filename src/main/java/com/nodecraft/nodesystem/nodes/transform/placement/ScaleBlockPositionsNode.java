package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PlacementBlockUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.scale_block_positions",
    displayName = "Scale Block Positions",
    description = "Scales a list of block positions relative to a center point",
    category = "transform.placement",
    order = 6
)
public class ScaleBlockPositionsNode extends AbstractPlacementNode {

    private static final String INPUT_BLOCK_POSITIONS_ID = "input_block_positions";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_SCALE_FACTOR_ID = "input_scale_factor";
    private static final String INPUT_SCALE_VECTOR_ID = "input_scale_vector";

    private static final String OUTPUT_BLOCK_POSITIONS_ID = "output_block_positions";
    private static final String OUTPUT_EFFECTIVE_SCALE_ID = "output_effective_scale";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public ScaleBlockPositionsNode() {
        super("transform.placement.scale_block_positions");

        addInputPort(new BasePort(INPUT_BLOCK_POSITIONS_ID, "Block Positions", "The block positions to scale", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Scaling center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_SCALE_FACTOR_ID, "Scale Factor", "Uniform scaling factor", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SCALE_VECTOR_ID, "Scale Vector", "Non-uniform scaling vector (XYZ)", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_POSITIONS_ID, "Block Positions", "Scaled block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_SCALE_ID, "Effective Scale", "Scale vector actually applied", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output block positions", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Scales a list of block positions relative to a center point";
    }

    @Override
    public String getDisplayName() {
        return "Scale Block Positions";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_BLOCK_POSITIONS_ID);
        if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            writeInvalid("Missing block position list");
            return;
        }

        String listError = PlacementBlockUtils.validateBlockListSize(coordinates);
        if (listError != null) {
            writeInvalid(listError);
            return;
        }

        Vector3d center = OptionalPortDrive.resolveOptionalPoint(this, INPUT_CENTER_ID, new Vector3d());
        if (center == null) {
            writeInvalid("Center connected but invalid");
            return;
        }

        boolean factorConnected = OptionalPortDrive.isConnected(this, INPUT_SCALE_FACTOR_ID);
        boolean vectorConnected = OptionalPortDrive.isConnected(this, INPUT_SCALE_VECTOR_ID);
        if (factorConnected && vectorConnected) {
            writeInvalid("Connect Scale Factor or Scale Vector, not both");
            return;
        }

        Vector3d scale;
        if (vectorConnected) {
            scale = OptionalPortDrive.resolveOptionalVector(this, INPUT_SCALE_VECTOR_ID, null);
            if (scale == null) {
                writeInvalid("Scale Vector connected but invalid");
                return;
            }
        } else {
            Double factor = OptionalPortDrive.resolveOptionalDouble(this, INPUT_SCALE_FACTOR_ID, 1.0d);
            if (factor == null) {
                writeInvalid("Scale Factor connected but invalid");
                return;
            }
            scale = new Vector3d(factor, factor, factor);
        }
        if (!isStrictlyPositive(scale)) {
            writeInvalid("Scale components must be finite and > 0");
            return;
        }

        BlockPosList result = new BlockPosList();
        for (BlockPos pos : coordinates) {
            Vector3d scaled = BlockSpace.cellCenter(pos)
                .sub(center)
                .mul(scale)
                .add(center);
            BlockPos snapped = PlacementBlockUtils.trySnapCellCenter(scaled);
            if (snapped == null) {
                writeInvalid("Non-finite scale result");
                return;
            }
            result.add(snapped);
        }

        outputValues.put(OUTPUT_BLOCK_POSITIONS_ID, result);
        outputValues.put(OUTPUT_EFFECTIVE_SCALE_ID, VectorUtils.toVectorPort(scale));
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        markSuccess();
    }

    private static boolean isStrictlyPositive(Vector3d scale) {
        return Double.isFinite(scale.x) && scale.x > 0.0d
            && Double.isFinite(scale.y) && scale.y > 0.0d
            && Double.isFinite(scale.z) && scale.z > 0.0d;
    }

    private void writeInvalid(String error) {
        putEmptyBlockListOutputs(OUTPUT_BLOCK_POSITIONS_ID);
        outputValues.put(OUTPUT_EFFECTIVE_SCALE_ID, VectorUtils.toVectorPort(null));
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }
}
