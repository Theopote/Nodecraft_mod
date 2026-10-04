# Node Language v2 — Input Values

**Status: PASSED / FROZEN** (Graph **V110**; V34 remains historical v1)

Optional-drive modernization plus freeze hardening for Value List, Text, and Boolean.
Aligns optional inputs with the global OptionalPortDrive rule used by Planes, Frames, and Patterns.

Related: [`node-language-v1-input-values.md`](./node-language-v1-input-values.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Graph strict vs UI normalize

```text
UI properties may normalize (clamp / default).
Graph-connected inputs stay fail-closed (no silent clamp / coercion).
```

## OptionalPortDrive rule

```text
unconnected → property / default fallback
connected + valid → wire value
connected + null/invalid → fail closed (no silent fallback)
```

## Value List (`input.values.dropdown`)

| Drive | Unconnected | Connected valid | Connected invalid / OOR |
|-------|-------------|-----------------|-------------------------|
| Index | property; **UI clamp** into `[0, size)` | exact in-range `Integer` | `Valid=false` + `Error` (no clamp) |
| Options | CSV property parse | homogeneous `STRING_LIST` | `Valid=false` + `Error` (no CSV fallback) |

- `output_error` (`STRING`) explains invalid paths (null options, empty, bad index, wrong type).
- Non-String list elements invalidate the whole Options drive (no coercion / filtering).
- Blank string entries are trimmed and skipped (CSV editor semantics).

Helper: `OptionalPortDrive.resolveOptionalInteger`, `resolveOptionalStringList`.

## Text Input (`input.values.text`)

- Hard cap: `MAX_TEXT_INPUT_CHARS = 32767` (`MULTI_LINE_BUF_SIZE - 1`).
- `setMaxLength`: `<= 0` → `32767`; `> 32767` → ignore (keep previous); `1..32767` accepted.

## Boolean Toggle (`input.values.boolean`)

- `setNodeState`: accept `Boolean` only (map key `value` or bare `Boolean`).
- String / Number coercion on restore is dropped.

## Gradient Ramp

| Drive | Unconnected | Connected valid | Connected invalid |
|-------|-------------|-----------------|-------------------|
| T / X / Y | `0.5` | exact finite `Double` | `Valid=false` → existing invalidate path |

Connected `Integer` / other `Number` types are **not** accepted (`StrictDoubleUtils.requireExactFiniteDouble`).

Sampling math (modes, wrap, interpolation, equal-stop upper pick, radius `> 0`) unchanged from V34.

## Deferred (P2)

- Gradient / File Path saved-state enum and stop repair → future `StrictStateReader`
- DialogMode / gradientMode invalid-token fallback on restore

## Migration

Graph **V109→V110** is a no-op (runtime semantics only; no wire remaps). Stamp-only migrator.
V109 is a reserved Graph step (Vector signed-angle placeholder) when CURRENT jumped from V108.

## Contract

- `InputValuesLanguageV2ContractTest` — V110 fence, connected-null / OOR Index + Error,
  unconnected clamp, Text hard cap, Boolean Boolean-only restore, Gradient exact-Double.
- `InputValuesLanguageContractTest` — inventory and V34 migration retained.
