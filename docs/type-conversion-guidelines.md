# Node Type Conversion Guidelines

This document defines how type relationships work in the v1 node system.

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
  - `COORDINATE <-> BLOCK_POS`
  - `VECTOR <-> POSITION`
  - `COORDINATE_LIST <-> BLOCK_LIST`
- specific geometry -> `GEOMETRY`
- generic list family compatibility

These relationships are considered safe because they do not require a user-facing policy choice.

### Explicit Required

These must stay as explicit nodes in the graph:

- `POINT -> COORDINATE / BLOCK_POS`
  - grid snap / rounding policy matters
- `BOX_FACE -> PLANE`
  - semantic conversion, not identity
- `SURFACE_STRIP -> GEOMETRY`
  - geometry construction step
- `GEOMETRY -> BLOCK_LIST / BLOCK_PLACEMENT_LIST`
  - voxelization / bake policy matters

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
