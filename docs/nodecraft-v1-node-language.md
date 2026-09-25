# NodeCraft V1 Node Language Rules

Design freeze: player-facing and AI-facing **graph language** for ports, numeric types,
angles, and spatial concepts.

Last updated: 2026-09-21

## Authority

| Concern | Source of truth |
|---------|-----------------|
| Node id / category inventory | Code `@NodeInfo` + generated [`NODE_LIBRARY.md`](./NODE_LIBRARY.md) — see [`architecture/docs-authority.md`](./architecture/docs-authority.md) |
| Port connectability / conversion policy | [`type-conversion-guidelines.md`](./type-conversion-guidelines.md) + `TypeConversionRegistry` |
| Scalar Math Valid / finite / zero / Round | [`node-language-v1-scalar-math.md`](./node-language-v1-scalar-math.md) + `ScalarMathOps` |
| Trigonometry degrees / domains / Expression | [`node-language-v1-trigonometry.md`](./node-language-v1-trigonometry.md) + `TrigMathOps` |
| Compare exact / strict equality | [`node-language-v1-compare.md`](./node-language-v1-compare.md) + `CompareUtils` |
| Logic boolean / If / Switch (no coercion) | [`node-language-v1-logic.md`](./node-language-v1-logic.md) + `LogicUtils` |
| Directed Domain / Remap | [`node-language-v1-numeric-domain.md`](./node-language-v1-numeric-domain.md) |
| **This document** | How new (and remediated) nodes express values: types, port ids, units, overrides |

If a node’s current implementation disagrees with this freeze, **treat the gap as debt to
remediate** (with graph/preset migration). Do not copy the gap into new nodes.

Related audits that motivated this freeze: first-pass review of Float Input, Float Slider,
Integer Slider, Angle Slider, Coordinate Input, Vector Input, World Plane, Selected Block.

---

## Five frozen rules

### 1. Continuous parameters use `DOUBLE`

Manual entry and sliders that express the same continuous number must share one data type.

| Role | Type |
|------|------|
| Continuous scalar (radius, distance, scale, generic float param) | `DOUBLE` |
| Discrete count / index / block integer | `INTEGER` |
| `FLOAT` | Compatibility / legacy only — do not introduce on new ordinary ports |

**Do not** ship pairs like:

- Float Input → `FLOAT`
- Float Slider → `DOUBLE`

UI style must not change data semantics.

### 2. Port IDs follow a strict convention

Display names may be short (`Value`, `X`). **Internal port ids must be stable and uniform.**

Canonical patterns:

| Kind | Port id |
|------|---------|
| Scalar value out | `output_value` |
| Scalar value in | `input_value` |
| Component in | `input_x`, `input_y`, `input_z` |
| Component out | `output_x`, `output_y`, `output_z` |
| Geometry | `input_geometry`, `output_geometry` |
| Point | `input_point`, `output_point` |
| Vector | `input_vector`, `output_vector` |
| Block position | `input_block_pos`, `output_block_pos` (or domain-specific `output_position` when the semantic is clearly block grid — prefer `block_pos` for new nodes) |

**Forbidden for new scalar outputs:** bare `value` when peers use `output_value`.

Renames require graph/preset **migration** (alias old id → new id). Never rely on
ad-hoc remap only in one tool (AI schema, quick connect, etc.).

### 3. Angles are degrees in the graph language

All geometry / transform rotation-style ports that take an angle treat the number as
**degrees** (`90` means 90°).

| Allowed | Not allowed |
|---------|-------------|
| Angle sources output degrees as `DOUBLE` | Hiding unit choice in a property while still typing the port as bare `DOUBLE` |
| Explicit nodes: Degrees→Radians / Radians→Degrees when math APIs need radians | Downstream guessing from a hidden `DEGREES` / `RADIANS` property |

Do **not** introduce `NodeDataType.ANGLE` in V1 unless a later design revisit proves degrees-as-DOUBLE insufficient.

Minecraft building language prefers 45 / 90 / 180 / 360.

### 4. Player spatial language is three layers

