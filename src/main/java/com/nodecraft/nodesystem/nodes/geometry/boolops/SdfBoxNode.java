package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxSdfData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_box",
    displayName = "SDF Box",
    description = "Builds an axis-aligned box signed-distance-field primitive from center and half extents",
    category = "geometry.boolean",
    order = 11
)
public class SdfBoxNode extends BaseNode {
    @NodeProperty(displayName = "Half Extent X", category = "SDF", order = 1)
    private double halfX = 4.0d;
    @NodeProperty(displayName = "Half Extent Y", category = "SDF", order = 2)
    private double halfY = 4.0d;
    @NodeProperty(displayName = "Half Extent Z", category = "SDF", order = 3)
    private double halfZ = 4.0d;

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_HALF_EXTENTS_ID = "input_half_extents";

    private static final String OUTPUT_SDF_ID = "output_sdf";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SdfBoxNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_box");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Box center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_HALF_EXTENTS_ID, "Half Extents", "Box half extents as vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Box signed distance field", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when center and extents are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds an axis-aligned box signed-distance-field primitive from center and half extents";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Vector3d extInput = SpatialValueResolver.resolveVector3d(inputValues.get(INPUT_HALF_EXTENTS_ID));
        Vector3d ext = extInput != null
            ? new Vector3d(Math.abs(extInput.x), Math.abs(extInput.y), Math.abs(extInput.z))
            : new Vector3d(Math.abs(halfX), Math.abs(halfY), Math.abs(halfZ));
        if (center == null || ext.lengthSquared() <= 0.0d) {
            outputValues.put(OUTPUT_SDF_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        SignedDistanceFieldData sdf = new BoxSdfData(center, ext);
        outputValues.put(OUTPUT_SDF_ID, sdf);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d resolvePoint(Object value) {
        return SpatialValueResolver.resolveVector3d(value);
    }
}
