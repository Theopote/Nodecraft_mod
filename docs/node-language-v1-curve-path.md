# Node Language v1 — Curve & Path

## PATH-first naming

Path operations consume and emit **PATH** where possible. LINE, POLYLINE, and CURVE connect to PATH implicitly.

| Legacy / avoid | Canonical display name | id |
|----------------|------------------------|-----|
| Curve Evaluate | **Evaluate Path** | `geometry.curves.evaluate_curve` |
| Polyline Length | **Path Length** | `geometry.curves.polyline_length` |
| Resample Polyline / Rebuild By Length | **Resample Path** | `geometry.curves.resample_path` |
| Offset Polyline In Plane | *(deleted — use Offset Path In Plane)* | `geometry.curves.offset_curve_plane` |

## Responsibility layers

| Layer | Nodes |
|-------|-------|
| Construct | Points To Path, Line, Arc, Bezier, … |
| Query | Path Length, Evaluate Path |
| Modify | Offset Path In Plane, Fillet Path Corners, Blend/Tween Paths |
| Sample | **Resample Path only** |
| Frames | Path Frames (parallel transport; no hidden resample) |
| Extract | Extract Path Points |

## Sampling Mode — Resample Path only

Explicit **Count** or **Spacing** arc-length sampling lives on **Resample Path** (`geometry.curves.resample_path`) alone.

Other path nodes use path vertices / internal curve samples as-is:

- **Path Frames** — frames at existing path vertices
- **Voxelize Path** — voxelize segment geometry between vertices
- **Offset Path In Plane** — offset existing vertices (no pre-resample)

Need uniform spacing? Insert **Resample Path** upstream.

## Evaluate Path

Single evaluator: `geometry.curves.evaluate_curve` (display **Evaluate Path**).

- Inputs: Path, normalized **t** ∈ [0..1]
- Outputs: Point, Tangent, Length, Valid
- Frame axes belong on **Path Frames** / **Deconstruct Frames**, not here.

## Resample Path outputs

Primary output: **Path** (`output_path`). Secondary: Points, Count, Length, Valid.

## Deleted / merged (V12→V13)

| Removed id | Migration |
|------------|-----------|
| `geometry.curves.evaluate_path` | → `geometry.curves.evaluate_curve` |
| `geometry.curves.rebuild_curve_length` | → `geometry.curves.resample_path` |
| `geometry.curves.resample_polyline_length` | → `geometry.curves.resample_path` |
| `geometry.curves.offset_polyline_plane` | → `geometry.curves.offset_curve_plane` |