Players and AI should primarily reason about:

| Concept | Meaning | Typical use | Internal representation (target) |
|---------|---------|-------------|----------------------------------|
| **Point** | Continuous location `(10.5, 64.0, 20.25)` | curves, profiles, geometry, transform | `POINT` → `PointData` |
| **Vector** | Direction or displacement `(1, 0, 0)` | move, extrude, normal, axis | `VECTOR` → `Vector3d` |
| **Block Position** | Integer grid `(10, 64, 20)` | get/set block, selection, snap | `BLOCK_POS` → `BlockPos` |

Conversions must stay **explicit** where policy matters (corner vs center, snap):

```
Block Position  →  Block To Point  →  Point
Point           →  Snap To Block   →  Block Position
```

See [`type-conversion-guidelines.md`](./type-conversion-guidelines.md) for
`IMPLICIT_SAFE` vs `EXPLICIT_REQUIRED`.

**Removed (pre-release language cleanup — no old-graph preserve):**

| Type | Replacement |
|------|-------------|
| `COORDINATE` | `BLOCK_POS` |
| `POSITION` | `POINT` (location) or `VECTOR` (direction / displacement) by role |
| `COORDINATE_LIST` | `BLOCK_LIST` |

Do not grow new player docs or AI examples around Coordinate / Position as first-class
type ids.

Nodes named like `point_from_coordinates` that only emit integer `BlockPos` should be
repositioned as **Block Position Input** (id/display), with a separate true **Point**
input node for continuous xyz when needed.

### 5. Property fallback + port override is one pattern

Parameter nodes resolve values as:

```
connected input port  (if present and valid)
        ↓ else
node property / UI field
```

When a port drives a field:

- UI must show the field as **driven by connection** (disabled or clearly non-editable).
- Do not silently ignore a live connection without visible source state.

**Reference implementation:** Vector Input (`input_x/y/z` + property fallback).

**Exceptions** (e.g. Selected Block: interactive pick vs coordinate inputs) must expose an
explicit **Source Mode** (and show the active source in the node UI). Do not leave wires
connected while a hidden priority makes them inert.

---

## Supporting norms

### Avoid `ANY` for spatial origins / points

Prefer a concrete type (`POINT`) plus registry conversions, not `ANY` + runtime
`resolvePoint()` catch-all. Runtime tolerance is not a substitute for graph language.

### Dead properties

`@NodeProperty` must only expose settings that change observable behavior or UI.
Properties wired to dead code (`if (false)`, unused flags) confuse players and pollute
AI schema — remove or re-enable, do not leave half-alive.

### Minecraft-first defaults that stay

Examples that remain correct under this freeze:

- World Plane default `XZ` (horizontal ground) — keep.
- Explicit Block↔Point conversion nodes — keep and prefer over silent policy.

---

## Known gaps (first-pass sample)

These violated the freeze at audit time. Batch A items below are remediated in code.

| Gap | Rule | Status |
|-----|------|--------|
| Float Input outputs `FLOAT`; Float Slider outputs `DOUBLE` | §1 | Fixed (both `DOUBLE`) |
| Integer Slider port id `value` vs peers’ `output_value` | §2 | Fixed + V1→V2 migration |
| Angle Slider unit property converts to radians while port stays `DOUBLE` | §3 | Fixed (degrees only; legacy `unit` ignored — pre-release, no old-graph preserve) |
| Coordinate Input: int xyz, dual Coordinate/Block Pos, no `input_x/y/z` | §4, §5 | Fixed → Block Position Input + overrides |
| World Plane Origin typed as `ANY` | Supporting norm | Fixed → `POINT`; Block→Point and Vector→Point explicit |
| Selected Block: pick silently overrides connected X/Y/Z | §5 | Fixed — Source Mode + Active Source |
| Selected Block Position / Center types | §4 | Fixed — `BLOCK_POS` / `POINT` |
| Float / Integer slider dead UI properties | Dead properties | Fixed |

---

## Remediation batches (implementation order)

Document first; code follows this order unless a dependency forces otherwise.

