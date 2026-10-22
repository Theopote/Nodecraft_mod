package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.gui.components.customui.CustomUIPropertyHistory;
import com.nodecraft.gui.components.customui.PreviewStatusPanelWidget;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlocksPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewRequest;
import com.nodecraft.nodesystem.preview.protocol.PreviewStyle;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.BlockStateResolver;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PlacementSubmitPlan;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Block-level ghost preview of the same placement set Apply would submit.
 * <p>
 * Uses {@link PlacementSubmitPlan} (Placement priority, last-write-wins dedupe). Each ghost carries
 * {@code blockId + BlockStateData} so oriented blocks (stairs, slabs, trapdoors) match Apply.
 * Display may sample for the render budget; {@code output_unique_count} always reports the full
 * Apply set size.
 */
@NodeInfo(
    effect = NodeEffect.PREVIEW_WRITE,
    id = "output.preview.preview_placements",
    displayName = "Preview Placements",
    description = "GHOST preview of Apply's PlacementSubmitPlan with full BlockState (placements win over "
        + "geometry; last-write-wins). May sample for display; unique count is the full submit set. "
        + "Does not write the world.",
    category = "output.preview",
    order = 2
)
public class PreviewPlacementsNode extends BaseCustomUINode {

    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_BLOCK_PLACEMENTS_ID = "input_block_placements";
    private static final String INPUT_BLOCK_PLACEMENTS_TREE_ID = "input_block_placements_tree";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";
    private static final String OUTPUT_SOURCE_ID = "output_source";
    private static final String OUTPUT_RAW_COUNT_ID = "output_raw_count";
    private static final String OUTPUT_UNIQUE_COUNT_ID = "output_unique_count";
    private static final String OUTPUT_SHOWN_COUNT_ID = "output_shown_count";
    private static final String OUTPUT_SAMPLED_ID = "output_sampled";
    private static final String OUTPUT_GEOMETRY_IGNORED_ID = "output_geometry_ignored";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Solid Geometry", category = "Geometry", order = 2)
    private boolean solidGeometry = true;

    @NodeProperty(displayName = "Transparency", category = "Preview", order = 3)
    private float transparency = 0.45f;

