package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "reference.planes.world_plane",
    displayName = "World Plane",
    description = "Creates a standard XY, YZ, or XZ world plane with a configurable origin",
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
    private static final String OUTPUT_ORIGIN_ID = "output_origin";
    private static final String OUTPUT_ORIGIN_VECTOR_ID = "output_origin_vector";
    private static final String OUTPUT_NORMAL_ID = "output_normal";

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
    private int originX = 0;

    @NodeProperty(
        displayName = "Origin Y",
        category = "Origin",
        order = 3,
        description = "Fallback Y coordinate when no origin input is connected"
    )
    private int originY = 0;

    @NodeProperty(
        displayName = "Origin Z",
        category = "Origin",
        order = 4,
        description = "Fallback Z coordinate when no origin input is connected"
    )
    private int originZ = 0;

    public PlaneSelectorNode() {
        super(UUID.randomUUID(), "reference.planes.world_plane");

        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin",
            "Optional origin for the selected world plane. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane",
            "Constructed plane data", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_ORIGIN_ID, "Origin",
            "Plane origin as a block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_ORIGIN_VECTOR_ID, "Origin Vector",
            "Plane origin as a vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal",
            "Plane normal vector", NodeDataType.VECTOR, this));
    }

    @Override
    public String getDescription() {
        return "Creates a standard XY, YZ, or XZ world plane with a configurable origin";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPos origin = resolveOrigin(inputValues.get(INPUT_ORIGIN_ID));
        Vector3d originVector = new Vector3d(origin.getX(), origin.getY(), origin.getZ());
        Vector3d normal = resolveNormal();
        PlaneData plane = new PlaneData(
            new Vector3d(originVector),
            new Vector3d(normal)
        );

        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_ORIGIN_ID, origin);
        outputValues.put(OUTPUT_ORIGIN_VECTOR_ID, originVector);
        outputValues.put(OUTPUT_NORMAL_ID, normal);
    }

    private BlockPos resolveOrigin(Object originObj) {
        BlockPos fallback = new BlockPos(originX, originY, originZ);
        if (originObj instanceof BlockPos blockPos) {
            return blockPos.toImmutable();
        }
        return PlaneUtils.toBlockPos(PlaneUtils.resolvePoint(originObj), fallback);
    }

    private Vector3d resolveNormal() {
        return switch (planePreset) {
            case XY -> new Vector3d(0.0d, 0.0d, 1.0d);
            case YZ -> new Vector3d(1.0d, 0.0d, 0.0d);
            case XZ -> new Vector3d(0.0d, 1.0d, 0.0d);
        };
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

    public int getOriginX() {
        return originX;
    }

    public void setOriginX(int originX) {
        if (this.originX != originX) {
            this.originX = originX;
            markDirty();
        }
    }

    public int getOriginY() {
        return originY;
    }

    public void setOriginY(int originY) {
        if (this.originY != originY) {
            this.originY = originY;
            markDirty();
        }
    }

    public int getOriginZ() {
        return originZ;
    }

    public void setOriginZ(int originZ) {
        if (this.originZ != originZ) {
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
            setOriginX(x.intValue());
        }
        if (map.get("originY") instanceof Number y) {
            setOriginY(y.intValue());
        }
        if (map.get("originZ") instanceof Number z) {
            setOriginZ(z.intValue());
        }
    }
}
