# Node Language v2 — Input Numeric

**Status: PASSED / FROZEN** (Graph **V111**; V31 remains historical v1)

Domain Input finite directed-span contract and `NumericRangeData.canonical`.
Sliders, Angle, XY, Pi, and E are unchanged from V31.

Related: [`node-language-v1-input-numeric.md`](./node-language-v1-input-numeric.md),
[`node-language-v1-numeric-domain.md`](./node-language-v1-numeric-domain.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Domain Input (`input.numeric.range`)

Valid only when:

```text
Start finite ∧ End finite ∧ (End − Start) finite
```

| Output | Valid=true | Valid=false |
|--------|------------|-------------|
| Domain | `NumericRangeData` | `null` |
| Start / End / Span | finite doubles | `NaN` |
| Error | `""` | non-blank |

- Directed: does **not** sort Start/End. `10→0` is Valid with `Span=-10`.
- Overflow example: `Start=MAX`, `End=-MAX` → Valid=false (matches Number Slider rejecting non-finite usable span).

## `NumericRangeData.canonical`

```text
canonical(start, end) → domain | null
```

Requires finite endpoints and finite directed span. Producers and
`NumericDomainResolver` re-canonicalize inbound domains (fail closed).

`delta()` / `length()` / `lerp()` return `NaN` when directed span overflows
(defense for non-canonical instances).

## Slider vs Domain (unchanged)

| Concept | Semantics |
|---------|-----------|
| Slider Min/Max | Unordered UI bounds; may auto-swap; require finite usable span |
| Domain Start→End | Directed; never auto-sorted; require finite directed span |

## Deferred (P2)

- Integer saved-state `Number.intValue()` truncation → `StrictStateReader`
- XY Slider negative Step UI sanitize

## Migration

Graph **V110→V111** is a no-op (runtime semantics; no wire remaps). New Valid/Error
ports on Domain Input need no migration.

## Contract

- `InputNumericLanguageV2ContractTest` — V111 fence, overflow fail-closed, reversed Valid,
  `canonical` rejection.
- `InputNumericLanguageContractTest` / `NumericDomainLanguageContractTest` — V31 retained.
