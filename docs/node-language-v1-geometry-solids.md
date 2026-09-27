# Geometry Solids — Surface / Solid Modeling Language v1

**Status: PASSED / FROZEN** (Graph **V72**)

Geometry Solids v1 freezes **22** canonical `geometry.solids.*` nodes under a four-layer stack: `POLYGON_PROFILE` → `PATH` → `SURFACE_STRIP` → `GEOMETRY`, with Valid+Error on every node, typed list ports only (no bare `LIST`), PATH (not POLYLINE) for curve topology, and fail-closed workloads.

**Out of scope:** package rename to analysis; analytic CAD section; global `GEOMETRY` retirement; rewriting loft/sweep algorithms; `setNodeState` exact-Integer hardening (P2).

## Inventory (order 0–21)

| Order | Display | Id |
|------:|---------|-----|
| 0 | Extrude | `geometry.solids.extrude` |
| 1 | Extrude Point List | `geometry.solids.extrude_from_points` |
| 2 | Prism By Points | `geometry.solids.extrude_profile_from_points` |
| 3 | Extrude Box Face | `geometry.solids.extrude_box_face` |
| 4 | Push/Pull Box Face | `geometry.solids.push_pull_face` |
| 5 | Loft Surface | `geometry.solids.loft` |
| 6 | Loft Point Lists | `geometry.solids.loft_from_points` |
| 7 | Multi-Section Loft Surface | `geometry.solids.loft_multi_section` |
| 8 | Sweep Surface | `geometry.solids.sweep` |
| 9 | Sweep Surface From Points | `geometry.solids.sweep_from_points` |
| 10 | Sweep 2 Rails | `geometry.solids.sweep_two_rails` |
| 11 | Revolve Surface | `geometry.solids.revolve` |
| 12 | Morph Between Profiles | `geometry.solids.morph_profiles` |
| 13 | Offset Surface Strip | `geometry.solids.offset_surface_strip` |
| 14 | Thicken Surface | `geometry.solids.thicken_surface` |
| 15 | Extract Surface Strip Range | `geometry.solids.extract_surface_strip_range` |
| 16 | Deconstruct Surface Strip | `geometry.solids.deconstruct_surface_strip` |
| 17 | Surface Strip To Lattice | `geometry.solids.surface_strip_to_lattice` |
| 18 | Voxel Section | `geometry.solids.section_cut` |
| 19 | Voxel Contours | `geometry.solids.contour` |
| 20 | Shrinkwrap Points On Surface Strip | `geometry.solids.shrinkwrap_points_surface_strip` |
| 21 | Shrinkwrap Points To Voxel Geometry | `geometry.solids.shrinkwrap_points_voxel_geometry` |

**Deleted (V72):** `geometry.solids.extrude_profile` → Extrude; `geometry.solids.shell` → Thicken Surface.

All nodes are `PURE`.

## Four-layer language

| Layer | Canonical type | Role |
|-------|----------------|------|
| Profile | `POLYGON_PROFILE` / `POLYGON_PROFILE_LIST` | planar closed 2D region |
| 1D | `PATH` / `PATH_LIST` | curve topology (V71) |
| Surface | `SURFACE_STRIP` / `SURFACE_STRIP_LIST` | sampled parametric surface (U = section, V = within-section) |
| Solid / volume | `GEOMETRY`, `PRISM_GEOMETRY` | renderable / voxelizable body |

**Rule:** Loft / Sweep / Revolve emit **surface**, not solid. Surface Strip To Lattice is the explicit cylinder-lattice approximation boundary (not a filled solid). Offset / Thicken emit surface strips only — no embedded lattice geometry.

## Valid + Error

Every solids node exposes `output_valid` (`BOOLEAN`) and `output_error` (`STRING`).

| Outcome | Behavior |
|---------|----------|
| Success | `Valid=true`, `Error=""` |
| Invalid | `Valid=false`, actionable `Error`, continuous outputs `null` |

Connection-aware optional ports use [`OptionalPortDrive`](../src/main/java/com/nodecraft/nodesystem/util/OptionalPortDrive.java) via [`SurfaceInputUtils`](../src/main/java/com/nodecraft/nodesystem/util/SurfaceInputUtils.java).

## SurfaceStrip invariant

[`SurfaceStripData`](../src/main/java/com/nodecraft/nodesystem/datatypes/SurfaceStripData.java) + [`SurfaceStripValidator`](../src/main/java/com/nodecraft/nodesystem/util/SurfaceStripValidator.java):

- sections ≥ 2; each section ≥ 2 points; all points finite
- uniform `pointsPerSection`; closed flags length matches sections
- workload `sections × pointsPerSection ≤ MAX_SURFACE_TOTAL_POINTS`

## Match Sections (Loft)

`STRICT` (default) / `RESAMPLE_MAX` / `RESAMPLE_COUNT` — no silent auto-repair.

## Sweep field semantics

Scale / Rotation as `DOUBLE_LIST` along U: 0 → defaults; 1 → broadcast; N → resample to section count. Scale `> 0`; rotation degrees.

## Voxel approximation

**Voxel Section** and **Voxel Contours** use `GeometryVoxelizer.voxelizeStrict()`. They are voxel-grid approximations, not analytic CAD sections. Shrinkwrap To Voxel Geometry does not truncate queries/voxels — over budget → invalid.

## Budgets ([`GenerationLimits`](../src/main/java/com/nodecraft/nodesystem/util/GenerationLimits.java))

| Cap | Role |
|-----|------|
| `MAX_SURFACE_SECTIONS` | section count |
| `MAX_SURFACE_POINTS_PER_SECTION` | points per section |
| `MAX_SURFACE_TOTAL_POINTS` | long product |
| `MAX_SURFACE_PROJECTION_QUERIES` / `MAX_SURFACE_PROJECTION_WORK` | shrinkwrap |

## Migration (V71 → V72)

[`migrateV71ToV72`](../src/main/java/com/nodecraft/nodesystem/graph/GraphMigrationRegistry.java):

- Retire `extrude_profile` → `extrude`; `shell` → `thicken_surface`
- Drop lattice geometry wires from Offset/Thicken/Shell
- Drop legacy geometry mirror inputs on Section Cut / Contour
- Strip Shrinkwrap `maxVoxels`/`maxQueries` and Extract INDEX range state

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GeometrySolidsLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.SolidsFamilyContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
```
