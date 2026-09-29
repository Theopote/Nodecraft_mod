# Node Language v2 — Input Values

**Status: PASSED / FROZEN** (Graph **V110**; V34 remains historical v1)

Optional-drive modernization for `input.values.dropdown` (Value List) and
`input.values.gradient_ramp`. Aligns early V34 optional-input semantics with the
global OptionalPortDrive rule used by Planes, Frames, and Patterns.

Related: [`node-language-v1-input-values.md`](./node-language-v1-input-values.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## OptionalPortDrive rule

```text
unconnected → property / default fallback
connected + valid → wire value
connected + null/invalid → fail closed (no silent fallback)
```

## Value List (`input.values.dropdown`)

| Drive | Unconnected | Connected valid | Connected invalid |
|-------|-------------|-----------------|-------------------|
| Index | `selectedIndex` property | exact `Integer` | `Valid=false` |
| Options | CSV property parse | homogeneous `STRING_LIST` | `Valid=false` (no CSV fallback) |

- Index still **clamped** into `[0, size-1]` when options are non-empty (intentional).
- Non-String list elements invalidate the whole Options drive (no coercion / filtering).
- Blank string entries are trimmed and skipped (CSV editor semantics).

Helper: `OptionalPortDrive.resolveOptionalInteger`, `resolveOptionalStringList`.

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

Graph **V109→V110** is a no-op (runtime semantics only; no wire remaps).
V109 is a reserved Graph step (Vector signed-angle placeholder) when CURRENT jumped from V108.

## Contract

- `InputValuesLanguageV2ContractTest` — V110 fence, connected-null Index/Options/T,
  unconnected fallbacks, exact-Double rejection of Integer T.
- `InputValuesLanguageContractTest` — inventory and V34 migration retained.
