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
    description = "Builds temperature from latitude bands and elevation lapse-rate cooling.",
    category = "world.terrain",
    order = 14
)
public class TemperatureFieldNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_EQUATOR_TEMP_ID = "input_equator_temp";
    private static final String INPUT_LAPSE_RATE_ID = "input_lapse_rate";

    private static final String OUTPUT_TEMPERATURE_FIELD_ID = "output_temperature_field";

    @NodeProperty(displayName = "Equator Temp", category = "Climate", order = 1)
    private double equatorTemp = 1.0d;

    @NodeProperty(displayName = "Lapse Rate", category = "Climate", order = 2)
    private double lapseRate = 0.18d;

    public TemperatureFieldNode() {
        super(UUID.randomUUID(), "world.terrain.temperature_field");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional region for latitude normalization", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Terrain elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_EQUATOR_TEMP_ID, "Equator Temp", "Base equatorial temperature", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LAPSE_RATE_ID, "Lapse Rate", "Temperature decrease with elevation", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_TEMPERATURE_FIELD_ID, "Temperature Field", "Temperature intensity field in [0,1]", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        if (!(heightObj instanceof ScalarFieldData heightField)) {
            outputValues.put(OUTPUT_TEMPERATURE_FIELD_ID, null);
            return;
        }

        RegionData region = inputValues.get(INPUT_REGION_ID) instanceof RegionData value ? value : null;
        double resolvedEquatorTemp = clamp01(getInputDouble(INPUT_EQUATOR_TEMP_ID, equatorTemp));
        double resolvedLapseRate = Math.max(0.0d, getInputDouble(INPUT_LAPSE_RATE_ID, lapseRate));

        LatitudeBounds latitudeBounds = LatitudeBounds.fromRegion(region);

        ScalarFieldData temperatureField = point -> {
            double latitude01 = latitudeBounds.normalizedLatitude(point.z);
            double latitudeCooling = latitude01 * 0.75d;

            double elevation = heightField.sampleScalar(point);
            double elevation01 = clamp01((elevation + 1.0d) * 0.5d);
            double elevationCooling = elevation01 * resolvedLapseRate;

            double temp = resolvedEquatorTemp - latitudeCooling - elevationCooling;
            return clamp01(temp);
        };

        outputValues.put(OUTPUT_TEMPERATURE_FIELD_ID, temperatureField);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private record LatitudeBounds(double minZ, double maxZ, double centerZ, double halfSpan) {

        private static LatitudeBounds fromRegion(@Nullable RegionData region) {
            if (region == null || !region.isComplete()) {
                return new LatitudeBounds(-4096.0d, 4096.0d, 0.0d, 4096.0d);
            }

            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min == null || max == null) {
                return new LatitudeBounds(-4096.0d, 4096.0d, 0.0d, 4096.0d);
            }

            double minZ = min.getZ();
            double maxZ = max.getZ();
            double centerZ = (minZ + maxZ) * 0.5d;
            double halfSpan = Math.max(1.0d, Math.abs(maxZ - minZ) * 0.5d);
            return new LatitudeBounds(minZ, maxZ, centerZ, halfSpan);
        }

        private double normalizedLatitude(double z) {
            return Math.max(0.0d, Math.min(1.0d, Math.abs(z - centerZ) / halfSpan));
        }
    }
}
