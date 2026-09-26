# Node Language v1 — FileIO

**Status: PASSED / FROZEN** (Graph **V56**)

Local file import helpers under `utilities.fileio` (3 nodes). The frozen rule for this
family is: **typed IMAGE / COLOR_LIST payloads, allowlist-only path access, structure-only
VOX — no graph self-grant and no hidden stone/placements.**

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md),
[`contracts/preview-side-effects.md`](./contracts/preview-side-effects.md) (`FILE_IO`).

## Product boundary

```text
fileio = Read Image + Image Sampler + Import VOX (3)
IMAGE protocol: sample dims == colors length
decode after metadata + file-byte caps
allowlist via ImportAccessPolicy (no graph self-grant)
COLOR_LIST / DOUBLE_LIST / INTEGER_LIST typed
Import VOX = structure only (no stone / no placements)
Sampler PURE; exact coords; GenerationLimits budgets
```

## Inventory (3)

| order | Display name | Type id |
|------:|--------------|---------|
| 0 | Read Image | `utilities.fileio.read_image` |
| 1 | Image Sampler | `utilities.fileio.image_sampler` |
| 2 | Import VOX | `utilities.fileio.import_vox` |

## IMAGE protocol

`ImageData` carries:

```text
sourceWidth, sourceHeight
sampleWidth, sampleHeight
sampleStep
colors  // size == sampleWidth * sampleHeight, row-major
```

Read Image → Image Sampler is `IMAGE` → sample (not Width/Height + Pixel Colors LIST).

When IMAGE is present, Width/Height/Aspect report **sample** dims. Metadata-only mode
emits null IMAGE and reports **source** dims.

## Read Image

- Effect: `FILE_IO`
- Path gated by `ImportAccessPolicy` (default allowlist only; graphs cannot set Allow External)
- Caps: `GenerationLimits.MAX_IMAGE_FILE_BYTES` then metadata `(w×h)` vs `MAX_IMAGE_PIXELS`
- Modes (OptionalPortDrive / property): `METADATA_ONLY` / `FULL` / `DOWNSAMPLED`
  - `FULL` over budget → fail closed (no metadata sneak)
  - `DOWNSAMPLED` may raise effective step for budget; prefer subsampling
- Downsample Step: exact Integer `≥1` quality request (not a safety override)
- No Max Pixels / Allow External ports or state

## Image Sampler

- Effect: **`PURE`**
- Inputs: `IMAGE` + UV or X/Y by Coordinate Mode property
- Finite doubles required for active mode; NaN/Inf/missing → fail
- No Width / Height / Pixel Colors LIST inputs

## Import VOX

- Effect: `FILE_IO`
- Caps: `MAX_VOX_FILE_BYTES`, `MAX_IMPORTED_VOXELS`
- Origin: `OptionalPortDrive` BLOCK_POS (connected null → fail)
- Outputs: Blocks `BLOCK_LIST`, Voxel Colors `COLOR_LIST`, Color Indices `INTEGER_LIST`,
  sizes, count, version, Valid, Error
- No Block Placements, Default Block / stone, Block Type, Max Voxels, Allow External
- Z Up To Minecraft Y property kept (VOX Z → Minecraft Y)

## Migration

`migrateV55ToV56`: strips `allowExternalPaths` / `maxPixels` / `maxVoxels` /
`defaultBlock` / `defaultBlockType` state; drops wires to obsolete Read Image /
Image Sampler / Import VOX ports (`input_max_pixels`, `input_allow_external_paths`,
`input_max_voxels`, `input_block_type`, `output_placements`, `input_pixel_colors`,
`input_image_width` / `input_image_height` / `input_width` / `input_height`).
