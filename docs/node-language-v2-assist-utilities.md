# Node Language v2 — Assist Utilities

**Status: PASSED / FROZEN** (Graph **V89**; V55 remains historical v1)

Graph readability / flow helpers under `utilities.assist` (5 nodes). The frozen rule:

> **Assist nodes must not destroy the type system.**

Related: [`node-language-v1-assist-utilities.md`](./node-language-v1-assist-utilities.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Passthrough `T`

Relay / Fork / Validate Value / Coalesce declare `ANY` ports with `bindPassthroughType("T")`.

```text
BLOCK_POS → Relay → effective BLOCK_POS  (cannot connect to POINT)
unbound ANY → typed input = UNSUPPORTED
typed → ANY = widening sink OK
```

## Per-node output policy

| Node | Outputs | Valid+Error |
|------|---------|:-----------:|
| Relay | Output T | no |
| Signal Fork | Outputs T… | no |
| Validate | Value, Message, Valid, Error | yes |
| Coalesce | Signal, Source, Valid, Error | yes |
| String Format | Text, counts, Message, Valid, Error | yes |

`null` remains legal passthrough data on Relay/Fork/Coalesce branches.

## String Format workload caps

Shared limits via `GenerationLimits` + `StringFormatEngine`:

| Cap | Value |
|-----|------:|
| `MAX_FORMAT_DEPTH` | 32 |
| `MAX_FORMAT_TEMPLATE_CHARS` | 65_536 |
| `MAX_FORMAT_OUTPUT_CHARS` | 65_536 |
| Values list size | `MAX_LIST_ELEMENTS` |

Single-pass placeholder scan; identity-based cycle detection on List/Map; no partial output on failure.

**Message vs Error:**
- missing placeholder → `Valid=false`, semantic `Message`, `Error=""`
- graph/runtime failure → `Valid=false`, `Error="..."`, `Message=""`

**Sink exception:** LIST and ANY on Value 0–2 are input-only formatting sinks (not type laundering).

Connected empty `Values` LIST is valid. Value 0–2 driven by connection state (`OptionalPortDrive.isConnected`).

## Dynamic port identity

Signal Fork / Coalesce branch count changes add/remove **only the last branch port**. Surviving port objects (`output_a`, `input_primary`, `input_prefer_primary`, …) keep runtime connection identity.

## Validate / Coalesce semantics

**Validate:** Condition false → business `Message`, `Error=""`. Connected-invalid Condition/Message → `Error`.

**Coalesce:** connected branch `null` = skip; all connected null → `Valid=true`, `Source=none`. Prefer Primary connected-invalid → `Error`.

## Migration (V88 → V89)

Format bump only. Error ports additive; stricter runtime limits; no wire remap. Legacy V54→V55 assist remaps preserved.
