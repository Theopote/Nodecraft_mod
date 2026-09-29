# Node Language v2 — Reference Frame Transform Semantics (Graph V108)

**Status: ACTIVE** (Graph **V108**)

Locks Transform Frame rotation space and Euler order after V85 language freeze:

1. World-axis Euler XYZ rotation (pre-multiplied onto frame orientation)
2. UI labels: World Rotation X/Y/Z
3. Saved-state non-finite values fail at process time (no silent retention)

Related: [`node-language-v2-reference-frames.md`](./node-language-v2-reference-frames.md) (V85 foundation).

## Rotation space = WORLD

`reference.frames.transform_frame` applies Euler angles as **world-axis** rotations:

```text
R_world = rotateXYZ(rx, ry, rz)   // degrees → radians; JOML Matrix3d.rotateXYZ
outAxis = R_world · frameAxis
```

Equivalently: `R_out = R_world · R_frame` (pre-multiplication).

This is **not** local-frame rotation (`R_out = R_frame · R_local`). Example: a frame whose local X already equals world +Z, with World Rotation X = 90°, yields `outX ≈ (0,-1,0)` — rotating around world X, not around the frame's own X.

## Euler order = XYZ

Order is locked to JOML `Matrix3d.rotateXYZ(rx, ry, rz)`: rotate X, then Y, then Z about world axes.

## UI labels

| Port / property | Display name |
|-----------------|--------------|
| `input_rotation_x` / `rotationX` | World Rotation X |
| `input_rotation_y` / `rotationY` | World Rotation Y |
| `input_rotation_z` / `rotationZ` | World Rotation Z |

Port IDs and state keys are unchanged.

## Saved-state strictness

`setNodeState` assigns numeric values as-is (including non-finite). At process time, non-finite World Rotation properties fail closed via OptionalPortDrive — same as a connected NaN wire. Corrupted saved state is not silently retained.

## Deferred

- LOCAL rotation space (future optional `Rotation Space` enum)
- Scale / shear on FRAME (FRAME remains position + orientation only)
- FrameData constructor hardening (V47 deferred P2)

## Migration

V107 → V108 is a no-op format bump (runtime semantics / labels only).
