# Node Language v2 — Reference Frames

**Status: PASSED / FROZEN** (Graph **V85** foundation; **V108** transform semantics — see
[`node-language-v2-reference-frames-transform-semantics.md`](./node-language-v2-reference-frames-transform-semantics.md);
V47 remains historical v1)

Language modernization for the eight canonical `reference.frames.*` nodes:
Valid+Error on fallible nodes, OptionalPortDrive on optional ports, strict connected X Hint
semantics, BoxFaceValidator on Face Center, Transform input canonicalization, and
Deconstruct Frames list cap.

Related: [`node-language-v1-reference-frames.md`](./node-language-v1-reference-frames.md),
[`node-language-v2-reference-frames-transform-semantics.md`](./node-language-v2-reference-frames-transform-semantics.md),
[`node-language-v1-frame-plane.md`](./node-language-v1-frame-plane.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## FRAME invariant

Unchanged from v1 — orthonormal right-handed orientation only (no scale/shear).

## Inventory (orders 0–7)

| Order | Display name | Type id | Valid+Error |
|------:|--------------|---------|:-----------:|
| 0 | Face Center Frame | `reference.frames.frame_from_face` | yes |
| 1 | Sphere Surface Frame | `reference.frames.sphere_surface_frame` | yes |
| 2 | World Frame | `reference.frames.world_frame` | no (constant) |
| 3 | Construct Frame | `reference.frames.construct_frame` | yes |
| 4 | Frame From Plane | `reference.frames.frame_from_plane` | yes |
| 5 | Transform Frame | `reference.frames.transform_frame` | yes |
| 6 | Deconstruct Frame | `reference.frames.deconstruct_frame` | yes |
| 7 | Deconstruct Frames | `reference.frames.deconstruct_frames` | yes |

## Core rules

1. **Valid + Error** on all fallible nodes; **World Frame** remains constant-only (Frame output only).
2. **OptionalPortDrive** — Construct Origin/X/Y; Transform Translation + World Rotation X/Y/Z (degrees):
   unconnected → documented default or node property; connected valid → wire; connected invalid → fail.
3. **Connected X Hint** (Frame From Plane, Sphere Surface Frame) — uses
   `FrameUtils.fromPlaneRequireHint` / `fromNormalRequireHint`; **no cardinal fallback** when connected.
   Unconnected → `fromPlane(plane, null)` / `fromNormal(..., null)` with deterministic fallback.
4. **Construct parallel axes** — when **both** X and Y are connected and parallel → `Valid=false`.
5. **Face Center Frame** — `BoxFaceValidator.validate(face)` before geometry.
6. **Transform Frame** — input `FRAME` canonicalized via `orthonormalized()` before transform.
7. **Deconstruct boundaries** — single/list entries must `orthonormalized()` or fail closed.
8. **Deconstruct Frames cap** — `list.size() > GenerationLimits.MAX_LIST_ELEMENTS` fails preflight.
9. **FrameUtils public API unchanged** — nodes call existing helpers plus RequireHint variants.
10. **Transform Frame rotation (V108)** — World Rotation X/Y/Z are world-axis Euler **XYZ** degrees
    (`R_out = R_world · R_frame`); non-finite saved-state values fail at process time.

## Migration (V84 → V85)

Format bump only. Error ports additive; order is catalog metadata; no wire or state migration.
