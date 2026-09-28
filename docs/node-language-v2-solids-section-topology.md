# Geometry Solids — Section Topology v2

**Status: PASSED / FROZEN** (Graph **V94**)

Closes the V92 gap where voxel sections emitted independent `POLYGON_PROFILE`s and lost hole topology. Inventory remains **23** `geometry.solids.*` nodes (no redesign of Extrude / Loft / Sweep; Extrude vs Extrude Region split kept).

V72 Solids v1 and V93 SDF remain historical fences.

## What changed

### Voxel Section / Contour → region topology

[`SectionContourUtils`](../src/main/java/com/nodecraft/nodesystem/nodes/geometry/solids/SectionContourUtils.java) traces boundary loops as before, then builds containment:

1. Each closed loop → profile via `ProfileConstructionUtils.tryCreateProfile` (fail-closed)
2. Project to 2D JTS shell polygons on the section plane
3. Parent = smallest-area covering polygon
4. Even nesting depth = region outer; odd depth = hole of nearest even-depth ancestor
5. Emit `PlanarRegionData` per outer + direct hole children

Legacy Profile / Boundary / Tree / Slice ports are unchanged (all loops, largest-first).

**Additive ports on both nodes:**

| Port | Type | Meaning |
|------|------|---------|
| `output_region` | `PLANAR_REGION` | Largest-area region on first successful plane |
| `output_regions` | `PLANAR_REGION_LIST` | Flattened across planes |
| `output_regions_tree` | `DATA_TREE` | Keyed `[planeIndex, regionIndex]` |

Empty regions with non-empty profiles remains Valid (legacy path). Neither regions nor profiles → fail as today.

Voxel Section: when `input_planes` is connected and invalid → Error `"Planes list invalid"` (not “at least one plane”).

### Plane resolver — connection-aware

[`AbstractSolidNode.resolvePlane`](../src/main/java/com/nodecraft/nodesystem/nodes/geometry/solids/AbstractSolidNode.java) uses `OptionalPortDrive.resolveOptionalPlane`. Connected-invalid Plane → `null` → Error (never silent XY).

### POINT_LIST — strict fail-closed

Solids consumers use `SolidNodeUtils.resolveStrictPointList`:

| Payload | Result |
|---------|--------|
| Non-collection | `null` → node fails |
| Empty collection | empty list (node-specific empty success where applicable) |
| Any non-`PointData` / non-finite | `null` → node fails (no silent drop / index drift) |

Affected: Shrinkwrap ×2, Extrude Point List, Loft Point Lists, Prism By Points, Sweep From Points, Sweep 2 Rails (profile points path).

## Out of scope

Auto Seam, Region Loft/Sweep/Revolve, Analytic Section, renaming Surface Strip To Lattice, Extrude accepting Region.

## Contract tests

`GeometrySolidsSectionTopologyV2ContractTest` (Graph V94)
