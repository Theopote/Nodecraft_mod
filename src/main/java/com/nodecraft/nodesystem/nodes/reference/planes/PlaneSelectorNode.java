package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PlaneUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.planes.world_plane",
    displayName = "World Plane",
    description = "Creates a standard XY, YZ, or XZ world plane with a Point-compatible origin",
    category = "reference.planes",
    order = 0
)
public class PlaneSelectorNode extends BaseNode {

    public enum PlanePreset {
        XY,
        YZ,
        XZ
    }

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(
        displayName = "Plane Preset",
        category = "Plane",
        order = 1,
        description = "Selects which standard world plane to construct"
    )
    private PlanePreset planePreset = PlanePreset.XZ;

    @NodeProperty(
        displayName = "Origin X",
        category = "Origin",
        order = 2,
        description = "Fallback X coordinate when no origin input is connected"
    )
    private double originX = 0.0d;

    @NodeProperty(
        displayName = "Origin Y",
        category = "Origin",
        order = 3,
        description = "Fallback Y coordinate when no origin input is connected"
    )
    private double originY = 0.0d;

    @NodeProperty(
        displayName = "Origin Z",
        category = "Origin",
        order = 4,
        description = "Fallback Z coordinate when no origin input is connected"
    )
    private double originZ = 0.0d;

    public PlaneSelectorNode() {
        super(UUID.randomUUID(), "reference.planes.world_plane");

        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin",
            "Optional plane origin (Point). Block Pos / Vector / Position connect via type conversion.",
            NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane",
            "Constructed plane data", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a valid plane was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates a standard XY, YZ, or XZ world plane with a Point-compatible origin";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean originConnected = inputValues.get(INPUT_ORIGIN_ID) != null;
        Vector3d originVector;
        if (originConnected) {
            originVector = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
            if (!PlaneUtils.isFinite(originVector)) {
                writeInvalid();
                return;
            }
        } else {
            originVector = new Vector3d(originX, originY, originZ);
        }

        PlaneData plane = PlaneUtils.fromOriginNormal(originVector, resolveNormal());
        if (plane == null) {
            writeInvalid();
            return;
        }
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d resolveNormal() {
        return switch (planePreset) {
            case XY -> new Vector3d(0.0d, 0.0d, 1.0d);
            case YZ -> new Vector3d(1.0d, 0.0d, 0.0d);
            case XZ -> new Vector3d(0.0d, 1.0d, 0.0d);
        };
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public PlanePreset getPlanePreset() {
        return planePreset;
    }

    public void setPlanePreset(PlanePreset planePreset) {
        if (planePreset != null && this.planePreset != planePreset) {
            this.planePreset = planePreset;
            markDirty();
        }
    }

    public double getOriginX() {
        return originX;
    }

    public void setOriginX(double originX) {
        if (Double.isFinite(originX) && this.originX != originX) {
            this.originX = originX;
            markDirty();
        }
    }

    public double getOriginY() {
        return originY;
    }

    public void setOriginY(double originY) {
        if (Double.isFinite(originY) && this.originY != originY) {
            this.originY = originY;
            markDirty();
        }
    }

    public double getOriginZ() {
        return originZ;
    }

    public void setOriginZ(double originZ) {
        if (Double.isFinite(originZ) && this.originZ != originZ) {
            this.originZ = originZ;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("planePreset", planePreset.name());
        state.put("originX", originX);
        state.put("originY", originY);
        state.put("originZ", originZ);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        if (map.get("planePreset") instanceof String preset) {
            try {
                setPlanePreset(PlanePreset.valueOf(preset));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (map.get("originX") instanceof Number x) {
            setOriginX(x.doubleValue());
        }
        if (map.get("originY") instanceof Number y) {
            setOriginY(y.doubleValue());
        }
        if (map.get("originZ") instanceof Number z) {
            setOriginZ(z.doubleValue());
        }
    }
}
