# Node Type Conversion Guidelines

This document defines how type relationships work in the v1 node system.

Player-facing spatial concepts, numeric defaults, port-id conventions, and angle
units are frozen in [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).
This file owns **connectability policy** only.

## Rules

1. Port-level compatibility only allows `implicit-safe` relationships.
2. Any conversion with strategy, ambiguity, or information loss requires an explicit conversion node.
3. Unsupported type pairs must stay disconnected.
4. The classification source of truth is `TypeConversionRegistry`.

## Relationship Classes

### Implicit Safe

These are allowed directly at the port layer:

- identical types
- `ANY -> *`
- numeric family:
  - `INTEGER`
  - `FLOAT`
  - `DOUBLE`
- semantic aliases:
  - `COORDINATE <-> BLOCK_POS` (legacy alias pair)
  - `VECTOR <-> POSITION` (legacy alias pair)
  - `COORDINATE_LIST <-> BLOCK_LIST`
- location drivers of `POINT` inputs:
  - `POSITION -> POINT` (legacy continuous-location alias)
- specific geometry -> `GEOMETRY`
- generic list family compatibility

These relationships are considered safe because they do not require a user-facing policy choice.

### Explicit Required

These must stay as explicit nodes in the graph:

- `BLOCK_POS / COORDINATE -> POINT`
  - grid cell → continuous location; use **Block To Point** (corner vs center policy)
- `BLOCK_POS / COORDINATE -> VECTOR / POSITION`
  - corner vs center policy matters when treating a grid cell as a continuous vector
- `VECTOR -> POINT`
  - direction / displacement must not silently become a location
- `POINT -> COORDINATE / BLOCK_POS`
  - grid snap / rounding policy matters
- `BOX_FACE -> PLANE`
  - semantic conversion, not identity
- `SURFACE_STRIP -> GEOMETRY`
  - geometry construction step
- `GEOMETRY -> BLOCK_LIST / BLOCK_PLACEMENT_LIST`
  - voxelization / bake policy matters
- `LIST -> DATA_TREE`
  - branch layout policy matters; use **Graft List** (or Partition List)
- `DATA_TREE -> LIST`
  - flattens branch structure; use **Flatten Tree**
- `LIST / BLOCK_TYPE / STRING -> BLOCK_PALETTE`
  - legacy block-id bags; use **Create Block Palette**
- `SDF -> SCALAR_FIELD`
  - distance value wrap; use **Scalar Field From SDF**
- `SDF -> VECTOR_FIELD`
  - gradient policy; use **Vector Field From SDF Gradient**

Canonical spatial chain for new graphs:

```
Block Position  →  Block To Point  →  Point
Position (legacy) → Point            (implicit)
Vector            → Point            (explicit — do not wire Look Direction to Origin)
```

Current canonical explicit conversion nodes:

- [BlockToPointNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/reference/points/BlockToPointNode.java)
- [BlockToVectorNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/reference/points/BlockToVectorNode.java)
- [SnapPointToBlockNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/selection/SnapPointToBlockNode.java)
- [BoxFaceToPlaneNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/reference/planes/BoxFaceToPlaneNode.java)
- [SurfaceStripToGeometryNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/geometry/solids/SurfaceStripToGeometryNode.java)

### Unsupported

Unrelated domains stay disconnected:

- `BIOME -> ITEM_TYPE`
- `BLOCK_TYPE -> SOUND_EVENT`
- similar cross-domain semantic mismatches

## Current Implementation Boundary

`TypeConversionRegistry` currently classifies type relationships only.

It does **not**:

- auto-insert conversion nodes
- auto-run adapters
- hide lossy conversions behind port connectability

That boundary is intentional. The graph should remain explicit where semantics matter.

Pre-release: when language changes, update in-repo presets/tests rather than adding
behavior-preserving load migrations for abandoned on-disk variants.
