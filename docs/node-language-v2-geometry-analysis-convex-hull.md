# Geometry Analysis / Convex Hull 3D — Node Language v2

**Status: PASSED / FROZEN** (current graph format)

Focused remediation for `geometry.analysis.convex_hull_3d` only. **Block Bounds** and **Geometry Bounds** remain under [node-language-v1-geometry-analysis.md](node-language-v1-geometry-analysis.md).

## Node

| Order | Display | Id | Effect |
|------:|---------|-----|--------|
| 2 | Convex Hull 3D From Points | `geometry.analysis.convex_hull_3d` | `PURE` |

## Ports

| Port | Type |
|------|------|
| Points | POINT_LIST |
| Mesh | TRIANGLE_MESH |
| Hull Vertices | POINT_LIST |
| Triangle Count | INTEGER |
| Valid / Error | BOOLEAN / STRING |

**Removed:** raw `output_faces` (`LIST` of triangle soup). Migration V95→V96 drops wires from `output_faces`.

## TriangleMeshData

Indexed mesh carrier:

- `vertices`: ordered `Vector3d` hull sites (shared across triangles)
- `triangles`: each `int[3]` references three vertex indices

Factory `tryCreate(...)` → `null` on null/empty input, non-finite coordinates, out-of-range indices, or degenerate (zero-area) triangles.

## Input contract

1. **Strict POINT_LIST** via `PointUtils.resolveStrictPointList` — any non-`PointData` or non-finite entry → `Valid=false` (no silent drop).
2. **De-duplication** via `ConvexHull3d.dedupePoints` (light coincident-point merge).
3. **Budget** on **deduped** count:
   - `< 4` unique sites → fail
   - `> Max Points` property → fail
4. **Property validation** at process time:
   - `Max Points` must be in `[4, GenerationLimits.MAX_CONVEX_HULL_3D_POINTS]` (hard cap **96**)

## Hull algorithm

Brute-force supporting-plane discovery (O(n⁴), capped at 96 sites), then:

1. Group discovered planes by quantized normal + distance
2. Collect coplanar hull vertices per plane
3. 2D convex hull on plane projection
4. Fan triangulate each face polygon → non-overlapping triangles

Example: unit cube 8 corners → **12** triangles (6 faces × 2), not overlapping coplanar soup.

Degenerate inputs (collinear, coplanar, `< 4` unique sites) → `Valid=false` (no 2D fallback).

## Failure outputs

| Port | Value |
|------|-------|
| Mesh | `null` |
| Hull Vertices | `[]` |
| Triangle Count | `0` |
| Valid | `false` |
| Error | actionable message |

Graph format is stamp-only (`CURRENT = 1`). Historical `output_faces` wires are gone; use `output_mesh` (`TRIANGLE_MESH`).

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GeometryAnalysisLanguageContractTest"
```
