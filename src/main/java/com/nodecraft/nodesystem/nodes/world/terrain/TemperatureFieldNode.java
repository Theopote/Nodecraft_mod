package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.temperature_field",
    displayName = "Temperature Field",
    description = "Builds temperature from latitude bands and elevation lapse-rate cooling. "
        + "Unconnected Region defaults to continental soft domain ±4096. "
        + "Validates inputs at process time; lazy samples may propagate NaN to materialize consumers.",
    category = "world.terrain",
    order = 13
)
public class TemperatureFieldNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_EQUATOR_TEMP_ID = "input_equator_temp";
    private static final String INPUT_LAPSE_RATE_ID = "input_lapse_rate";

    private static final String OUTPUT_TEMPERATURE_FIELD_ID = "output_temperature_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Equator Temp", category = "Climate", order = 1)
    private double equatorTemp = 1.0d;

    @NodeProperty(displayName = "Lapse Rate", category = "Climate", order = 2)
    private double lapseRate = 0.18d;

    public TemperatureFieldNode() {
        super(UUID.randomUUID(), "world.terrain.temperature_field");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional region for latitude normalization; unconnected → continental ±4096", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Terrain elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_EQUATOR_TEMP_ID, "Equator Temp", "Base equatorial temperature", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LAPSE_RATE_ID, "Lapse Rate", "Temperature decrease with elevation", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_TEMPERATURE_FIELD_ID, "Temperature Field", "Temperature intensity field in [0,1]", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the temperature field was created", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when temperature field creation failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        if (!(heightObj instanceof ScalarFieldData heightField)) {
            publishInvalid("Missing height field input.");
            return;
        }

        RegionData region = TerrainNodeUtils.resolveOptionalRegion(this, INPUT_REGION_ID);
        if (TerrainNodeUtils.isInvalidRegionMarker(region)) {
            publishInvalid("Connected Region is incomplete or invalid.");
            return;
        }

        Double resolvedEquatorTempRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_EQUATOR_TEMP_ID, equatorTemp);
        if (resolvedEquatorTempRaw == null) {
            publishInvalid("Equator Temp must be a finite DOUBLE.");
            return;
        }

        Double resolvedLapseRateRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_LAPSE_RATE_ID, lapseRate);
        if (resolvedLapseRateRaw == null) {
            publishInvalid("Lapse Rate must be a finite DOUBLE.");
            return;
        }

        double resolvedEquatorTemp = clamp01(resolvedEquatorTempRaw);
        double resolvedLapseRate = Math.max(0.0d, resolvedLapseRateRaw);

        LatitudeBounds latitudeBounds = LatitudeBounds.fromRegion(region);

        ScalarFieldData temperatureField = point -> {
            double latitude01 = latitudeBounds.normalizedLatitude(point.z);
            double latitudeCooling = latitude01 * 0.75d;

            double elevation = heightField.sampleScalar(point);
            if (!Double.isFinite(elevation)) {
                return Double.NaN;
            }
            double elevation01 = clamp01((elevation + 1.0d) * 0.5d);
            double elevationCooling = elevation01 * resolvedLapseRate;

            double temp = resolvedEquatorTemp - latitudeCooling - elevationCooling;
            return clamp01(temp);
        };

        outputValues.put(OUTPUT_TEMPERATURE_FIELD_ID, temperatureField);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_TEMPERATURE_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private record LatitudeBounds(double minZ, double maxZ, double centerZ, double halfSpan) {

        private static LatitudeBounds fromRegion(@Nullable RegionData region) {
            if (region == null || !region.isComplete()) {
                return continentalDefault();
            }

            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min == null || max == null) {
                return continentalDefault();
            }

            double minZ = min.getZ();
            double maxZ = max.getZ();
            double centerZ = ((double) min.getZ() + (double) max.getZ()) * 0.5d;
            double halfSpan = Math.max(1.0d, Math.abs((long) max.getZ() - (long) min.getZ()) * 0.5d);
            return new LatitudeBounds(minZ, maxZ, centerZ, halfSpan);
        }

        private static LatitudeBounds continentalDefault() {
            double minZ = TerrainNodeUtils.CONTINENTAL_MIN_XZ;
            double maxZ = TerrainNodeUtils.CONTINENTAL_MAX_XZ;
            double centerZ = ((double) TerrainNodeUtils.CONTINENTAL_MIN_XZ + (double) TerrainNodeUtils.CONTINENTAL_MAX_XZ) * 0.5d;
            double halfSpan = Math.max(1.0d, Math.abs((long) TerrainNodeUtils.CONTINENTAL_MAX_XZ - (long) TerrainNodeUtils.CONTINENTAL_MIN_XZ) * 0.5d);
            return new LatitudeBounds(minZ, maxZ, centerZ, halfSpan);
        }

        private double normalizedLatitude(double z) {
            return Math.max(0.0d, Math.min(1.0d, Math.abs(z - centerZ) / halfSpan));
        }
    }
}
