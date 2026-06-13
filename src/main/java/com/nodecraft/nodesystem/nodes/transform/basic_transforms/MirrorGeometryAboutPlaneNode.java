package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryMirror;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Reflects supported {@link GeometryData} primitives about a plane (including composites and boolean wrappers).
 */
@NodeInfo(
    id = "transform.basic_transforms.mirror_geometry_plane",
    displayName = "Mirror Geometry About Plane",
    description = "Mirrors analytic geometry about a plane (recursive for composites and boolean geometry nodes)",
    category = "transform.basic_transforms",
    order = 7
)
public class MirrorGeometryAboutPlaneNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MirrorGeometryAboutPlaneNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.mirror_geometry_plane");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Geometry to mirror (primitives, composite, intersection, difference)",
            NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Mirror plane",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry",
            "Mirrored geometry",
            NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when mirroring fails",
            NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when mirroring succeeded",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Mirror Geometry About Plane";
    }

    @Override
    public String getDescription() {
        return "Mirrors analytic geometry about a plane (recursive for composites and boolean geometry nodes)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geomObj = inputValues.get(INPUT_GEOMETRY_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(geomObj instanceof GeometryData geometry) || !(planeObj instanceof PlaneData plane)) {
            writeResult(null, false, "Missing geometry or plane input");
            return;
        }
        GeometryData mirrored = GeometryMirror.mirror(geometry, plane);
        if (mirrored == null) {
            writeResult(null, false, "Unsupported geometry type or invalid mirror plane");
            return;
        }
        writeResult(mirrored, true, "");
    }

    private void writeResult(@Nullable GeometryData geometry, boolean valid, String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}
