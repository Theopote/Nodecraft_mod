package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "world.terrain.precipitation_field",
    displayName = "Precipitation Field",
    description = "Builds precipitation from latitude bands and terrain rain-shadow response.",
    category = "world.terrain",
    order = 9
)
public class PrecipitationFieldNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_WIND_FIELD_ID = "input_wind_field";
    private static final String INPUT_EQUATOR_Z_ID = "input_equator_z";
    private static final String INPUT_RAIN_BASE_ID = "input_rain_base";

    private static final String OUTPUT_RAIN_FIELD_ID = "output_rain_field";

    @NodeProperty(displayName = "Equator Z", category = "Climate", order = 1)
    private double equatorZ = 0.0d;

    @NodeProperty(displayName = "Rain Base", category = "Climate", order = 2)
    private double rainBase = 1.0d;

    public PrecipitationFieldNode() {
        super(UUID.randomUUID(), "world.terrain.precipitation_field");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional region for latitude normalization", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Terrain elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_WIND_FIELD_ID, "Wind Field", "Optional prevailing wind vector field", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_EQUATOR_Z_ID, "Equator Z", "Z position of equator centerline", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RAIN_BASE_ID, "Rain Base", "Global rainfall multiplier", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RAIN_FIELD_ID, "Rain Field", "Precipitation intensity field in [0,1]", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        if (!(heightObj instanceof ScalarFieldData heightField)) {
            outputValues.put(OUTPUT_RAIN_FIELD_ID, null);
            return;
        }

        RegionData region = inputValues.get(INPUT_REGION_ID) instanceof RegionData r ? r : null;
        VectorFieldData windField = inputValues.get(INPUT_WIND_FIELD_ID) instanceof VectorFieldData wind ? wind : null;
        double resolvedEquatorZ = getInputDouble(INPUT_EQUATOR_Z_ID, equatorZ);
        double resolvedRainBase = Math.max(0.0d, getInputDouble(INPUT_RAIN_BASE_ID, rainBase));

        double latitudeHalfSpan = resolveLatitudeHalfSpan(region);
        ScalarFieldData rainField = point -> {
            double latitude01 = latitudeFactor(point.z, resolvedEquatorZ, latitudeHalfSpan);
            double latitudeBand = 1.0d - latitude01;

            double h = heightField.sampleScalar(point);
            double elevationDrying = clamp01(h * 0.12d);

            double orographicBoost = sampleOrographicBoost(point, heightField, windField);

            double base = (0.35d + 0.65d * latitudeBand) * resolvedRainBase;
            double rain = base * (1.0d + orographicBoost) * (1.0d - 0.55d * elevationDrying);
            return clamp01(rain);
        };

        outputValues.put(OUTPUT_RAIN_FIELD_ID, rainField);
    }

    private double resolveLatitudeHalfSpan(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return 4096.0d;
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return 4096.0d;
        }
        return Math.max(1.0d, Math.abs(max.getZ() - min.getZ()) * 0.5d);
    }

    private double latitudeFactor(double z, double equatorZ, double halfSpan) {
        return clamp01(Math.abs(z - equatorZ) / halfSpan);
    }

    private double sampleOrographicBoost(Vector3d point,
                                         ScalarFieldData heightField,
                                         @Nullable VectorFieldData windField) {
        Vector3d wind = new Vector3d(1.0d, 0.0d, 0.0d);
        if (windField != null) {
            windField.sampleVector(point, wind);
        }

        double len = Math.sqrt(wind.x * wind.x + wind.z * wind.z);
        if (len <= 1.0e-9d) {
            return 0.0d;
        }

        double ux = wind.x / len;
        double uz = wind.z / len;
        double sampleDist = 8.0d;

        Vector3d upwind = new Vector3d(point.x - ux * sampleDist, point.y, point.z - uz * sampleDist);
        Vector3d downwind = new Vector3d(point.x + ux * sampleDist, point.y, point.z + uz * sampleDist);

        double hUpwind = heightField.sampleScalar(upwind);
        double hDownwind = heightField.sampleScalar(downwind);
        double delta = hDownwind - hUpwind;

        // Positive delta means rising terrain along wind direction (windward uplift, more rain).
        return clamp(-0.35d, 0.35d, delta * 0.08d);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private double clamp(double min, double max, double value) {
        if (value < min) {
            return min;
        }
        return Math.min(max, value);
    }
}