### Batch A — low risk, high leverage — **done (2026-09-21)**

1. Float Input → `DOUBLE` (fields + port).
2. Integer Slider `value` → `output_value` + graph format **V1→V2** migration.
3. Remove dead slider UI properties (`showMinMaxLabels` / `showSettingsPanel` / `showRangeInfo` / unused Float Input range+label flags).
4. Angle Slider: degrees-only graph output; unit switch removed. Legacy `unit` state is
   ignored (pre-release: no radians behavior-preserving migration). Use Degrees To Radians
   when new graphs need radians.

### Batch B — language alignment — **done (2026-09-21)**

5. Coordinate Input → **Block Position Input** (`reference.points.block_position`); `input_x/y/z` INTEGER overrides like Vector Input; graph format **V2→V3** type rename.
6. World Plane Origin: `ANY` → `POINT`. Continuous locations use identical `POINT`.
   `BLOCK_POS → POINT` and `VECTOR → POINT` require explicit conversion so Block Position /
   Look Direction cannot silently become Origin.

### Batch C — composition semantics — **done (2026-09-21)**

7. Selected Block Source Mode (`Auto` / `Picked` / `Coordinates`) + visible **Active Source**.
   - Auto: complete X/Y/Z connections win over pick; otherwise pick; otherwise coordinate values.
   - Pick storage and coordinate-input storage are separate — switching mode does not silently discard the other.
   - UI shows mode combo, active source label, and warnings when the unused source still exists.

### Batch D — spatial / angle freeze closure — **done (2026-09-21)**

8. `TypeConversionRegistry`: `BLOCK_POS → POINT` and `VECTOR → POINT` are `EXPLICIT_REQUIRED`.
9. Angle Slider: degrees-only; ignore legacy `unit` (no old-file radians migration in pre-release).
10. Selected Block: Position → `BLOCK_POS`, Center → `POINT`.
11. Removed `COORDINATE` / `POSITION` / `COORDINATE_LIST` from `NodeDataType` (pre-release: no
    alias preserve). Continuous locations use `POINT`; block bags use `BLOCK_LIST`.

**Pre-release compatibility policy:** prefer clean language breaks and update in-repo presets /
tests. Do not add graph migrations solely to preserve abandoned on-disk semantic variants.

### Deferred (phase 2)

- Collapse Selected Block advanced outputs behind Block Info / Deconstruct patterns.
- Optional editor Convert → Block To Point insert assist.

---

## Geometry language (Batch 2 sample freeze)

Applies to `geometry.primitives.*` and `geometry.profiles.*` (sample set first, then roll out).

### Spatial port roles (no `ANY`)

| Role | Type |
|------|------|
| Center / Start / End / Apex / Corner | `POINT` |
| Axis / Direction / Normal / X Axis | `VECTOR` |
| Block grid | `BLOCK_POS` |
| Ordered locations (corners, samples) | `POINT_LIST` |
| Ordered paths (beam centerlines, multi-edge eaves) | `PATH_LIST` |
| Ordered frames (placement layouts) | `FRAME_LIST` |
| Ordered polygon profiles (loft sections) | `POLYGON_PROFILE_LIST` |

Do **not** type locations as `VECTOR` / `VECTOR_LIST`.
Direction resolvers (`SpatialValueResolver.resolveVector`, `SolidNodeUtils.resolveDirection`)
accept vector-like values only — not `PointData` or `BlockPos`.

### Defaults (Minecraft-first)

- Unspecified construction plane → **`XZ`** (horizontal ground), same as World Plane.
- Profile: connected `Center` wins; else use **Plane origin**; else world origin on XZ.
- Continuous size params use property fallback + port override (e.g. Sphere radius `5`).

### Geometry ≠ Blocks

```
POINT / params  →  GEOMETRY / PROFILE
GEOMETRY        →  Voxelize  →  Block placements  →  Preview / Bake
```

`geometry.*` nodes must not treat voxelization as the canonical result. Convenience
`Blocks` / `Region` / `Count` on Box may remain as **legacy** outputs while presets migrate.

### Canonical chain

