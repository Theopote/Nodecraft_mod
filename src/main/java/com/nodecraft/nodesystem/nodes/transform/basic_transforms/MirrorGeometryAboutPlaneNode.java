package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryMirror;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.basic_transforms.mirror_geometry_plane",
    displayName = "Mirror Geometry About Plane",
    description = "Mirrors analytic geometry about a plane (recursive for composites and boolean geometry nodes)",
    category = "transform.basic_transforms",
    order = 4
)
public class MirrorGeometryAboutPlaneNode extends AbstractBasicTransformNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";

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
        addValidAndErrorOutputs();
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
        if (!(geomObj instanceof GeometryData geometry) || !(planeObj instanceof PlaneData planeRaw)) {
            writeResult(null, "Missing geometry or plane input");
            return;
        }
        PlaneData plane = planeRaw.normalized();
        if (plane == null) {
            writeResult(null, "Invalid mirror plane");
            return;
        }
        GeometryData mirrored = GeometryMirror.mirror(geometry, plane);
        if (mirrored == null) {
            writeResult(null, "Unsupported geometry type or invalid mirror plane");
            return;
        }
        writeResult(mirrored, "");
    }

    private void writeResult(@Nullable GeometryData geometry, String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        if (error == null || error.isEmpty()) {
            markSuccess();
        } else {
            markInvalid(error);
        }
    }
}
