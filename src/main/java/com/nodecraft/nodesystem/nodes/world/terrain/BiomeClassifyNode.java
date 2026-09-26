package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.biome_classify",
    displayName = "Biome Classify",
    description = "Classifies a biome index using temperature, precipitation, and elevation. "
        + "Non-finite climate samples yield NaN (never a default biome).",
    category = "world.terrain",
    order = 14
)
public class BiomeClassifyNode extends BaseNode {

    private static final String INPUT_TEMPERATURE_FIELD_ID = "input_temperature_field";
    private static final String INPUT_PRECIPITATION_FIELD_ID = "input_precipitation_field";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";

    private static final String OUTPUT_BIOME_ID_FIELD_ID = "output_biome_id_field";
    private static final String OUTPUT_BIOME_LABELS_ID = "output_biome_labels";
    private static final String OUTPUT_BIOME_COUNT_ID = "output_biome_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private static final List<String> BIOME_LABELS = List.of(
        "Hot Desert",
        "Savanna",
        "Temperate Steppe",
        "Temperate Grassland",
        "Temperate Forest",
        "Tundra",
        "Boreal Forest",
        "Alpine",
        "Tropical Rainforest"
    );

    public BiomeClassifyNode() {
        super(UUID.randomUUID(), "world.terrain.biome_classify");

        addInputPort(new BasePort(INPUT_TEMPERATURE_FIELD_ID, "Temperature Field",
            "Temperature field in [0,1]", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_PRECIPITATION_FIELD_ID, "Precipitation Field",
            "Precipitation field in [0,1]", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field",
            "Height field used for alpine override", NodeDataType.SCALAR_FIELD, this));

        addOutputPort(new BasePort(OUTPUT_BIOME_ID_FIELD_ID, "Biome Id Field",
            "Biome class id encoded as scalar; NaN when climate samples are non-finite",
            NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_LABELS_ID, "Biome Labels",
            "Biome labels ordered by scalar id", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_COUNT_ID, "Biome Count",
            "Number of biome classes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "Whether classification wiring succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when classification failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object temperatureObj = inputValues.get(INPUT_TEMPERATURE_FIELD_ID);
        Object precipitationObj = inputValues.get(INPUT_PRECIPITATION_FIELD_ID);
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);

        if (!(temperatureObj instanceof ScalarFieldData temperatureField)
            || !(precipitationObj instanceof ScalarFieldData precipitationField)
            || !(heightObj instanceof ScalarFieldData heightField)) {
            outputValues.put(OUTPUT_BIOME_ID_FIELD_ID, null);
            outputValues.put(OUTPUT_BIOME_LABELS_ID, BIOME_LABELS);
            outputValues.put(OUTPUT_BIOME_COUNT_ID, BIOME_LABELS.size());
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Missing temperature, precipitation, or height field input.");
            return;
        }

        ScalarFieldData biomeIdField = point -> {
            double temperatureRaw = temperatureField.sampleScalar(point);
            double precipitationRaw = precipitationField.sampleScalar(point);
            double height = heightField.sampleScalar(point);
            if (!Double.isFinite(temperatureRaw)
                || !Double.isFinite(precipitationRaw)
                || !Double.isFinite(height)) {
                return Double.NaN;
            }

            double temperature = clamp01(temperatureRaw);
            double precipitation = clamp01(precipitationRaw);

            // Elevation override first.
            if (height > 0.72d) {
                return 7.0d; // Alpine
            }

            // Simplified Whittaker-style classification.
            if (temperature < 0.22d) {
                if (precipitation < 0.35d) {
                    return 5.0d; // Tundra
                }
                return 6.0d; // Boreal forest
            }

            if (temperature < 0.5d) {
                if (precipitation < 0.25d) {
                    return 2.0d; // Temperate steppe
                }
                if (precipitation < 0.55d) {
                    return 3.0d; // Temperate grassland
                }
                return 4.0d; // Temperate forest
            }

            if (precipitation < 0.2d) {
                return 0.0d; // Hot desert
            }
            if (precipitation < 0.45d) {
                return 1.0d; // Savanna
            }
            return 8.0d; // Tropical rainforest
        };

        outputValues.put(OUTPUT_BIOME_ID_FIELD_ID, biomeIdField);
        outputValues.put(OUTPUT_BIOME_LABELS_ID, BIOME_LABELS);
        outputValues.put(OUTPUT_BIOME_COUNT_ID, BIOME_LABELS.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
