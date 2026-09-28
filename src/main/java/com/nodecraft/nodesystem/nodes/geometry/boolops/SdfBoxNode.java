package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxSdfData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_box",
    displayName = "SDF Box",
    description = "Builds an axis-aligned box signed-distance-field primitive from center and half extents",
    category = "geometry.sdf",
    order = 11
)
public class SdfBoxNode extends AbstractSdfNode {
    @NodeProperty(displayName = "Half Extent X", category = "SDF", order = 1)
    private double halfX = 4.0d;
    @NodeProperty(displayName = "Half Extent Y", category = "SDF", order = 2)
    private double halfY = 4.0d;
    @NodeProperty(displayName = "Half Extent Z", category = "SDF", order = 3)
    private double halfZ = 4.0d;

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_HALF_EXTENTS_ID = "input_half_extents";
    private static final String OUTPUT_SDF_ID = "output_sdf";

    public SdfBoxNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_box");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Box center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_HALF_EXTENTS_ID, "Half Extents", "Box half extents as vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Box signed distance field", NodeDataType.SDF, this));
        addValidAndErrorOutputs("True when center and extents are valid");
    }

    @Override
    public String getDescription() {
        return "Builds an axis-aligned box signed-distance-field primitive from center and half extents";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveOptionalPoint(INPUT_CENTER_ID, null);
        if (center == null) {
            writeFailure(isPortConnected(INPUT_CENTER_ID)
                ? "Center input must be a finite point"
                : "Box requires a finite center");
            return;
        }

        Vector3d propertyExtents = new Vector3d(halfX, halfY, halfZ);
        Vector3d ext = resolveOptionalVector(INPUT_HALF_EXTENTS_ID, propertyExtents);
        if (ext == null) {
            writeFailure("Half extents must be a finite vector");
            return;
        }
        if (!(ext.x > 0.0d) || !(ext.y > 0.0d) || !(ext.z > 0.0d)) {
            writeFailure("Box half extents must be finite and > 0 on every axis");
            return;
        }

        SignedDistanceFieldData sdf = new BoxSdfData(center, new Vector3d(ext));
        outputValues.put(OUTPUT_SDF_ID, sdf);
        markSuccess();
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_SDF_ID);
        markInvalid(error);
    }
}
