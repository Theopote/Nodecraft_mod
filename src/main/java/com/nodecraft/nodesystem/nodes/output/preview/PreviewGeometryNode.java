package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.*;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.nodesystem.util.SurfaceStripBridge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.preview.preview_geometry",
    displayName = "Preview Geometry",
    description = "Previews analytic geometry directly (semi-transparent fill + outline) before voxelization",
    category = "output.preview"
)
public class PreviewGeometryNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_CONE_GEOMETRY_ID = "input_cone_geometry";
    private static final String INPUT_FRUSTUM_CONE_GEOMETRY_ID = "input_frustum_cone_geometry";
    private static final String INPUT_ELLIPSOID_GEOMETRY_ID = "input_ellipsoid_geometry";
    private static final String INPUT_HEMISPHERE_GEOMETRY_ID = "input_hemisphere_geometry";
    private static final String INPUT_PRISM_GEOMETRY_ID = "input_prism_geometry";
    private static final String INPUT_TETRAHEDRON_GEOMETRY_ID = "input_tetrahedron_geometry";
    private static final String INPUT_OCTAHEDRON_GEOMETRY_ID = "input_octahedron_geometry";
    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";
    private static final String OUTPUT_PREVIEW_IDS_ID = "output_preview_ids";
    private static final String OUTPUT_PREVIEW_COUNT_ID = "output_preview_count";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Fill Color", category = "Preview", order = 2)
    private String fillColor = "#53D6FF";

    @NodeProperty(displayName = "Outline Color", category = "Preview", order = 3)
    private String outlineColor = "#102B33";

    @NodeProperty(displayName = "Show Fill", category = "Preview", order = 4)
    private boolean showFill = true;

    @NodeProperty(displayName = "Show Outline", category = "Preview", order = 5)
    private boolean showOutline = true;

    @NodeProperty(displayName = "Transparency", category = "Preview", order = 6)
    private float transparency = 0.35f;

    @NodeProperty(displayName = "Line Width", category = "Preview", order = 7)
    private float lineWidth = 1.6f;

    @NodeProperty(displayName = "Quality", category = "Preview", order = 8)
    private int quality = 20;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 9)
    private int duration = 30;

    @NodeProperty(displayName = "Surface Strip Radius", category = "Surface Strip", order = 10)
    private double surfaceStripRadius = 0.25d;

    @NodeProperty(displayName = "Surface Strip Steps", category = "Surface Strip", order = 11)
    private int surfaceStripSteps = 4;

    private volatile int cachedGeometrySignature = 0;
    private volatile int cachedOptionsSignature = 0;
    private volatile List<String> cachedPreviewIds = List.of();

    public PreviewGeometryNode() {
        super(UUID.randomUUID(), "output.preview.preview_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified analytic geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CONE_GEOMETRY_ID, "Cone Geometry", "Cone geometry data", NodeDataType.CONE_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_FRUSTUM_CONE_GEOMETRY_ID, "Frustum Cone Geometry", "Circular frustum (truncated cone) geometry data", NodeDataType.FRUSTUM_CONE_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_ELLIPSOID_GEOMETRY_ID, "Ellipsoid Geometry", "Ellipsoid geometry data", NodeDataType.ELLIPSOID_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_HEMISPHERE_GEOMETRY_ID, "Hemisphere Geometry", "Hemisphere geometry data", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PRISM_GEOMETRY_ID, "Prism Geometry", "Prism geometry data", NodeDataType.PRISM_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_TETRAHEDRON_GEOMETRY_ID, "Tetrahedron Geometry", "Tetrahedron geometry data", NodeDataType.TETRAHEDRON_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_OCTAHEDRON_GEOMETRY_ID, "Octahedron Geometry", "Octahedron geometry data", NodeDataType.OCTAHEDRON_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip approximated into preview geometry", NodeDataType.SURFACE_STRIP, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether preview is active", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_IDS_ID, "Preview IDs", "Active preview identifiers", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_COUNT_ID, "Preview Count", "Number of rendered geometry previews", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Forwarded geometry output", NodeDataType.GEOMETRY, this));
    }

    @Override
    public String getDescription() {
        return "Previews analytic geometry directly (semi-transparent fill + outline) before voxelization";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<GeometryData> geometries = resolveGeometryInputs();
        GeometryData geometry = geometries.isEmpty()
            ? null
            : (geometries.size() == 1 ? geometries.getFirst() : new CompositeGeometryData(geometries));
        List<String> previewIds = cachedPreviewIds;

        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
            clearPreviewCache();
            previewIds = List.of();
        } else if (!geometries.isEmpty()) {
            int geometrySignature = computeGeometrySignature(geometries);
            int optionsSignature = computeOptionsSignature();
            boolean previewDirty = geometrySignature != cachedGeometrySignature
                || optionsSignature != cachedOptionsSignature
                || previewIds.isEmpty();

            Color parsed = Color.fromHex(fillColor);
            Color outlineParsed = Color.fromHex(outlineColor);
            PreviewOptions options = new PreviewOptions()
                .setColor(parsed.getRed(), parsed.getGreen(), parsed.getBlue())
                .setTintColor(outlineParsed.getRed(), outlineParsed.getGreen(), outlineParsed.getBlue())
                .setOpacity(clamp01(transparency))
                .setShowFill(showFill)
                .setShowOutline(showOutline)
                .setLineWidth(Math.max(0.25f, lineWidth))
                .setDuration(Math.max(1, duration));
            options.particleDensity = Math.max(8, Math.min(64, quality));

            if (previewDirty) {
                List<String> refreshedIds = PreviewManager.showGeometrySurfaces(getId().toString(), geometries, options);
                previewIds = List.copyOf(refreshedIds);
                cachedPreviewIds = previewIds;
                cachedGeometrySignature = geometrySignature;
                cachedOptionsSignature = optionsSignature;
                NodeCraft.LOGGER.info(
                    "PreviewGeometryNode[{}] refreshed: geometries={}, previews={}, geometrySig={}, optionsSig={}",
                    getId(),
                    geometries.size(),
                    previewIds.size(),
                    geometrySignature,
                    optionsSignature
                );
            }
        } else {
            PreviewManager.hideNodePreviews(getId().toString());
            clearPreviewCache();
            previewIds = List.of();
        }

        outputValues.put(OUTPUT_SUCCESS_ID, !previewIds.isEmpty());
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewIds.isEmpty() ? null : previewIds.getFirst());
        outputValues.put(OUTPUT_PREVIEW_IDS_ID, List.copyOf(previewIds));
        outputValues.put(OUTPUT_PREVIEW_COUNT_ID, previewIds.size());
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
    }

    private int computeGeometrySignature(List<GeometryData> geometries) {
        int hash = geometries.size();
        for (GeometryData geometry : geometries) {
            if (geometry == null) {
                hash = 31 * hash + 1;
                continue;
            }
            hash = 31 * hash + geometry.getClass().getName().hashCode();
            hash = 31 * hash + geometry.hashCode();
        }
        return hash;
    }

    private int computeOptionsSignature() {
        int hash = 17;
        hash = 31 * hash + Boolean.hashCode(previewEnabled);
        hash = 31 * hash + (fillColor != null ? fillColor.hashCode() : 0);
        hash = 31 * hash + (outlineColor != null ? outlineColor.hashCode() : 0);
        hash = 31 * hash + Boolean.hashCode(showFill);
        hash = 31 * hash + Boolean.hashCode(showOutline);
        hash = 31 * hash + Float.floatToIntBits(transparency);
        hash = 31 * hash + Float.floatToIntBits(lineWidth);
        hash = 31 * hash + quality;
        hash = 31 * hash + duration;
        hash = 31 * hash + Double.hashCode(surfaceStripRadius);
        hash = 31 * hash + surfaceStripSteps;
        return hash;
    }

    private void clearPreviewCache() {
        cachedGeometrySignature = 0;
        cachedOptionsSignature = 0;
        cachedPreviewIds = List.of();
    }

    private List<GeometryData> resolveGeometryInputs() {
        List<GeometryData> geometries = new ArrayList<>();
        collectGeometryInput(inputValues.get(INPUT_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_BOX_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_CYLINDER_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_SPHERE_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_TORUS_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_CONE_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_FRUSTUM_CONE_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_ELLIPSOID_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_HEMISPHERE_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_PRISM_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_TETRAHEDRON_GEOMETRY_ID), geometries);
        collectGeometryInput(inputValues.get(INPUT_OCTAHEDRON_GEOMETRY_ID), geometries);

        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (surfaceStripObj instanceof SurfaceStripData surfaceStrip) {
            GeometryData bridgeGeometry = SurfaceStripBridge.toGeometry(
                surfaceStrip,
                Math.max(1, surfaceStripSteps),
                SurfaceStripBridge.BridgeMode.LATTICE,
                Math.max(0.0d, surfaceStripRadius)
            );
            collectGeometryInput(bridgeGeometry, geometries);
        }

        return geometries;
    }

    private void collectGeometryInput(@Nullable Object value, List<GeometryData> target) {
        if (value == null) {
            return;
        }
        if (value instanceof CompositeGeometryData composite) {
            for (GeometryData child : composite.getGeometries()) {
                collectGeometryInput(child, target);
            }
            return;
        }
        if (value instanceof DifferenceGeometryData difference) {
            collectGeometryInput(difference.getMinuend(), target);
            collectGeometryInput(difference.getSubtrahend(), target);
            return;
        }
        if (value instanceof IntersectionGeometryData intersection) {
            collectGeometryInput(intersection.getLeft(), target);
            collectGeometryInput(intersection.getRight(), target);
            return;
        }
        if (value instanceof GeometryData geometry) {
            target.add(geometry);
            return;
        }
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                collectGeometryInput(entry, target);
            }
        }
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("fillColor", fillColor);
        state.put("outlineColor", outlineColor);
        state.put("showFill", showFill);
        state.put("showOutline", showOutline);
        state.put("transparency", transparency);
        state.put("lineWidth", lineWidth);
        state.put("quality", quality);
        state.put("duration", duration);
        state.put("surfaceStripRadius", surfaceStripRadius);
        state.put("surfaceStripSteps", surfaceStripSteps);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        if (map.get("previewEnabled") instanceof Boolean value) {
            previewEnabled = value;
        }
        if (map.get("fillColor") instanceof String value) {
            fillColor = value;
        }
        if (map.get("outlineColor") instanceof String value) {
            outlineColor = value;
        }
        if (map.get("showFill") instanceof Boolean value) {
            showFill = value;
        }
        if (map.get("showOutline") instanceof Boolean value) {
            showOutline = value;
        }
        if (map.get("transparency") instanceof Number value) {
            transparency = clamp01(value.floatValue());
        }
        if (map.get("lineWidth") instanceof Number value) {
            lineWidth = Math.max(0.25f, value.floatValue());
        }
        if (map.get("quality") instanceof Number value) {
            quality = Math.max(8, Math.min(64, value.intValue()));
        }
        if (map.get("duration") instanceof Number value) {
            duration = Math.max(1, value.intValue());
        }
        if (map.get("surfaceStripRadius") instanceof Number value) {
            surfaceStripRadius = Math.max(0.0d, value.doubleValue());
        }
        if (map.get("surfaceStripSteps") instanceof Number value) {
            surfaceStripSteps = Math.max(1, value.intValue());
        }
        clearPreviewCache();
    }
}
