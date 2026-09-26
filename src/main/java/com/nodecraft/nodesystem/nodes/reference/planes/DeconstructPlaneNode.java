package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.planes.deconstruct_plane",
    displayName = "Deconstruct Plane",
    description = "Splits a PLANE into origin point and normal vector",
    category = "reference.planes",
    order = 6
)
public class DeconstructPlaneNode extends BaseNode {

    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_ORIGIN_ID = "output_origin";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.deconstruct_plane");

        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Plane to deconstruct", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_ORIGIN_ID, "Origin", "Plane origin point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Plane normal vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when plane input is a usable canonical plane", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane)) {
            writeInvalid();
            return;
        }

        PlaneData canonical = plane.normalized();
        if (canonical == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_ORIGIN_ID, new PointData(canonical.getPoint()));
        outputValues.put(OUTPUT_NORMAL_ID, canonical.getNormal());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_ORIGIN_ID, null);
        outputValues.put(OUTPUT_NORMAL_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
