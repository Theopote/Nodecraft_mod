# Node Language v1 — Placement

**Status: PASSED / FROZEN** (Graph **V54**)

Geometry place/orient plus block-grid transforms under `transform.placement` (8 nodes).

Related: [`node-language-v1-basic-transforms.md`](./node-language-v1-basic-transforms.md),
[`node-language-v1-deformations.md`](./node-language-v1-deformations.md),
[`node-language-v1-reference-frames.md`](./node-language-v1-reference-frames.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Product boundary

```text
placement = geometry place/orient + block-grid transforms (8 nodes)
BLOCK_POS → cellCenter → transform → floor snap → BLOCK_POS
no RoundingMode / CUSTOM axis-plane enums
FRAME_LIST strict; Frame XOR Frames by connection
place-on-frames transactional + geometry instance cap
Geometry output only (no bare LIST)
Pivot / X Hint: OptionalPortDrive
BLOCK_LIST: order + duplicates preserved
```

## Inventory (8)

| order | Display name | Type id |
|------:|--------------|---------|
| 0 | Place Geometry On Frames | `transform.placement.place_geometry_on_frames` |
| 1 | Place Geometry On Plane | `transform.placement.place_geometry_on_plane` |
| 2 | Orient Geometry To Frame | `transform.placement.orient_geometry_to_frame` |
| 3 | Offset Block Position | `transform.placement.offset_block_position` |
| 4 | Offset Block Positions | `transform.placement.offset_block_positions` |
| 5 | Rotate Block Positions | `transform.placement.rotate_block_positions` |
| 6 | Scale Block Positions | `transform.placement.scale_block_positions` |
| 7 | Mirror Block Positions | `transform.placement.mirror_block_positions` |

Renamed at V54 (Coordinate → Block Position):

| Legacy id | New id |
|-----------|--------|
| `…offset_coordinate` | `…offset_block_position` |
| `…offset_coordinates` | `…offset_block_positions` |
| `…rotate_coordinates` | `…rotate_block_positions` |
| `…scale_coordinates` | `…scale_block_positions` |
| `…mirror_coordinates` | `…mirror_block_positions` |

## Block-grid snap pipeline

Canonical continuous entry for Rotate / Scale / Mirror:

```text
BLOCK_POS → BlockSpace.cellCenter → continuous transform → BlockSpace.snapCellCenter → BLOCK_POS
```

`snapCellCenter` is an alias of `pointToBlockFloor` (containing cell). Placement never uses
`pointToBlockNearest` / `Math.round` for world snap — nearest breaks cell-center round-trips
(`(0.5,0.5,0.5) → (1,1,1)`).

Offset Block Position(s) round **displacement** components with `Math.round`, then `BlockPos.add`
(integer displacement, not world-space cell snap).

## Geometry placement

### Place Geometry On Frames

```text
Frames connected → Frames only (strict FRAME_LIST)
Frame connected  → Frame only
both connected   → fail
neither          → fail
connected-invalid → fail (no cross-fallback)
any placeOnFrame null → whole node fail (transactional)
frames.size() > MAX_GEOMETRY_INSTANCES → fail
outputs: Geometry + Count + Error + Valid (no Geometries LIST)
```

### Place Geometry On Plane

- Pivot: `OptionalPortDrive` (unconnected → origin).
- X Hint unconnected → auto tangent (cardinal fallback OK via `FrameUtils.fromPlane`).
- X Hint connected valid → required projection (`fromPlaneRequireHint`); zero projection → fail.
- X Hint connected invalid → fail closed.

### Orient Geometry To Frame

Pivot: `OptionalPortDrive` only; algorithm unchanged.

## Shared helpers

- `BlockSpace.cellCenter` / `snapCellCenter`
- `FrameUtils.resolveStrictFrameList` / `fromPlaneRequireHint`
- `OptionalPortDrive`
- `GenerationLimits.MAX_GEOMETRY_INSTANCES` (16384)

## Migration

`migrateV53ToV54`: remaps five Coordinate type ids; drops `output_geometries` wires;
strips `roundingMode`; maps legacy `CUSTOM` rotationAxis → `Y_AXIS`, mirrorPlane → `XZ`.
