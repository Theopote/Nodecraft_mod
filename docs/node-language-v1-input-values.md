# Node Language v1 — Input Values

**Status: PASSED / FROZEN** (HEAD `b7bee1c9`, Graph **V34**)

Language unification for exactly **6** `input.values.*` value-source nodes: typed ports,
no graph-facing `ANY` / unconstrained `LIST`, no hidden coercion, and `Valid` where needed.

Shared helper: `ValueInputUtils`. Graph schema: **V34** remaps `input.basic.text_input` /
`color_picker` / `boolean_toggle` → `input.values.*`, tightens Color channels to `DOUBLE` +
`ColorData`, Value List options to `STRING_LIST`, drops Gradient `output_ramp`, and adds File
Path `output_valid`.

Related: [`node-language-v1-input-numeric.md`](./node-language-v1-input-numeric.md),
[`node-language-v1-input-context.md`](./node-language-v1-input-context.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Continuous scalars are `DOUBLE`** — Color RGBA channels and Gradient sample channels use
   `DOUBLE` (no new `FLOAT` ports).
2. **No graph-facing `ANY` / unconstrained `LIST`** on these six nodes.
3. **No hidden coercion** — Value List index override accepts `Integer` only (not
   `Number.intValue`); options accept only homogeneous `String` lists (no `String.valueOf`,
   no silent filtering of non-String elements — any foreign element fails the whole list).
4. **`Valid=true` ⇒ finite / parseable / non-empty** — Gradient numeric outputs finite;
   File Path syntax parseable; Value List options non-empty after resolve.

## Inventory (6)

| Display name | Type id | Legacy type id |
|--------------|---------|----------------|
| Text Input | `input.values.text_input` | `input.basic.text_input` |
| Color Picker | `input.values.color_picker` | `input.basic.color_picker` |
| Boolean Toggle | `input.values.boolean_toggle` | `input.basic.boolean_toggle` |
| Gradient Ramp | `input.values.gradient_ramp` | (unchanged) |
| Value List | `input.values.dropdown` | (id kept; display rename) |
| File Path Input | `input.values.file_path` | (unchanged) |

All six are **PURE**.

## Text Input

| Port | Type | Notes |
|------|------|-------|
| `output_text` | `STRING` | Current text |
| `output_length` | `INTEGER` | Character length |

Always multiline editor. **No `multiline` property.** Max Length truncates explicitly.

## Color Picker

| Port | Type | Notes |
|------|------|-------|
| `output_color` | `COLOR` | Payload is `ColorData` |
| `output_red/green/blue/alpha` | `DOUBLE` | Channels in 0..1 |

Property display **Edit Alpha** (UI). Internal ImGui/`util.Color` may remain for editing.

## Boolean Toggle

| Port | Type | Notes |
|------|------|-------|
| `output_value` | `BOOLEAN` | Graph ports strict |

Legacy persisted state may still coerce string/number on restore (acceptable).

## Value List (`input.values.dropdown`)

| Port | Type | Notes |
|------|------|-------|
| `input_index` | `INTEGER` | Override; `Integer` only |
| `input_options` | `STRING_LIST` | Homogeneous Strings only; any non-String → empty / `Valid=false` |
| `output_index` | `INTEGER` | Clamped into `[0, size-1]` when non-empty |
| `output_value` | `STRING` | Selected option text |
| `output_options` | `STRING_LIST` | Resolved options |
| `output_valid` | `BOOLEAN` | `false` when options empty |

Property CSV `Options = "A, B, C"` is the unconnected fallback.

## Gradient Ramp

| Port | Type | Notes |
|------|------|-------|
| `input_t` / `input_x` / `input_y` | `DOUBLE` | Sample coordinates |
| `output_color` | `COLOR` | `ColorData` when valid |
| `output_red/green/blue/alpha` / `output_t` | `DOUBLE` | Finite when valid; else `NaN` |
| `output_hex` | `STRING` | `#RRGGBB` or `""` when invalid |
| `output_valid` | `BOOLEAN` | Finite contract |

**Finite contract:** T/X/Y, Angle / Center / Radius, stop positions, stop RGBA must be finite;
RADIAL/BOX require `radius > 0`. On failure: `Valid=false`, `COLOR=null`, channels/`T` = `NaN`,
Hex `""`.

**No hidden EPS:** exact radius (no `Math.max(1e-9, radius)`); equal stops use upper stop for
LINEAR/SMOOTH; tiny real spans interpolate with true span.

**No `output_ramp` port.**

## File Path Input

| Port | Type | Notes |
|------|------|-------|
| `output_path` | `FILE_PATH` | **Raw** user string (portable; not absolute-ized) |
| `output_text` | `STRING` | Same raw path as text |
| Directory / Filename / Extension | `STRING` | Derived from parse (may absolute internally) |
| `output_has_value` | `BOOLEAN` | `!blank` |
| `output_valid` | `BOOLEAN` | blank → false; non-empty parseable → true; else false |

No `Files.exists`. PURE.

## Graph migration (V33→V34)

| Action | Detail |
|--------|--------|
| Type remap | `input.basic.text_input/color_picker/boolean_toggle` → `input.values.*` |
| Color channels | Drop wires from `output_red/green/blue/alpha` when DOUBLE source incompatible with target |
| Value List options | Drop incompatible wires on `input_options` / `output_options` after `STRING_LIST` |
| Gradient | Drop all wires from `output_ramp` |
| File Path | Add `output_valid` — no wire migration |

## Contract

- `InputValuesLanguageContractTest` — inventory, ColorData/DOUBLE, Value List strictness,
  Gradient Valid, File Path Valid, Text no multiline, V33→V34 remaps/drops.
- Format contract tests bumped to **V34**.
- `AnyAllowlistContractTest` — `input.values.` forbidden for ANY.
