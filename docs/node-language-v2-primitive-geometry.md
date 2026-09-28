# Node Language v2 — Primitive Geometry

**Status: PASSED / FROZEN** (Graph **V90**; V74 remains historical v1)

Continuous analytic primitives under `geometry.primitives` (31 nodes, orders 0–30). V90 closes the continuous/discrete bounds boundary on deconstruct nodes and adds Deconstruct Torus + Deconstruct Capsule.

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Inventory (order 0–30)

| Order | Display | Id |
|------:|---------|-----|
| 0 | Box by Center + Size | `geometry.primitives.box` |
| 1 | Box by Corner + Size | `geometry.primitives.box_from_corner_size` |
| 2 | Box by Two Corners | `geometry.primitives.box_from_corners` |
| 3 | Sphere By Center Radius | `geometry.primitives.sphere` |
| 4 | Sphere By Diameter | `geometry.primitives.sphere_from_diameter` |
| 5 | Cylinder By Axis Radius | `geometry.primitives.cylinder` |
| 6 | Cone By Base Apex Radius | `geometry.primitives.cone` |
| 7 | Frustum By Two Centers Radii | `geometry.primitives.frustum_cone` |
| 8 | Capsule By Axis Radius | `geometry.primitives.capsule` |
| 9 | Hemisphere By Center Axis Radius | `geometry.primitives.hemisphere` |
| 10 | Torus By Center Axis Radii | `geometry.primitives.torus` |
| 11 | Ellipsoid By Center Radii | `geometry.primitives.ellipsoid` |
| 12 | Square Pyramid | `geometry.primitives.square_pyramid` |
| 13 | Tetrahedron By Center Edge | `geometry.primitives.tetrahedron` |
| 14 | Octahedron By Center Size | `geometry.primitives.octahedron` |
| 15 | Icosahedron By Center Edge | `geometry.primitives.icosahedron` |
| 16 | Dodecahedron By Center Edge | `geometry.primitives.dodecahedron` |
| 17 | Deconstruct Box Geometry | `geometry.primitives.deconstruct_box` |
| 18 | Deconstruct Sphere | `geometry.primitives.deconstruct_sphere` |
| 19 | Deconstruct Cylinder | `geometry.primitives.deconstruct_cylinder` |
| 20 | Deconstruct Cone | `geometry.primitives.deconstruct_cone` |
| 21 | Deconstruct Frustum Cone | `geometry.primitives.deconstruct_frustum_cone` |
| 22 | Deconstruct Hemisphere | `geometry.primitives.deconstruct_hemisphere` |
| 23 | Deconstruct Ellipsoid | `geometry.primitives.deconstruct_ellipsoid` |
| 24 | Deconstruct Prism | `geometry.primitives.deconstruct_prism` |
| 25 | Deconstruct Tetrahedron | `geometry.primitives.deconstruct_tetrahedron` |
| 26 | Deconstruct Octahedron | `geometry.primitives.deconstruct_octahedron` |
| 27 | Deconstruct Icosahedron | `geometry.primitives.deconstruct_icosahedron` |
| 28 | Deconstruct Dodecahedron | `geometry.primitives.deconstruct_dodecahedron` |
| 29 | Deconstruct Torus | `geometry.primitives.deconstruct_torus` |
| 30 | Deconstruct Capsule | `geometry.primitives.deconstruct_capsule` |

## Continuous vs discrete bounds (V90)

Deconstruct nodes that expose **Bounding Box** and **Region** use a strict dual-track path:

```text
Continuous Geometry
        ↓
GeometryBoundsResolver.resolve  →  Bounding Box (continuous AABB)
        ↓
BoxBlockGenerator.regionFromBoundingBox  →  Region (block-space)
```

**Do not** derive Bounding Box from `GeometryVoxelizer.createBoundingRegion` — voxelizers may inflate small radii for block coverage.

| Port | Semantics |
|------|-----------|
| Bounding Box | Geometric axis-aligned bounds (continuous) |
| Region | Block-space bounds derived from continuous AABB |

## Shared input contract (unchanged from V74)

[`PrimitiveInputUtils`](../src/main/java/com/nodecraft/nodesystem/util/PrimitiveInputUtils.java):

- unconnected → property/default
- connected valid → override
- connected invalid/null → fail closed (no property washout)
- POINT rejects `LineData`

All 31 nodes: `PURE`, `Valid` + `Error`, no raw `ANY` / `LIST` / `LINE` / `POLYLINE`.

## Plane orientation note

[`PlaneData`](../src/main/java/com/nodecraft/nodesystem/datatypes/PlaneData.java) is **Origin + Normal** only (no stable local X / roll). Box generators derive X/Z from normal via reference axis — consistent with current Plane contract. If Plane later gains a full basis, Box orientation must follow.

## Torus policy

Ring torus only: `0 < minorRadius < majorRadius`. Horn/spindle types are out of scope until explicitly modeled.

## Contract tests

- Historical v1 fence: `GeometryPrimitivesLanguageContractTest` (V74 orders 0–28)
- v2 fence: `GeometryPrimitivesLanguageV2ContractTest` (V90, 31 nodes, continuous bounds, round trips)
