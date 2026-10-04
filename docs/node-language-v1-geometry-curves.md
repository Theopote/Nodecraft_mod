# Geometry Curves — Node Language v1 (PATH v2)

**Status: PASSED / FROZEN** (historical Graph **V71** residue; `GraphFormatVersion.CURRENT` is stamp-only **1**)

Geometry Curves v2 freezes all **28** canonical `geometry.curves.*` nodes under PATH Language v2: connection-aware inputs via [`CurveInputUtils`](../src/main/java/com/nodecraft/nodesystem/util/CurveInputUtils.java), Valid+Error on every node, strict normalized arc-length parameter `t`, typed list ports only (no bare `LIST`), and removal of graph-facing `CURVE` / `POLYLINE` / `LINE` mirror outputs.

**Out of scope:** global `NodeDataType.CURVE` retirement; deprecated `CurveFrameAlongPathNode`; `setNodeState` exact-Integer hardening (P2); rewriting curve algorithms.

## Inventory (order 0–27)

| Order | Display | Id |
|------:|---------|-----|
| 0 | Points To Path | `geometry.curves.points_to_path` |
| 1 | Path To Points | `geometry.curves.path_to_points` |
| 2 | Face Edge To Path | `geometry.curves.edge_to_curve` |
| 3 | Box Face Boundary Path | `geometry.curves.face_boundary_curve` |
| 4 | Arc | `geometry.curves.arc` |
| 5 | Bezier | `geometry.curves.bezier` |
| 6 | Interpolate Spline | `geometry.curves.interpolate_spline` |
| 7 | B-Spline | `geometry.curves.b_spline` |
| 8 | NURBS | `geometry.curves.nurbs_curve` |
| 9 | Parabola On Plane | `geometry.curves.parabola_on_plane` |
| 10 | Helix | `geometry.curves.helix` |
| 11 | Infinity Curve On Plane | `geometry.curves.infinity_curve_on_plane` |
| 12 | Join Paths | `geometry.curves.join_paths` |
| 13 | Reverse Path | `geometry.curves.reverse_path` |
| 14 | Split Path | `geometry.curves.split_path` |
| 15 | Trim Path | `geometry.curves.trim_path` |
| 16 | Explode Path | `geometry.curves.explode_path` |
| 17 | Extend Path | `geometry.curves.extend_path` |
| 18 | Fillet Path Corners | `geometry.curves.fillet_polyline_corners` |
| 19 | Offset Path In Plane | `geometry.curves.offset_curve_plane` |
| 20 | Resample Path | `geometry.curves.resample_path` |
| 21 | Path Length | `geometry.curves.path_length` |
| 22 | Evaluate Path | `geometry.curves.evaluate_curve` |
| 23 | Closest Point On Path | `geometry.curves.closest_point_on_path` |
| 24 | Rainbow Curve Offset | `geometry.curves.rainbow_curve_offset` |
| 25 | Blend Paths | `geometry.curves.blend_curves` |
| 26 | Tween Paths | `geometry.curves.tween_curves` |
| 27 | Voxelize Path | `geometry.curves.voxelize_curve` |

All nodes are `PURE`. Deprecated `geometry.curves.frame_along_path` is excluded from this inventory.

## PATH canonical public language

**Graph-facing types:** `PATH`, `PATH_LIST`, `POINT_LIST`, `DOUBLE_LIST`, `INTEGER_LIST`, `POINT`, `VECTOR`, `DOUBLE`, `INTEGER`, `BOOLEAN`, `STRING`, `BLOCK_LIST`, and domain types (`BOX_FACE`, `PLANE`, etc.).

**No bare `LIST`** on canonical curve ports. Internal [`PathUtils.resolvePath()`](../src/main/java/com/nodecraft/nodesystem/nodes/geometry/curves/util/PathUtils.java) still accepts `PathData` / `LineData` / `PolylineData` / `Curve` for implementation; nodes wrap to `PathData` at the graph boundary.

### Removed mirror ports (V71)

| Removed | Remapped to |
|---------|-------------|
| `output_curve`, `output_polyline`, `output_line` | `output_path` |
| `output_control_polygon` | `output_control_path` |
| `output_polylines` | `output_paths` |
| `output_center_polyline` | `output_center_path` |
| NURBS `input_weights` (`LIST`) | `input_weights` (`DOUBLE_LIST`) |
| NURBS `output_effective_degree` | dropped (use `output_degree`) |
| Box Face Boundary `output_corner_indices` (`LIST`) | `INTEGER_LIST` |
| Evaluate `clampT` node state | dropped |

## Valid + Error

Every curve node exposes `output_valid` (`BOOLEAN`) and `output_error` (`STRING`).

| Outcome | Behavior |
|---------|----------|
| Success | `Valid=true`, `Error=""`, typed outputs populated |
| Invalid | `Valid=false`, actionable `Error`, continuous outputs `null` (never NaN) |

