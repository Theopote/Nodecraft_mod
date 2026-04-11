package com.nodecraft.nodesystem.nodes.utilities.legacy.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Ellipsoid generator that supports independent radii on X/Y/Z axes.
 */
@NodeInfo(
    id = "spatial.generators.ellipsoid_blocks",
    displayName = "Ellipsoid Generator",
    description = "Generates an ellipsoid block volume with independent axis radii.",
    category = "utilities.legacy.spatial.generators"
)
public class EllipsoidBlocksNode extends BaseNode {

    private boolean hollow = false;
    private int thickness = 1;

    // ---           IDs ---
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_X_ID = "input_radius_x";
    private static final String INPUT_RADIUS_Y_ID = "input_radius_y";
    private static final String INPUT_RADIUS_Z_ID = "input_radius_z";
    private static final String INPUT_HOLLOW_ID = "input_hollow";
    private static final String INPUT_THICKNESS_ID = "input_thickness";

    // ---           IDs ---
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public EllipsoidBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.ellipsoid_blocks");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Ellipsoid center point", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_RADIUS_X_ID, "Radius X", "Radius along X axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_Y_ID, "Radius Y", "Radius along Y axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_Z_ID, "Radius Z", "Radius along Z axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HOLLOW_ID, "Hollow", "Whether ellipsoid is hollow", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Shell thickness when hollow", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Generated ellipsoid blocks", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated blocks", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Generates an ellipsoid block volume with independent axis radii.";
    }

    @Override
    public String getDisplayName() {
        return "Ellipsoid (Blocks)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object rxObj = inputValues.get(INPUT_RADIUS_X_ID);
        Object ryObj = inputValues.get(INPUT_RADIUS_Y_ID);
        Object rzObj = inputValues.get(INPUT_RADIUS_Z_ID);
        Object hollowObj = inputValues.get(INPUT_HOLLOW_ID);
        Object thicknessObj = inputValues.get(INPUT_THICKNESS_ID);

        BlockPosList result = new BlockPosList();

        boolean isHollow = (hollowObj instanceof Boolean) ? (Boolean) hollowObj : this.hollow;
        int shellThickness = (thicknessObj instanceof Number) ? Math.max(1, ((Number) thicknessObj).intValue()) : this.thickness;

        if (centerObj instanceof BlockPos &&
            rxObj instanceof Number &&
            ryObj instanceof Number &&
            rzObj instanceof Number) {

            BlockPos center = (BlockPos) centerObj;
            double rx = Math.max(1, ((Number) rxObj).doubleValue());
            double ry = Math.max(1, ((Number) ryObj).doubleValue());
            double rz = Math.max(1, ((Number) rzObj).doubleValue());

            int cx = center.getX();
            int cy = center.getY();
            int cz = center.getZ();

            int maxR = (int) Math.ceil(Math.max(rx, Math.max(ry, rz)));

            for (int dx = -maxR; dx <= maxR; dx++) {
                for (int dy = -maxR; dy <= maxR; dy++) {
                    for (int dz = -maxR; dz <= maxR; dz++) {
                        //          : (x/rx)^2 + (y/ry)^2 + (z/rz)^2 <= 1
                        double dist = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);

                        if (dist <= 1.0) {
                            if (isHollow) {
                                double irx = Math.max(0, rx - shellThickness);
                                double iry = Math.max(0, ry - shellThickness);
                                double irz = Math.max(0, rz - shellThickness);
                                double innerDist = (irx > 0 && iry > 0 && irz > 0)
                                    ? (dx * dx) / (irx * irx) + (dy * dy) / (iry * iry) + (dz * dz) / (irz * irz)
                                    : 2.0;
                                if (innerDist >= 1.0) {
                                    result.add(new BlockPos(cx + dx, cy + dy, cz + dz));
                                }
                            } else {
                                result.add(new BlockPos(cx + dx, cy + dy, cz + dz));
                            }
                        }
                    }
                }
            }
        }

        outputValues.put(OUTPUT_BLOCKS_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
    }

    // --- Getters/Setters ---
    public boolean isHollow() { return hollow; }
    public void setHollow(boolean hollow) { this.hollow = hollow; markDirty(); }
    public int getThickness() { return thickness; }
    public void setThickness(int thickness) { this.thickness = Math.max(1, thickness); markDirty(); }

    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("hollow", hollow);
        state.put("thickness", thickness);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.get("hollow") instanceof Boolean) setHollow((Boolean) m.get("hollow"));
            if (m.get("thickness") instanceof Number) setThickness(((Number) m.get("thickness")).intValue());
        }
    }
}