```
BLOCK_POS → Block To Point → POINT → PROFILE / PRIMITIVE → GEOMETRY
  → TRANSFORM → PATTERN → VOXELIZE → BLOCK PLACEMENTS → PREVIEW / BAKE
```

### Batch E — Geometry sample remediation (8 nodes) — **done (2026-09-21)**

P1/P2 applied to Sphere, Cylinder, Cone, Torus, Box (center+size), Rectangle/Circle/Polygon profiles:
typed spatial ports, `POINT_LIST`, XZ defaults, plane-origin center fallback, property defaults,
continuous Box geometry (Blocks/Region legacy).

**Profiles family rollout (`geometry.profiles.*`) — done (2026-09-21):** Batch-2 language rules
applied to all remaining profile generators and utilities under `geometry.profiles` (OnPlane shapes,
Polygon By Points, deconstruct/resample, convex hull, Voronoi). Center inputs are `POINT`; location
outputs are `POINT` / `POINT_LIST`; default plane is XZ via `ProfilePlaneUtils`; direction/axis
ports remain `VECTOR`.

**Primitives family rollout (`geometry.primitives.*`) — done (2026-09-21):** remaining constructors /
deconstructors use `POINT` / `POINT_LIST` for locations; axes stay `VECTOR`. Continuous Box helpers
cover corner/size siblings. Polyhedron `input_orientation` is **`MATRIX3`** (not `ANY`).

**Point-list runtime bridge (accepted / frozen):** `SpatialValueResolver.resolvePointList(...)`
accepts `PointData`, `Vector3d`, and legacy point-like entries for algorithms;
`toPointDataList(...)` emits graph-facing `POINT_LIST`. Prefer this at every list-input boundary.
Do not add new ad-hoc list coercion at node boundaries.

### Profile generator minimum output protocol

Every **OnPlane profile generator** (and any profile node that emits `output_profile` /
`output_outer_profile`) must expose at least:

| Port id | Display | Type |
|---------|---------|------|
| `output_profile` | Profile | `POLYGON_PROFILE` |
| `output_boundary` | Boundary | `POLYLINE` (generators) |
| `output_points` | Points | `POINT_LIST` (generators) |
| `output_plane` | Plane | `PLANE` — resolved construction plane |
| `output_center` | Center | `POINT` — resolved profile center |
| `output_valid` | Valid | `BOOLEAN` |

Port order for generators: Points → Profile → Boundary → **Plane → Center** → param echoes → Valid.
On invalid input, emit `null` for Profile / Boundary / Plane / Center; empty list for Points;
`false` for Valid. Reuse the same `PlaneData` instance passed to `PolygonProfileData` for
`output_plane` when possible.

Multi-boundary generators (e.g. Annulus) keep domain-specific profile/boundary ports but still
emit shared `output_plane` / `output_center`. Profile Boolean / Offset emit Plane / Center from
the primary result profile.

**Orientation (polyhedra):** `input_orientation` → **`MATRIX3`**. Unconnected falls back to Euler
degree properties via `PolyhedronOrientationUtil.resolveFromPortOrEuler`.

**P3 — runtime fallback cleanup:** gradually remove obsolete **LINE → POINT** runtime fallbacks
once consumers use explicit `POINT` / `POINT_LIST` and the point-list bridge. New nodes must not
depend on LINE-as-point coercion.

**Contract tests:** `GeometrySampleLanguageContractTest` (sample 8) and
`GeometryPrimitiveAndProfileFamilyContractTest` (no spatial `ANY`; polyhedron orientation
`MATRIX3`; location ports not `VECTOR` / `VECTOR_LIST`; profile Plane / Center outputs).

**Product note — Primary vs construction variants:** beginner primitives (Sphere, Box, Cylinder, …)
should be usable with property defaults; exact construction variants (Sphere By Diameter,
Box By Two Corners, …) may stay invalid until required ports are connected.

**Next:** Curves language batch (degrees / samples / frames), then remaining intentional
`VECTOR_LIST` axes/normals only.

