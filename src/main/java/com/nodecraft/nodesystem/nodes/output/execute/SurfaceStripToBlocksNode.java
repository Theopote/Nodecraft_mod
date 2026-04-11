package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.SurfaceStripBridge;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.execute.bake_surface_strip_to_blocks",
    displayName = "Surface Strip To Blocks",
    description = "Approximates a surface strip as a sampled block lattice using section edges and rails",
    category = "output.execute"
)
public class SurfaceStripToBlocksNode extends BaseNode {

    @NodeProperty(displayName = "Mode", category = "Sampling", order = 1)
    private SurfaceStripBridge.BridgeMode mode = SurfaceStripBridge.BridgeMode.LATTICE;

    @NodeProperty(displayName = "Longitudinal Steps", category = "Sampling", order = 2)
    private int longitudinalSteps = 4;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SurfaceStripToBlocksNode() {
        super(UUID.randomUUID(), "output.execute.bake_surface_strip_to_blocks");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to approximate on the block grid", NodeDataType.SURFACE_STRIP, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Approximated block lattice for the surface strip", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the surface strip", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a surface strip was resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Approximates a surface strip as a sampled block lattice using section edges and rails";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        BlockPosList blocks = new BlockPosList();
        RegionData region = null;
        boolean valid = false;

        if (surfaceStripObj instanceof SurfaceStripData surfaceStrip) {
            blocks = SurfaceStripBridge.voxelize(surfaceStrip, longitudinalSteps, mode);
            region = SurfaceStripBridge.createBoundingRegion(surfaceStrip);
            valid = true;
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    public SurfaceStripBridge.BridgeMode getMode() {
        return mode;
    }

    public void setMode(SurfaceStripBridge.BridgeMode mode) {
        SurfaceStripBridge.BridgeMode resolved = mode == null ? SurfaceStripBridge.BridgeMode.LATTICE : mode;
        if (this.mode != resolved) {
            this.mode = resolved;
            markDirty();
        }
    }

    public void setModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setMode(SurfaceStripBridge.BridgeMode.LATTICE);
            return;
        }
        try {
            setMode(SurfaceStripBridge.BridgeMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setMode(SurfaceStripBridge.BridgeMode.LATTICE);
        }
    }

    public int getLongitudinalSteps() {
        return longitudinalSteps;
    }

    public void setLongitudinalSteps(int longitudinalSteps) {
        int resolved = Math.max(1, longitudinalSteps);
        if (this.longitudinalSteps != resolved) {
            this.longitudinalSteps = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("mode", mode.name());
        state.put("longitudinalSteps", longitudinalSteps);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("mode") instanceof String value) {
            setModeString(value);
        } else {
            boolean hasSections = !(map.get("includeSectionEdges") instanceof Boolean value) || value;
            boolean hasRails = !(map.get("includeRails") instanceof Boolean value) || value;
            if (hasSections && hasRails) {
                setMode(SurfaceStripBridge.BridgeMode.LATTICE);
            } else if (hasSections) {
                setMode(SurfaceStripBridge.BridgeMode.SECTIONS_ONLY);
            } else if (hasRails) {
                setMode(SurfaceStripBridge.BridgeMode.RAILS_ONLY);
            }
        }
        if (map.get("longitudinalSteps") instanceof Number value) {
            setLongitudinalSteps(value.intValue());
        }
    }
}
