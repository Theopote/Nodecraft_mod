package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates a straight staircase from a line segment.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.staircase",
    displayName = "Staircase",
    description = "Generates a straight staircase from a line segment",
    category = "geometry.architectural_primitives",
    order = 4
)
public class StaircaseNode extends BaseNode {

    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_STEP_COUNT_ID = "input_step_count";
    private static final String INPUT_STEP_RUN_ID = "input_step_run";
    private static final String INPUT_STEP_RISE_ID = "input_step_rise";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_LANDING_LENGTH_ID = "input_landing_length";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public StaircaseNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.staircase");

        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Straight path used for the staircase run", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_STEP_COUNT_ID, "Step Count", "Number of steps to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_STEP_RUN_ID, "Step Run", "Horizontal run of each step", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEP_RISE_ID, "Step Rise", "Vertical rise of each step", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Stair width measured across the run", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LANDING_LENGTH_ID, "Landing Length", "Optional top landing length", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the staircase steps", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of step solids created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid staircase could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a straight staircase from a line segment";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object lineObj = inputValues.get(INPUT_LINE_ID);

        GeometryData geometry = null;
        int count = 0;
        boolean valid = false;

        if (lineObj instanceof LineData line) {
            Vec3d startVec = line.getStart();
            Vec3d endVec = line.getEnd();
            ArchitecturalPrimitiveSupport.LineFrame frame = ArchitecturalPrimitiveSupport.resolveLineFrame(startVec, endVec);
            if (frame != null) {
                int stepCount = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_STEP_COUNT_ID), 1);
                double stepRun = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_STEP_RUN_ID), 1.0d);
                double stepRise = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_STEP_RISE_ID), 0.2d);
                double width = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_WIDTH_ID), 1.0d);
                double landingLength = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_LANDING_LENGTH_ID), 0.0d);

                List<GeometryData> steps = buildStairs(frame, stepCount, stepRun, stepRise, width, landingLength);
                if (!steps.isEmpty()) {
                    geometry = new CompositeGeometryData(steps);
                    count = steps.size();
                    valid = true;
                }
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private List<GeometryData> buildStairs(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        int stepCount,
        double stepRun,
        double stepRise,
        double width,
        double landingLength
    ) {
        List<GeometryData> results = new ArrayList<>(stepCount + 1);
        Vector3d halfWidth = new Vector3d(frame.sideAxis()).mul(width / 2.0d);

        for (int index = 0; index < stepCount; index++) {
            Vector3d center = new Vector3d(frame.start())
                .fma(stepRun * index + stepRun / 2.0d, frame.runAxis())
                .fma(stepRise * index + stepRise / 2.0d, frame.upAxis());

            Vector3d halfExtents = new Vector3d(stepRun / 2.0d, stepRise / 2.0d, width / 2.0d);
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.runAxis(), frame.upAxis(), frame.sideAxis()));
        }

        if (landingLength > 0.0d) {
            Vector3d landingCenter = new Vector3d(frame.start())
                .fma(stepRun * stepCount + landingLength / 2.0d, frame.runAxis())
                .fma(stepRise * stepCount + stepRise / 2.0d, frame.upAxis());
            Vector3d halfExtents = new Vector3d(landingLength / 2.0d, stepRise / 2.0d, width / 2.0d);
            results.add(ArchitecturalPrimitiveSupport.createOrientedBox(landingCenter, halfExtents, frame.runAxis(), frame.upAxis(), frame.sideAxis()));
        }

        return List.copyOf(results);
    }
}