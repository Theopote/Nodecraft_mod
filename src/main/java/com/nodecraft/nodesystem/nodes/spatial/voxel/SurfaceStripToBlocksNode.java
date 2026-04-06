package com.nodecraft.nodesystem.nodes.spatial.voxel;

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
    id = "spatial.voxel.surface_strip_to_blocks",
    displayName = "Surface Strip To Blocks",
    description = "Approximates a surface strip as a sampled block lattice using section edges and rails",
    category = "spatial.voxel"
)
public class SurfaceStripToBlocksNode extends BaseNode {

    @NodeProperty(displayName = "Longitudinal Steps", category = "Sampling", order = 1)
    private int longitudinalSteps = 4;

    @NodeProperty(displayName = "Include Section Edges", category = "Sampling", order = 2)
    private boolean includeSectionEdges = true;

    @NodeProperty(displayName = "Include Rails", category = "Sampling", order = 3)
    private boolean includeRails = true;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SurfaceStripToBlocksNode() {
        super(UUID.randomUUID(), "spatial.voxel.surface_strip_to_blocks");

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
            blocks = SurfaceStripBridge.voxelize(surfaceStrip, longitudinalSteps, includeSectionEdges, includeRails);
            region = SurfaceStripBridge.createBoundingRegion(surfaceStrip);
            valid = true;
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
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

    public boolean isIncludeSectionEdges() {
        return includeSectionEdges;
    }

    public void setIncludeSectionEdges(boolean includeSectionEdges) {
        if (this.includeSectionEdges != includeSectionEdges) {
            this.includeSectionEdges = includeSectionEdges;
            markDirty();
        }
    }

    public boolean isIncludeRails() {
        return includeRails;
    }

    public void setIncludeRails(boolean includeRails) {
        if (this.includeRails != includeRails) {
            this.includeRails = includeRails;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("longitudinalSteps", longitudinalSteps);
        state.put("includeSectionEdges", includeSectionEdges);
        state.put("includeRails", includeRails);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("longitudinalSteps") instanceof Number value) {
            setLongitudinalSteps(value.intValue());
        }
        if (map.get("includeSectionEdges") instanceof Boolean value) {
            setIncludeSectionEdges(value);
        }
        if (map.get("includeRails") instanceof Boolean value) {
            setIncludeRails(value);
        }
    }
}
