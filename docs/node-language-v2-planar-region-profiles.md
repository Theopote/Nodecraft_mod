# Node Language v2 — Planar Region / Profiles

**Status: PASSED / FROZEN** (Graph **V91**; V73 remains historical Polygon Profile Language v1)

Type stack for planar modeling under `geometry.profiles` (+ `geometry.solids.extrude_region`):

```text
PATH
  ↓
POLYGON_PROFILE   (simple closed planar loop, no holes — V73 unchanged)
  ↓
PLANAR_REGION     (outer + 0..N holes + plane — V91)
  ↓
Extrude Region / Boolean / Offset
```

## PLANAR_REGION

[`PlanarRegionData`](../src/main/java/com/nodecraft/nodesystem/datatypes/PlanarRegionData.java):

- `outer` : `PolygonProfileData`
- `holes` : `List<PolygonProfileData>`
- `plane` : `PlaneData`

Validated by [`PlanarRegionValidator`](../src/main/java/com/nodecraft/nodesystem/util/PlanarRegionValidator.java): coplanar, JTS topology with holes, hole containment, non-overlapping holes, vertex budgets.

**NodeDataType:** `PLANAR_REGION`, `PLANAR_REGION_LIST`  
**Distinct from** Minecraft block-space `REGION`.

## JTS mapping

[`ProfilePlanarOps`](../src/main/java/com/nodecraft/nodesystem/nodes/geometry/profiles/ProfilePlanarOps.java):

| JTS | NodeCraft |
|-----|-----------|
| Polygon (no holes) | PlanarRegionData with empty holes |
| Polygon + interior rings | PlanarRegionData with holes |
| MultiPolygon | PLANAR_REGION_LIST |

`POLYGON_PROFILE` conversion still rejects holes (legacy simple-loop path).

## Node upgrades (inventory still 23 profiles)

| Node | V91 change |
|------|------------|
| Profile Boolean 2D | `output_region` / `output_regions`; Difference with holes succeeds |
| Profile Offset In Plane | `output_region` / `output_regions` |
| Annulus On Plane | `output_region` + `output_area` |
| Sector / Annular Sector | `0 < \|sweep\| < 360°` |
| Generators | `ProfileConstructionUtils.tryCreateProfile` fail-closed |

Convenience ports `output_profile` / `output_profiles` remain (outer of primary / outers list).

## Sector sweep language

```text
Sweep = End − Start (degrees)
Valid: 0 < |Sweep| < 360
```

Full disk/ring → Circle / Annulus, not Sector.

## Extrude Region

`geometry.solids.extrude_region` (order 22):

```text
outer prism = extrude(outer)
hole prisms = extrude(each hole)
geometry = Difference(outer, union(holes))
```

Existing `geometry.solids.extrude` unchanged (`POLYGON_PROFILE` only).

## Out of scope (P2)

- Centroid vs vertex-average Center
- Triangulate Region
- Failure numeric `0` vs `NaN` policy

## Contract tests

- Historical: `GeometryProfilesLanguageContractTest` (V73)
- v2: `GeometryProfilesLanguageV2ContractTest` (V91)