**Extrude / Project / loft / morph — done (2026-09-21):** `ExtrudePointList`,
`ExtrudeProfile` (base/top points), `Prism By Profile/Base Points Vector`, `Loft Point Lists`,
`Loft Profiles`, `Morph Between Profiles`, `ProjectPointsToPlane`, and `ProjectProfileToPlane`
use `POINT_LIST` + `resolvePointList` / `toPointDataList`. Project’s duplicate `Point Data`
port was removed (pre-release; no old-graph compat).

**Deformations point-list family — done (2026-09-21):** Twist/Bend/Taper/Relax/Noise/
CurveAttract/Lattice/SphericalDisplace: points `POINT_LIST`, axis origin/center `POINT`,
no spatial `ANY`; offsets that are displacements stay `VECTOR_LIST`.

**Solids sweep/slice/shrinkwrap — done (2026-09-21):** Sweep Point List Along Path, Sweep Profile
Along Path, Sweep 2 Rails, Deconstruct Surface Strip, Revolve Profile, Section Cut, Contour, and
both Shrinkwrap nodes: location lists use `POINT_LIST` + `resolvePointList` / `toPointDataList`.
Scale/rotation lists stay `LIST`; rail segments and profile lists unchanged.

**Curves location-list family — done (2026-09-21):** Blend Curves, Box Face Boundary Path, Curve
Rebuild By Length, Resample Polyline By Length, Voxelize Curve, Face Edge To Path, Offset Curve In
Plane, and Curve Frame Along Path: location outputs use `POINT_LIST` + `toPointDataList`; frame
X/Y/Z axes and path-direction aliases stay `VECTOR_LIST`.

**Curves generators & splines — done (2026-09-21):** Points To Path, Path To Points, Interpolate Spline,
B-Spline, Bezier, NURBS Curve, Arc, Helix, Parabola On Plane, and Infinity Curve On Plane: location
inputs/outputs use `POINT` / `POINT_LIST` + `resolvePointList` / `toPointDataList`; spline weights
and tween/offset nested list ports stay `LIST`.

**Pattern scatter / grid — done (2026-09-21):** Scatter*, Sample*, Poisson Disk On Plane, Image Based
Scatter, Facade Grid, Curve Array Origins, Path Instances Origins, L-System Points, Voronoi3D Lloyd
sites: location lists `POINT_LIST`; normals/axes/offsets stay `VECTOR_LIST`.

**Transform / world / arch leftovers — done (2026-09-21):** Project Curve To Plane, Offset/Inset Box Face,
Transform Points by Frames, Align Points To Surface Normals, Shear Point List, Mirror Vector List About Plane,
Selected Block Sequence centers, Filter Points By Rule, Molding Profile, Deconstruct Box Face; Bend/Twist Geometry
bounds min/max emit `PointData`. Axes/normals/offsets stay `VECTOR_LIST`.

**Batch F — Profile generator minimum outputs — done (2026-09-21):** all OnPlane profile generators
emit `output_plane` + `output_center` alongside Profile / Boundary / Points / Valid; family contract
test guards `output_profile` nodes for Plane + Center ports.

**Batch 3 Curves — PATH language (2026-09-21):**

- **`PATH`** is the unified graph input for line / polyline / curve consumers. Only
  `LINE`, `POLYLINE`, and `CURVE` connect implicitly to `PATH` (not Point / Vector / Geometry).
- Path consumers use a single **`input_path`** (or `input_path_a` / `input_path_b` for two-path nodes).
  Runtime resolution precedence when unpacking legacy triple values: **CURVE > POLYLINE > LINE**.
- V1 **`CURVE`** means a **sampleable path** (Bezier control structure or LINEAR sampled polyline),
  not a CAD analytic kernel.
- **`Curve.getLinearSamples()`** must not duplicate segment junction vertices (feeds Evaluate / Rebuild /
  Frame / Offset / Tween downstream).
- **Arc** center fallback uses numeric **`centerX` / `centerY` / `centerZ`**; plane fallback uses
  **`DefaultPlane` enum** (`XZ` default), not CSV strings.