    @NodeProperty(displayName = "Show Outline", category = "Preview", order = 4)
    private boolean showOutline = true;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 5)
    private int duration = 30;

    private volatile String cachedPreviewId;

    public PreviewPlacementsNode() {
        super(UUID.randomUUID(), "output.preview.preview_placements");

        addInputPort(new BasePort(INPUT_BLOCK_PLACEMENTS_ID, "Block Placements",
            "Per-position placements (priority over Geometry; Geometry is not also previewed)",
            NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_PLACEMENTS_TREE_ID, "Block Placements Tree",
            "Tree placements (same priority as Block Placements over Geometry)", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks",
            "Block list when placements are empty", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Unified geometry to voxelize when placements are empty", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry",
            "Box geometry to voxelize when placements are empty", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry",
            "Cylinder geometry to voxelize when placements are empty", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry",
            "Sphere geometry to voxelize when placements are empty", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry",
            "Torus geometry to voxelize when placements are empty", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type",
            "Uniform block for geometry/block-list path", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether a ghost preview is active", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_SOURCE_ID, "Source",
            "PLACEMENTS | GEOMETRY_VOXELS | EMPTY | INVALID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_RAW_COUNT_ID, "Raw Count",
            "Source placement instructions before last-write-wins dedupe", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_UNIQUE_COUNT_ID, "Unique Count",
            "Full Apply submit set size after dedupe (not reduced by display sampling)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SHOWN_COUNT_ID, "Shown Count",
            "Ghost cells actually rendered (may be sampled)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SAMPLED_ID, "Sampled",
            "True when display used a subset of the unique Apply set", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_IGNORED_ID, "Geometry Ignored",
            "True when Geometry was skipped because Placements took priority", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure or empty reason", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
            cachedPreviewId = null;
            publish(false, null, PlacementSubmitPlan.Source.EMPTY, 0, 0, 0, false, false, "Preview disabled");
            return;
        }

        Object placementsObj = inputValues.get(INPUT_BLOCK_PLACEMENTS_ID);
        Object placementsTreeObj = inputValues.get(INPUT_BLOCK_PLACEMENTS_TREE_ID);
        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        Object boxGeometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        Object cylinderGeometryObj = inputValues.get(INPUT_CYLINDER_GEOMETRY_ID);
        Object sphereGeometryObj = inputValues.get(INPUT_SPHERE_GEOMETRY_ID);
        Object torusGeometryObj = inputValues.get(INPUT_TORUS_GEOMETRY_ID);
        Object blockTypeObj = inputValues.get(INPUT_BLOCK_TYPE_ID);
        String blockType = blockTypeObj instanceof String s && !s.isBlank() ? s : "minecraft:stone";

        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                placementsObj,
                placementsTreeObj,
                blocksObj,
                geometryObj,
                boxGeometryObj,
                cylinderGeometryObj,
                sphereGeometryObj,
                torusGeometryObj,
                blockType,
                solidGeometry
        );

        if (!plan.valid()) {
            PreviewManager.hideNodePreviews(getId().toString());
            cachedPreviewId = null;
            publish(false, null, plan.source(), plan.rawEntryCount(), 0, 0, false,
                    plan.geometryIgnoredDueToPlacements(), plan.error());
            return;
        }
        if (plan.entries().isEmpty()) {
            PreviewManager.hideNodePreviews(getId().toString());
            cachedPreviewId = null;
            publish(false, null, plan.source(), plan.rawEntryCount(), 0, 0, false,
                    plan.geometryIgnoredDueToPlacements(), "No placements to preview");
            return;
        }

        GhostSample sample = sampleGhosts(plan.entries(), GenerationLimits.MAX_PREVIEW_BLOCKS);
        PreviewStyle style = PreviewStyle.forGhostBlocks(
                1.0f, 1.0f, 1.0f,
                Math.max(0.0f, Math.min(1.0f, transparency)),
                showOutline,
                null,
                2.0f,
                0.1f,
                Math.max(1, duration) * 20
        );
        String previewId = PreviewManager.showPreview(
                new PreviewRequest(
                        getId().toString(),
                        new PreviewBlocksPayload(sample.ghosts()),
                        style,
                        PreviewBackend.GHOST,
                        context)
        );
        cachedPreviewId = previewId;
        boolean success = previewId != null && !sample.ghosts().isEmpty();
        publish(
                success,
                previewId,
                plan.source(),
                plan.rawEntryCount(),
                plan.uniqueCount(),
                sample.ghosts().size(),
                sample.sampled(),
                plan.geometryIgnoredDueToPlacements(),
                success ? "" : "Preview renderer rejected the payload"
        );
    }

    /**
     * Builds GHOST cells from a unique plan entry list. Package-visible for unit tests.
     * Sampled results are a stride subset; each ghost preserves full BlockState via stateData.
     */
    static GhostSample sampleGhosts(List<PlacementSubmitPlan.Entry> uniqueEntries, int maxShown) {
        if (uniqueEntries == null || uniqueEntries.isEmpty() || maxShown <= 0) {
            return new GhostSample(List.of(), false);
        }
        int uniqueTotal = uniqueEntries.size();
        boolean sampled = uniqueTotal > maxShown;
        int stride = sampled ? Math.max(1, (int) Math.ceil(uniqueTotal / (double) maxShown)) : 1;
        List<PreviewBlock> ghosts = new ArrayList<>(Math.min(uniqueTotal, maxShown));
        for (int i = 0; i < uniqueTotal && ghosts.size() < maxShown; i += stride) {
            PreviewBlock ghost = toPreviewBlock(uniqueEntries.get(i));
            if (ghost != null) {
                ghosts.add(ghost);
            }
        }
        return new GhostSample(List.copyOf(ghosts), sampled);
    }

    static @Nullable PreviewBlock toPreviewBlock(@Nullable PlacementSubmitPlan.Entry entry) {
        if (entry == null || entry.pos() == null || entry.state() == null) {
            return null;
        }
        BlockPos pos = entry.pos();
        BlockState state = entry.state();
        String blockId;
        try {
            blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        } catch (Throwable ignored) {
            return null;
        }
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        BlockStateData stateData = BlockStateResolver.toStateData(state);
        return new PreviewBlock(pos.getX(), pos.getY(), pos.getZ(), blockId, stateData);
    }

    private void publish(
            boolean success,
            @Nullable String previewId,
            PlacementSubmitPlan.Source source,
            int rawCount,
            int uniqueCount,
            int shownCount,
            boolean sampled,
            boolean geometryIgnored,
            String error
    ) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId);
        outputValues.put(OUTPUT_SOURCE_ID, source.name());
        outputValues.put(OUTPUT_RAW_COUNT_ID, rawCount);
        outputValues.put(OUTPUT_UNIQUE_COUNT_ID, uniqueCount);
        outputValues.put(OUTPUT_SHOWN_COUNT_ID, shownCount);
        outputValues.put(OUTPUT_SAMPLED_ID, sampled);
        outputValues.put(OUTPUT_GEOMETRY_IGNORED_ID, geometryIgnored);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isPreviewEnabled() {
        return previewEnabled;
    }

    public void setPreviewEnabled(boolean value) {
        if (previewEnabled != value) {
            previewEnabled = value;
            markDirty();
        }
    }

    @Override
    protected float calculateUIHeight() {
        return PreviewStatusPanelWidget.preferredHeight() + getMediumPadding();
    }

    @Override
    protected float calculateMinUIWidth() {
        return 176.0f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            float edgeMargin = l.toPixels(getSmallPadding());
            ImGui.setCursorPosX(ImGui.getCursorPosX() + edgeMargin);
            PreviewStatusPanelWidget.Counts counts = readOutputCounts();
            return PreviewStatusPanelWidget.render(
                previewEnabled,
                counts,
                () -> true,
                enabled -> {
                    boolean before = previewEnabled;
                    setPreviewEnabled(enabled);
                    CustomUIPropertyHistory.recordPropertyChange(
                        getId(), "previewEnabled", before, enabled, 0L);
                });
        });
    }

    private PreviewStatusPanelWidget.Counts readOutputCounts() {
        int raw = intOutput(OUTPUT_RAW_COUNT_ID);
        int unique = intOutput(OUTPUT_UNIQUE_COUNT_ID);
        int shown = intOutput(OUTPUT_SHOWN_COUNT_ID);
        boolean sampled = Boolean.TRUE.equals(outputValues.get(OUTPUT_SAMPLED_ID));
        Object errorObj = outputValues.get(OUTPUT_ERROR_ID);
        String error = errorObj instanceof String text && !text.isBlank() ? text : null;
        return new PreviewStatusPanelWidget.Counts(raw, unique, shown, sampled, error);
    }

    private int intOutput(String portId) {
        Object value = outputValues.get(portId);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("solidGeometry", solidGeometry);
        state.put("transparency", transparency);
        state.put("showOutline", showOutline);
        state.put("duration", duration);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("previewEnabled") instanceof Boolean value) {
            setPreviewEnabled(value);
        }
        if (map.get("solidGeometry") instanceof Boolean value) {
            solidGeometry = value;
        }
        if (map.get("transparency") instanceof Number value) {
            transparency = Math.max(0.0f, Math.min(1.0f, value.floatValue()));
        }
        if (map.get("showOutline") instanceof Boolean value) {
            showOutline = value;
        }
        if (map.get("duration") instanceof Number value) {
            duration = Math.max(1, value.intValue());
        }
        markDirty();
    }

    record GhostSample(List<PreviewBlock> ghosts, boolean sampled) {
    }
}
