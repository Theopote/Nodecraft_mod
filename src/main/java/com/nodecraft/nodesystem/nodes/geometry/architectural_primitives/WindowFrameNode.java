package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parametric hollow window frame (four bars) in local X/Y/Z space for placement on frames.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.window_frame",
    displayName = "Window Frame",
    description = "Generates a hollow window frame solid aligned to local X/Y/Z for placement on frames",
    category = "geometry.architectural_primitives",
    order = 18
)
public class WindowFrameNode extends BaseNode {

    private static final String INPUT_FRAME_WIDTH_ID = "input_frame_width";
    private static final String INPUT_FRAME_HEIGHT_ID = "input_frame_height";
    private static final String INPUT_FRAME_THICKNESS_ID = "input_frame_thickness";
    private static final String INPUT_DEPTH_ID = "input_depth";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private static final Vector3d AXIS_X = new Vector3d(1.0d, 0.0d, 0.0d);
    private static final Vector3d AXIS_Y = new Vector3d(0.0d, 1.0d, 0.0d);
    private static final Vector3d AXIS_Z = new Vector3d(0.0d, 0.0d, 1.0d);

    public WindowFrameNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.window_frame");

        addInputPort(new BasePort(INPUT_FRAME_WIDTH_ID, "Frame Width",
            "Outer frame width (matches window opening width)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_FRAME_HEIGHT_ID, "Frame Height",
            "Outer frame height (matches window opening height)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_FRAME_THICKNESS_ID, "Frame Thickness",
            "Width of each frame bar", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPTH_ID, "Depth",
            "Frame depth along local Z", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry",
            "Hollow frame solid centered at origin", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a valid frame could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a hollow window frame solid aligned to local X/Y/Z for placement on frames";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Double frameWidth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_FRAME_WIDTH_ID, 1.0d);
        if (frameWidth == null) {
            writeInvalid("Frame Width must be a positive finite number");
            return;
        }
        Double frameHeight = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_FRAME_HEIGHT_ID, 1.0d);
        if (frameHeight == null) {
            writeInvalid("Frame Height must be a positive finite number");
            return;
        }
        Double barThickness = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_FRAME_THICKNESS_ID, 0.1d);
        if (barThickness == null) {
            writeInvalid("Frame Thickness must be a positive finite number");
            return;
        }
        Double depth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_DEPTH_ID, 0.15d);
        if (depth == null) {
            writeInvalid("Depth must be a positive finite number");
            return;
        }

        if (barThickness * 2.0d >= frameWidth || barThickness * 2.0d >= frameHeight) {
            writeInvalid("Frame Thickness is too large for the outer frame size");
            return;
        }

        List<BoxGeometryData> bars = buildFrameBars(frameWidth, frameHeight, barThickness, depth);
        GeometryData geometry = GeometryOutputUtils.packGeometry(bars);
        if (geometry == null) {
            writeInvalid("Could not generate frame geometry");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    static List<BoxGeometryData> buildFrameBars(double frameWidth, double frameHeight, double barThickness, double depth) {
        double halfW = frameWidth / 2.0d;
        double halfH = frameHeight / 2.0d;
        double halfT = barThickness / 2.0d;
        double halfD = depth / 2.0d;

        List<BoxGeometryData> bars = new ArrayList<>(4);
        bars.add(ArchitecturalPrimitiveSupport.createOrientedBox(
            new Vector3d(-halfW + halfT, 0.0d, 0.0d),
            new Vector3d(halfT, halfH, halfD), AXIS_X, AXIS_Y, AXIS_Z));
        bars.add(ArchitecturalPrimitiveSupport.createOrientedBox(
            new Vector3d(halfW - halfT, 0.0d, 0.0d),
            new Vector3d(halfT, halfH, halfD), AXIS_X, AXIS_Y, AXIS_Z));
        bars.add(ArchitecturalPrimitiveSupport.createOrientedBox(
            new Vector3d(0.0d, halfH - halfT, 0.0d),
            new Vector3d(halfW, halfT, halfD), AXIS_X, AXIS_Y, AXIS_Z));
        bars.add(ArchitecturalPrimitiveSupport.createOrientedBox(
            new Vector3d(0.0d, -halfH + halfT, 0.0d),
            new Vector3d(halfW, halfT, halfD), AXIS_X, AXIS_Y, AXIS_Z));
        return bars;
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