- **`SpatialValueResolver.resolvePoint()`** / **`resolveVector()`** are the semantic entry points;
  utilities must not use point resolvers for vector ports.
- **Curve Evaluate** is the contract sample: `PATH` in → `POINT` + `VECTOR` frame out.

**Contract tests:** `GeometryCurvesFamilyContractTest` (PATH ports, implicit connect, linear samples,
Arc defaults, Curve Evaluate contract).

**Batch 3 P2 — drag-out defaults (2026-09-21):**

- **Curve Rebuild By Length** and **Curve Frame Along Path**: `defaultSpacing = 1.0` block when Spacing /
  Count ports are unconnected (Count still wins when connected).
- **Helix**: numeric property fallbacks — center `0,0,0`, axis `0,1,0`, radius `4`, pitch `2`, turns `3`,
  segments/turn `24`, start angle `0°`.

**Batch 3 P2 — PATH producers & downstream (2026-09-21):**

- **Points To Path** emits primary **`output_path`** (`PATH`) plus legacy `Line` / `Polyline`.
- **Offset Path In Plane** (`geometry.curves.offset_curve_plane`) is the canonical in-plane offset;
  shared kernel `InPlanePathOffset`. **Offset Polyline In Plane** kept as legacy (order 99), same algorithm
  without optional resampling — prefer Offset Path In Plane for new graphs.
- **Along Path**, **Path Instances** use single **`PATH`** inputs with optional `POINT_LIST`
  fallback via `PathUtils.resolvePathOrPointList`.
- **Sweep** / **Sweep 2 Rails** spines/rails are **PATH-only** (Batch 4 P2 dropped path-point fallbacks).

**Batch 3 P3 — pattern / reference / preview PATH (2026-09-21):**

- **Curve Array Geometry**, **Array Along Curve** use single **`input_path`** (`POINT_LIST` fallback on
  Array Along Curve via `input_path_points`).
- **Project Curve To Plane**, **Project Point To Polyline**, **Closest Point To Object** use **`input_path`**.
- **Preview Curves** uses **`input_path`** plus list fallback on `input_points`.
- **Blend Curves** start/end outputs are **`POINT`** (`output_start_point` / `output_end_point`).

**Batch 3 P4 — Fillet / attractor PATH (2026-09-21):**

- **Fillet Path Corners** (`geometry.curves.fillet_polyline_corners`) uses **`input_path`**; closed paths rejected.
- **Path Attractor Field** (`math.fields.curve_attractor_field`) and **Path Attract Point List**
  (`transform.deformations.curve_attract`) use **`input_path`** via `PathUtils.resolvePath`.

**Batch 3 P5 — historical IDs + legacy port sweep (2026-09-21):**

- Canonical ids: **`geometry.curves.points_to_path`**, **`geometry.curves.path_to_points`**
  (was `curve_from_points` / `divide_curve_to_points`). Graph format **V4** migrates old ids and
  remaps legacy path ports (`input_curve` / `input_polyline` / path `input_line`) → `input_path`
  **only for allowlisted PATH consumers** (not a global `input_line` rewrite).
- In-repo presets updated to canonical ids + `input_path`. Architectural Railing / Staircase
  kept `input_line` until Batch 13 (**V10** remap → `input_path`).

**Batch 4 P1a — Solids language freeze (2026-09-21):**

- **Extrude** (`geometry.solids.extrude`) is the canonical player Extrude: Profile + Direction →
  Geometry / Prism / Side Surface.
- **Prism By Profile Vector** (`geometry.solids.extrude_profile`) kept as legacy/advanced (order 99).
  Graph format **V5** remaps saved `extrude_profile` → `extrude` (+ `input_extrusion_vector` →
  `input_direction`, `output_surface_strip` → `output_side_surface`).
- **Sweep / Loft** emit **SURFACE_STRIP** (surface topology), not solid Geometry. Display names /
  descriptions state Surface explicitly.
- **Surface Strip To Lattice** (`geometry.solids.surface_strip_to_lattice`) replaces misleading
  `surface_strip_to_geometry`; output labeled Lattice Geometry (cylinder edges + rails, not a fill).
