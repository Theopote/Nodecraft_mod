# Simple Gothic Church Node Audit

Date: 2026-04-14

## Verdict

The current node library is sufficient to land a "simple gothic church" if the graph is built on the v1 geometry pipeline:

- `geometry.primitives` / `geometry.solids` for shape construction
- `geometry.boolean` for shelling and openings ([workflow guide](geometry-boolean-workflows.zh-CN.md))
- `material.*` for block assignment
- `output.execute.apply_changes` for final write

It is not sufficient to land the graph exactly as written in the prompt because several node names there are either:

- old documentation names
- coordinate/block generators that are not present in source
- valid ideas, but mismatched with the current geometry-first pipeline

## What Exists Now

Directly available in source:

- `SelectedBlockNode`
- `IntegerInputNode`
- `OffsetCoordinateNode`
- `OffsetCoordinatesNode`
- `DifferenceNode`
- `GeometryUnionNode` (library label: **Combine Geometry**)
- `MirrorCoordinatesNode`
- `LinearArrayNode`
- `PolarArrayNode`
- `WindowArrayNode`
- `BlockTypeSelectorNode`
- `AssignBlockTypeNode`
- `HeightGradientMapNode`
- `GeometryViewerNode`
- `ValueMonitorNode`
- `ApplyChangesNode`
- `MergeBlockPlacementsNode`
- `CombineListsNode`
- `CommentNode`

Geometry primitives already available and usable for this build:

- `BoxCenterSizeNode`
- `BoxCornerSizeNode`
- `RectangleOnPlaneNode`
- `RegularPolygonOnPlaneNode`
- `PolygonByPointsNode`
- `PrismByProfileVectorNode`
- `PrismByBasePointsVectorNode`

Newly added in this pass:

- `SquarePyramidNode`

Files:

- [SquarePyramidNode](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/geometry/primitives/SquarePyramidNode.java)
- [SquarePyramidGeometryData](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/datatypes/SquarePyramidGeometryData.java)
- [GeometryVoxelizer](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/util/GeometryVoxelizer.java)

## What Does Not Match The Prompt

These names do not correspond to current source nodes:

- `BoxNode`
- `DifferenceCoordsNode` as the main shell workflow
- `TriangularPrismBlocksNode`
- `TriangularPyramidBlocksNode`
- `CircleSphereBlocksNode`
- `LineBlocksNode`

Current replacement strategy:

- `BoxNode` -> `BoxCornerSizeNode` or `BoxCenterSizeNode`
- shell / openings -> `DifferenceNode` on geometry, not coordinate subtraction
- roof prism -> `RectangleOnPlaneNode` or `PolygonByPointsNode` + `PrismByProfileVectorNode`
- rose window ring -> `RegularPolygonOnPlaneNode` + `PrismByProfileVectorNode` + `DifferenceNode`
- tower spire -> `SquarePyramidNode`

## Practical Pass/Fail By Area

### Area 0: Global Parameters and Anchor

Pass.

You can drive the whole graph from:

- `SelectedBlockNode`
- `IntegerInputNode`
- math nodes such as `AdditionNode`, `SubtractionNode`, `MultiplicationNode`
- `OffsetCoordinateNode`

### Area 1: Nave + Transept

Pass.

Recommended build:

- outer shell with `BoxCornerSizeNode`
- inner void with `BoxCornerSizeNode`
- `DifferenceNode` for hollowing
- `GeometryUnionNode` for nave + transept merge

### Area 2: Aisles

Pass.

Same pattern as the nave. Use another pair of boxes and union them into the main body.

### Area 3: Roofs

Pass, but not with the prompt's node names.

Recommended build:

- create a triangular profile with `PolygonByPointsNode`
- extrude with `PrismByProfileVectorNode`
- mirror or duplicate for cross roofs as needed

### Area 4: Towers + Spires

Pass.

- tower bodies: box shell workflow
- spires: `SquarePyramidNode`

This was the one genuine primitive gap for the target style. It is now filled.

### Area 5: Pointed Arch Windows

Pass, but requires graph rewrite.

Recommended build:

- define a 2D pointed-arch polygon with `PolygonByPointsNode`
- extrude shallow depth with `PrismByProfileVectorNode`
- array with `LinearArrayNode` only after voxelization, or duplicate geometry upstream
- subtract openings with `DifferenceNode`
- fill glazing with `AssignBlockTypeNode`

Important:

- `WindowArrayNode` only creates rectangular inset opening boxes
- it is useful for Romanesque or generic facades
- it is not enough for pointed Gothic windows by itself

### Area 6: Portal + Rose Window

Partial pass.

Portal:

- same strategy as pointed windows, just larger

Rose window:

- possible with current nodes, but should be simplified
- use a regular polygon approximation instead of a true circle
- avoid radial tracery in v1 unless needed for the test

Recommended simple version:

- one circular-ish hole
- one colored glass fill
- optional outer stone ring

### Area 7: Materials

Pass.

- `HeightGradientMapNode` handles the stone aging bands
- `AssignBlockTypeNode` handles uniform materials
- `MergeBlockPlacementsNode` should be used instead of generic list merging for final placement data

### Area 8: Preview + Write

Pass.

- `GeometryViewerNode` for preview
- `ValueMonitorNode` for block count
- `ApplyChangesNode` for final execution

The final execution path is already aligned with geometry and block placements.

## Main Graph Risk

The main risk is not missing execution nodes. The main risk is mixing two incompatible graph styles:

- old block-coordinate generator graphs
- new geometry-first v1 graphs

If the church graph is built mostly with geometry nodes and only converted to placements at the material/output stage, it should land cleanly.

If it is built around the old `*BlocksNode` names from the prompt, it will stall on node lookup before the build even starts.

## Recommended Minimal Landing Scope

For the first working version, keep:

- latin-cross body
- side aisles
- two west towers
- square spires
- pointed portal
- pointed side windows
- one simplified rose window
- steep nave roof

Skip for now:

- flying buttresses
- bundled columns as true interior systems
- detailed tracery spokes
- advanced facade layering

## Final Assessment

Current state after this pass:

- node library is good enough for the target
- one missing primitive has been added
- the data path to preview and final write is already complete
- the prompt's graph text should be treated as a design spec, not as a literal node list

So the answer is:

- the build can land
- but the graph must be rewritten against the current geometry pipeline
- and several old block-generator node names should not be used
