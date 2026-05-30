package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deconstructs architectural opening geometry into flattened components and bounds.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.deconstruct_opening",
    displayName = "Deconstruct Architectural Opening",
    description = "Flattens architectural opening geometry into component lists and bounds",
    category = "geometry.architectural_primitives",
    order = 11
)
public class DeconstructArchitecturalOpeningNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_COMPONENTS_ID = "output_components";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_BOUNDS_ID = "output_bounds";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructArchitecturalOpeningNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.deconstruct_opening");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to deconstruct", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_COMPONENTS_ID, "Components", "Flattened component geometry list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Total number of flattened components", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_ID, "Bounds", "Bounding region for the input geometry", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Axis-aligned bounding box for the input geometry", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when geometry was provided", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Flattens architectural opening geometry into component lists and bounds";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeInvalid();
            return;
        }

        List<GeometryData> components = flattenGeometry(geometry);
        outputValues.put(OUTPUT_COMPONENTS_ID, List.copyOf(components));
        outputValues.put(OUTPUT_COUNT_ID, components.size());
        outputValues.put(OUTPUT_BOUNDS_ID, GeometryVoxelizer.createBoundingRegion(geometry));
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, GeometryVoxelizer.createBoundingBox(GeometryVoxelizer.createBoundingRegion(geometry)));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private List<GeometryData> flattenGeometry(GeometryData geometry) {
        List<GeometryData> flattened = new ArrayList<>();
        appendGeometry(flattened, geometry);
        return flattened;
    }

    private void appendGeometry(List<GeometryData> target, GeometryData geometry) {
        if (geometry == null) {
            return;
        }
        if (geometry instanceof CompositeGeometryData composite) {
            for (GeometryData child : composite.getGeometries()) {
                appendGeometry(target, child);
            }
            return;
        }
        target.add(geometry);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_COMPONENTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_BOUNDS_ID, null);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}