Connection-aware optional ports use [`OptionalPortDrive`](../src/main/java/com/nodecraft/nodesystem/util/OptionalPortDrive.java): connected + wrong type or invalid value → whole-node invalid.

## Normalized arc-length parameter `t`

> `t ∈ [0,1]` = normalized **arc-length** parameter (`arcLength / totalLength`).

| Node | Open path | Closed path |
|------|-----------|-------------|
| **Evaluate Path** | `t` finite in `[0,1]`; no wrap | `t mod 1` allowed |
| **Split Path** | `0 < t < 1` strict | same |
| **Trim Path** | `0 ≤ start < end ≤ 1` | `0 ≤ start,end ≤ 1`, `start ≠ end`; `start > end` = seam-crossing trim |
| **Closest Point On Path** | outputs actual normalized `t` | same |

No silent clamp at the graph boundary. Evaluate **Clamp t** property removed at V71.

## Input strictness

[`CurveInputUtils`](../src/main/java/com/nodecraft/nodesystem/util/CurveInputUtils.java) centralizes:

- **INTEGER:** exact via `StrictIntegerUtils`; no `Number.intValue()` on graph inputs
- **DOUBLE:** finite only; positive / non-negative variants where applicable
- **POINT_LIST (constructors):** Collection of `PointData` only via `CurveInputUtils.resolveStrictPointListBounded`; mixed types / non-finite / oversize → fail (no silent skip)
- **Enums:** known string values only
- **NURBS weights:** unconnected → uniform defaults; connected → exact `List<Double>` (`instanceof Double`, not any `Number`), length = control count, all finite `> 0`
- **NURBS degree:** `1..5`, `< controlCount`; no silent clamp
- **Angles:** degrees on graph-facing ports (Arc, Fillet, etc.)
- **Optional Center / Plane / Normal / Axis:** connected-invalid ≠ unconnected. Plane connected-invalid does **not** fall through to Normal or the default plane.

## PATH value invariant

[`PathData`](../src/main/java/com/nodecraft/nodesystem/datatypes/PathData.java) factories fail closed: `fromLine` / `fromPolyline` / `fromCurve` / `wrap` return `null` when the payload is null, non-finite, or structurally illegal. Kind always matches the non-null payload.

## Workload budgets

[`GenerationLimits`](../src/main/java/com/nodecraft/nodesystem/util/GenerationLimits.java):

| Cap | Role |
|-----|------|
| `MAX_CURVE_SAMPLES` | per-path sample count (Resample Count mode, producer segments, etc.) |
| `MAX_CURVE_OUTPUT_PATHS` | Tween / Rainbow path count |
| `MAX_CURVE_TOTAL_SAMPLES` | `count × samplesPerPath` long product |
| `MAX_CURVE_CONTROL_POINTS` | constructor POINT_LIST size (alias of `MAX_PROFILE_VERTICES`) |
| `MAX_CURVE_EVALUATION_WORK` | `controlCount × sampleCount` (alias of `MAX_CURVE_TOTAL_SAMPLES`) |

Graph nodes **validate and fail**; `clampSegments` / `clampPositiveCount` remain for internal callers only.

## Selected node semantics

| Node | V71 rule |
|------|----------|
| **Join Paths** | endpoint match within connection-aware tolerance; no auto-reverse, no bridge |
| **Explode Path** | degenerate zero-length segment → fail closed |
| **Fillet Path Corners** | transactional: any interior corner that cannot satisfy radius → whole-node invalid |
| **Resample Path** | Count or Spacing only; ORIGINAL → invalid |
| **Tween / Rainbow** | workload budget enforced |
| **Arc** | directed sweep (end − start) may exceed ±360°; derived sweep/length/samples must be finite |
| **Points To Path** | Close Path uses `PathUtils.CLOSED_DISTANCE_EPSILON`; Count is serialized point count (includes closing vertex) |

## Producer outputs

Producers (Bezier, Arc, NURBS, …) emit primary **`output_path`** (`PATH`), optional **`output_points`** (`POINT_LIST`), **`output_control_path`** (`PATH`), Length, Count, Valid, Error. No `output_curve` / `output_polyline` mirrors.

## Migration (V70 → V71)

[`migrateV70ToV71`](../src/main/java/com/nodecraft/nodesystem/graph/GraphMigrationRegistry.java):

- Remap deleted mirror output ports → `output_path` / `output_paths` / `output_center_path` / `output_control_path`
- NURBS weights `LIST` → `DOUBLE_LIST` (incompatible wires dropped)
- Rainbow / Box Face typed list remaps
- Strip Evaluate `clampT` node state
- Drop wires to removed ports with no canonical target

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GeometryCurvesLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.PathLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GeometryCurvesFamilyContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
```

Related: [`node-language-v1-curve-path.md`](./node-language-v1-curve-path.md) (PATH ops naming and responsibility layers).