- Contract: `SolidsFamilyContractTest`.

**Next (Batch 4 P1b):** PathFrameUtils parallel transport + Sweep profile-plane local (u,v).

**Batch 4 P1b — Path frames + profile plane (2026-09-21):**

- Shared **`PathFrameUtils`**: parallel-transport frames along polylines/samples (`z` = tangent,
  `x`/`y` = section). Used by Sweep Surface, Sweep From Points, and Curve Frame Along Path.
- Sweep places profiles via **profile plane UV** (`profileLocalOffsets` / `pointsToLocalOffsets`),
  not world `point - center`.
- Contract/unit: `PathFrameUtilsTest` (+ existing Solids/Curves family contracts).

**Batch 4 P2 — Sweep PATH-only / Loft resample / PROFILE_LIST / strict direction (2026-09-22):**

- **Sweep Surface / Sweep From Points / Sweep 2 Rails**: PATH-only spines/rails — dropped
  `input_path_points` / `input_rail_*_points`. Use Points To Path when you have a point list.
- **Loft Surface** and **Multi-Section Loft**: Auto Resample defaults **on** (target 0 = max vertex count).
- New typed list **`POLYGON_PROFILE_LIST`** (`ListElementKind.POLYGON_PROFILE`); Multi-Section Loft
  Profiles in/out and Sweep Section Profiles use it.
- **`SolidNodeUtils.resolveDirection`** and **`SpatialValueResolver.resolveVector`** are strict:
  `Vector3d` / `Vec3d` / `Vector3` only — not `PointData` or `BlockPos`.

**Next (Batch 4 P3):** fill/solidization path for Sweep/Loft when ready; otherwise leave as surface.

**Batch 5 P1 — Geometry ops: Combine / Voxel Boolean / SDF (2026-09-22):**

Three distinct mechanisms (do not treat as one “Boolean”):

| Layer | Nodes | Behavior |
|-------|-------|----------|
| **Combine** | `geometry.combine.geometry` (Combine Geometry) | Structural `CompositeGeometryData`; bake = block set-union. **Not** analytic BRep union. Legacy id `geometry.boolean.union` → V6. |
| **Voxel Boolean** | Difference / Intersection | Deferred `DifferenceGeometryData` / `IntersectionGeometryData`; evaluated on the Minecraft block grid at voxelize/bake. |
| **SDF Boolean** | SDF Boolean (+ SDF primitives) | Continuous signed-distance ops (incl. Smooth K). `GEOMETRY` ⇄ `SDF` stays unsupported without explicit SDF To Geometry. |

- **Preview Geometry**: Difference / Intersection are **not** expanded to operands for surface preview. They voxelize and show ghost blocks so Preview ≈ Bake.
- Contract: `BooleanFamilyContractTest`.

**Batch 5 P2 — Category split + Difference bounds + SDF resolvers (2026-09-22):**

- **Difference bounds** = minuend (base) only — no longer union with cutter (tighter voxel scan).
- Library categories: `geometry.combine` (Combine), `geometry.boolean` (Difference / Intersection),
  `geometry.sdf` (SDF primitives + ops + To Geometry), `geometry.analysis` (Bounding Box / Geometry Bounds).
  Node type ids unchanged for Bounds/SDF (category move only).
- SDF nodes use `SpatialValueResolver.resolvePoint` / `resolveVector` (strict roles).

**Next (Batch 5 P3):** Auto Seam Alignment; PATH_LIST; voxelization cache; Combine single-input pass-through.

**Batch 12 — Field System language (2026-09-22):**

- **Locations** (Center / Origin / sample Point): `POINT` / `POINT_LIST` — not `VECTOR` / bare `LIST`.
- **Directions** (Axis): `VECTOR` via `SpatialValueResolver.resolveVector`.
- **Field types**: `SCALAR_FIELD` / `VECTOR_FIELD` stay first-class; no `ANY` in `math.fields.*`.
- **SDF bridges** are explicit: **Scalar Field From SDF**, **Vector Field From SDF Gradient**.
- Contract: `FieldsFamilyContractTest`.

