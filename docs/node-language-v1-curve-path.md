# Node Language v1 — Curve & Path

## PATH-first naming

Path operations consume and emit **PATH** where possible. LINE, POLYLINE, and CURVE connect to PATH implicitly.

| Legacy / avoid | Canonical display name | id |
|----------------|------------------------|-----|
| Curve Evaluate | **Evaluate Path** | `geometry.curves.evaluate_curve` |
| Polyline Length | **Path Length** | `geometry.curves.path_length` |
| Resample Polyline / Rebuild By Length | **Resample Path** | `geometry.curves.resample_path` |
| Offset Polyline In Plane | *(deleted — use Offset Path In Plane)* | `geometry.curves.offset_curve_plane` |

## Responsibility layers

| Layer | Nodes |
|-------|-------|
| Construct | Points To Path, Line, Arc, Bezier, … |
| Query | Path Length, Evaluate Path |
| Modify | Join / Reverse / Split / Trim Path, Offset Path In Plane, Fillet Path Corners, Blend/Tween Paths |
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

## Path modify ops (P2)

| Node | id | Role |
|------|-----|------|
| Join Paths | `geometry.curves.join_paths` | Concatenate Path A + Path B |
| Reverse Path | `geometry.curves.reverse_path` | Flip path direction |
| Split Path | `geometry.curves.split_path` | Split at normalized parameter |
| Trim Path | `geometry.curves.trim_path` | Sub-path between Start/End parameters |
| Closest Point On Path | `geometry.curves.closest_point_on_path` | Project query point onto path |
| Path Parameter At Point | `geometry.curves.path_parameter_at_point` | Normalized t for closest projection |

`reference.points.project_to_polyline` is retired — migrated to Closest Point On Path.

Use **Join Paths** after **Blend Paths** instead of a built-in joined output.

## Tween / Blend outputs

- **Tween Paths** → primary `PATH_LIST` (`output_paths`); no First Polyline / LIST/TREE convenience outputs.
- **Blend Paths** → primary `PATH` (`output_path`); no Joined Polyline (compose with Join Paths).

## Deleted / merged (V12→V13)

| Removed id | Migration |
|------------|-----------|
| `geometry.curves.evaluate_path` | → `geometry.curves.evaluate_curve` |
| `geometry.curves.rebuild_curve_length` | → `geometry.curves.resample_path` |
| `geometry.curves.resample_polyline_length` | → `geometry.curves.resample_path` |
| `geometry.curves.offset_polyline_plane` | → `geometry.curves.offset_curve_plane` |

## Id rename / retire (V14→V16)

| Legacy id | Canonical id |
|-----------|--------------|
| `geometry.curves.polyline_length` | `geometry.curves.path_length` |
| `reference.points.project_to_polyline` | `geometry.curves.closest_point_on_path` |

## Port changes (V15→V16)

| Node | Change |
|------|--------|
| Fillet Path Corners | `output_path` only; `output_polyline` removed (wires remapped to `output_path`) |
| Closest Point On Path | drops legacy `output_vector` / segment index ports from Project Point To Polyline |

## Port changes (V13→V14)

| Node | Change |
|------|--------|
| Tween Paths | `output_paths` PATH_LIST replaces legacy LIST/TREE outputs |
| Blend Paths | `output_path` PATH; dropped Joined Polyline / Curve outputs |
| Fillet Path Corners | added `output_path` PATH (polyline kept for compatibility) |
