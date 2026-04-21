# Bake Node Deprecation Plan (vNext)

## Scope

This plan tracks the removal of type-specific bake nodes in `output.execute` and consolidates baking through `GeometryToBlocksNode` (`output.execute.bake_geometry_to_blocks`).

Deprecated node IDs:

- `output.execute.bake_box_to_blocks`
- `output.execute.bake_sphere_to_blocks`
- `output.execute.bake_cylinder_to_blocks`
- `output.execute.bake_cone_to_blocks`
- `output.execute.bake_ellipsoid_to_blocks`
- `output.execute.bake_prism_to_blocks`
- `output.execute.bake_octahedron_to_blocks`
- `output.execute.bake_tetrahedron_to_blocks`
- `output.execute.bake_torus_to_blocks`

## Why

- Prevent node-library growth for every new geometry subtype.
- Keep voxelization responsibility inside `GeometryVoxelizer`.
- Ensure new geometry support is added in one place (voxelizer dispatch), not in multiple node classes.

## Phase Plan

### Phase 1 (current minor version)

- Keep deprecated nodes executable for backward compatibility.
- Mark deprecated node classes with `@Deprecated`.
- Hide deprecated nodes from default node-library browsing (existing graphs still load and run).
- Add explicit migration note in node library docs.

Exit criteria:

- Existing saved graphs continue to execute unchanged.
- New graph creation defaults to `Bake Geometry To Blocks`.

### Phase 2 (next minor version)

- Add a graph-load migration warning when deprecated bake node IDs are present.
- Add editor quick-fix action: replace deprecated bake node with `Bake Geometry To Blocks` and reconnect compatible ports.
- Add metrics/logging counter for deprecated node usage frequency to validate readiness for removal.

Exit criteria:

- Migration quick-fix is available and stable.
- Deprecated usage is low enough to remove in the next major.

### Phase 3 (next major version)

- Remove deprecated bake node registration from the node registry.
- Keep deserialization fallback:
  - On load, map deprecated bake node IDs to `output.execute.bake_geometry_to_blocks`.
  - Preserve equivalent properties where possible (for example fill/shell choices).
  - Emit one migration record per replaced node.
- Remove deprecated classes after registry + migration coverage is complete.

Exit criteria:

- Old graphs load through migration mapping without hard failure.
- No deprecated bake node IDs remain in saved graph output after save.

## Impact Checklist

- Node registration and category listing.
- Search/index behavior in node library.
- Graph deserialization and version migration path.
- Property translation:
  - `fillBox`, `fillCone`, `fillCylinder`, `fillEllipsoid`, `fillOctahedron`, `fillPrism`, `fillTetrahedron`, `fillTorus`
  - sphere mode mapping (`voxelMode`, `shellThickness`) to generic behavior where supported.
- QA scenarios:
  - open old graphs
  - execute old graphs
  - save/reopen migrated graphs
  - mixed graphs (deprecated + generic bake nodes)

## Non-goals

- This plan does not remove `output.execute.bake_surface_strip_to_blocks` (not a `GeometryData` bake path).