**Batch 13 — Architectural Components language (2026-09-22):**

- Core five: **Wall With Openings**, **Floor Slab**, **Roof Base**, **Staircase**,
  **Window Array** — footprint face ports stay `BOX_FACE`; continuous sizes stay `DOUBLE`.
- **Railing** / **Staircase** join PATH language: `input_line` → `input_path` (graph format **V10**).
- Spiral stair start angle remains **degrees** as `DOUBLE`.
- No `ANY` / no leftover `LINE` ports under `geometry.architectural_primitives.*`.
- Contract: `ArchitecturalFamilyContractTest`.

**Batch 13.1 P2 — Floor / Roof split (2026-09-23):**

- **Floor Slab** + **Beam Grid** are the composable hosts; **Floor Slab With Beams** stays a convenience composite
  (slab / beams / beam frames / center lines / top / bottom).
- **Roof Base** owns core types (`flat` / `shed` / `gable`) with **Eave Path** / **Ridge Path**.
- **Roof Generator** is the advanced specialty convenience node — do not expand it into more roof types;
  prefer composing Roof Base + future modifiers instead.

**Batch 13.2 / PATH_LIST (2026-09-23):**

- Multi-path architectural outputs use **`PATH_LIST`** (`ListElementKind.PATH`), not bare `LIST`.
- First consumers: **Beam Grid** `output_center_lines`, **Floor Slab With Beams** `output_beam_center_lines`,
  **Preview Curves** `input_paths`.
- Same typed-list rules as `POINT_LIST` / `FRAME_LIST`: same kind connects; different kinds unsupported;
  unconstrained `LIST` still bridges.

**Batch 13.2 / Architectural suggested connections (2026-09-23):**

- `node_recommendations.json` v2 prioritizes architectural PATH / PATH_LIST / BOX_FACE / FRAME_LIST chains
  (Roof eave → Railing, Floor top → Column Grid, Wall openings → Difference cutter, Window frames → Place On Frames).
- Contract: `ArchitecturalRecommendationContractTest`.

**Batch 13.2 / Architectural workflow presets (2026-09-23):**

- Built-in Architecture presets teach composable mini workflows (not God Nodes):
  - `architectural.workflow.wall_with_windows`
  - `architectural.workflow.floor_with_beam_grid`
  - `architectural.workflow.roof_with_eave`
- Contract: `ArchitecturalWorkflowPresetsContractTest`.

**Batch 13.2 status (2026-09-23):**

- **Code / contracts:** accepted (PATH_LIST, recommendation v2, workflow presets, playtest checklist).
- **Product playtest:** pending Minecraft client — do **not** green-check Preview→Apply or 5–10 min UX from static review alone.
- **Freeze now:** no more architectural node sprawl; no Batch 13.3 feature push until playtest (D then E, then A/B/C).
- **Deferred:** recommendation Top-N ranking contract; Roof multi-eave/ridge `PATH_LIST` when a real consumer appears.
- Playtest checklist: [`architecture/architectural-13.2-playtest-checklist.md`](./architecture/architectural-13.2-playtest-checklist.md).

---

## Checklist for new nodes

Before merging a new or remodeled node:

- [ ] Continuous scalars are `DOUBLE`, not `FLOAT`.
- [ ] Port ids match the table in §2 (or an existing domain convention already frozen for that family).
- [ ] Angle ports document and emit **degrees**.
- [ ] Spatial ports use Point / Vector / Block Position intentionally; no new Coordinate/Position-first API.
- [ ] Geometry centers/locations are `POINT` / `POINT_LIST`; multi-path outputs use `PATH_LIST`; directions are `VECTOR`; no spatial `ANY`.
- [ ] Building profiles/primitives default plane to **XZ** when unspecified.
- [ ] Optional drives use property fallback + connection override with clear UI.
- [ ] No `@NodeProperty` that cannot affect the node.
- [ ] Presets / migrations updated if port ids or types change.
- [ ] AI schema implications considered (one semantic type per player concept).
