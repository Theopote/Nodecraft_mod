# Node Language v2 — Planar Region / Profiles

**Status: PASSED / FROZEN** (Graph **V92**; V91 = region-output fence; V73 = historical Polygon Profile Language v1)

Type stack for planar modeling under `geometry.profiles` (+ `geometry.solids.extrude_region`):

```text
PATH
  ↓
POLYGON_PROFILE   (simple closed planar loop, no holes — V73 unchanged)
  ↓
PLANAR_REGION     (outer + 0..N holes + plane — V91)
  ↓
Region Boolean / Region Offset / Extrude Region
```

## Two-layer language

**Profile layer** — generators (Rectangle, Circle, Polygon, Gear, …) output `POLYGON_PROFILE`.

**Region layer** — modeling ops take/emit `PLANAR_REGION`:

| Node | ID | Role |
|------|-----|------|
| Profile To Region | `geometry.profiles.profile_to_region` | `POLYGON_PROFILE` → `PLANAR_REGION` (no holes) |
| Region Boolean 2D | `geometry.profiles.region_boolean_2d` | `PLANAR_REGION` × 2 → `PLANAR_REGION_LIST` |
| Region Offset In Plane | `geometry.profiles.region_offset_plane` | `PLANAR_REGION` → `PLANAR_REGION_LIST` |

Profile Boolean / Profile Offset remain as **convenience** nodes: internally `Profile → Region → Region op`.

Composable chain:

```text
Profile → Region → Boolean → Offset → Boolean → Extrude → Geometry
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

`POLYGON_PROFILE` conversion still rejects holes (legacy simple-loop path). Shared `booleanRegions` / `offsetRegion` power both Profile convenience and Region nodes.

## Inventory

`geometry.profiles`: **26** nodes (orders 0–25). New in V92: orders 23–25.

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

- `PlanarRegionData` constructor canonical invariant (validate in ctor / private + of/tryCreate)
- Centroid vs vertex-average Center
- Triangulate Region
- Failure numeric `0` vs `NaN` policy

## Contract tests

- Historical: `GeometryProfilesLanguageContractTest` (V73 inventory)
- v2: `GeometryProfilesLanguageV2ContractTest` (V91 region outputs)
- v3: `GeometryProfilesLanguageV3ContractTest` (V92 region modeling composability)
