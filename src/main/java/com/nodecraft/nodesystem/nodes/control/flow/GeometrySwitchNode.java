package com.nodecraft.nodesystem.nodes.control.flow;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Selects one geometry from multiple candidates by index.
 */
@NodeInfo(
    id = "control.flow.geometry_switch",
    displayName = "Geometry Switch",
    description = "Selects one geometry input by index",
    category = "control.flow"
)
public class GeometrySwitchNode extends BaseNode {

    private static final String INPUT_INDEX_ID = "input_index";
    private static final String INPUT_GEOMETRY_0_ID = "input_geometry_0";
    private static final String INPUT_GEOMETRY_1_ID = "input_geometry_1";
    private static final String INPUT_GEOMETRY_2_ID = "input_geometry_2";
    private static final String INPUT_GEOMETRY_3_ID = "input_geometry_3";
    private static final String INPUT_DEFAULT_ID = "input_default";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_MATCHED_INDEX_ID = "output_matched_index";

    public GeometrySwitchNode() {
        super(UUID.randomUUID(), "control.flow.geometry_switch");

        addInputPort(new BasePort(INPUT_INDEX_ID, "Index", "Geometry selection index (0-3)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_0_ID, "Geometry 0", "Geometry input 0", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_1_ID, "Geometry 1", "Geometry input 1", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_2_ID, "Geometry 2", "Geometry input 2", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_3_ID, "Geometry 3", "Geometry input 3", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_DEFAULT_ID, "Default", "Fallback geometry", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Selected geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_MATCHED_INDEX_ID, "Matched Index", "Actual selected input index", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Selects one geometry input by index";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object indexObj = inputValues.get(INPUT_INDEX_ID);
        int index = indexObj instanceof Number number ? number.intValue() : 0;

        String[] geometryKeys = {
            INPUT_GEOMETRY_0_ID,
            INPUT_GEOMETRY_1_ID,
            INPUT_GEOMETRY_2_ID,
            INPUT_GEOMETRY_3_ID
        };

        GeometryData result = null;
        int matchedIndex = -1;

        if (index >= 0 && index < geometryKeys.length) {
            Object value = inputValues.get(geometryKeys[index]);
            if (value instanceof GeometryData geometry) {
                result = geometry;
                matchedIndex = index;
            }
        }

        if (result == null) {
            Object fallback = inputValues.get(INPUT_DEFAULT_ID);
            if (fallback instanceof GeometryData geometry) {
                result = geometry;
            }
            matchedIndex = -1;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, result);
        outputValues.put(OUTPUT_MATCHED_INDEX_ID, matchedIndex);
    }
}
