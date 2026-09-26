# Node Language v1 — Variable Scope

**Status: PASSED / FROZEN** (Graph **V59**)

Execution-scope user variables under `variable.*` (6 runtime nodes).

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md),
[`contracts/preview-side-effects.md`](./contracts/preview-side-effects.md).

## Product boundary

```text
VariableScopeBridge → ExecutionContext.variables (or graph/thread fallback scope)
__nodecraft.* reserved for runtime internal keys (user nodes cannot write these names)
containsKey semantics: null stored value is valid data, distinct from missing key
connection-aware optional drives (OptionalPortDrive): unconnected → property; connected-null → fail closed
value ports bind passthrough T (no ANY laundering)
Clear Variables always preserves internal keys (no user-facing includeInternalVariables)
```

## Inventory (6)

| order | Display name | Type id | Effect |
|------:|--------------|---------|--------|
| 0 | Set Variable | `variable.set` | `CONTEXT_WRITE` |
| 1 | Get Variable | `variable.get` | `CONTEXT_READ` |
| 2 | Variable List | `variable.list` | `CONTEXT_READ` |
| 3 | Frame Local Variable | `variable.frame_local` | `CONTEXT_WRITE` |
| 4 | Remove Variable | `variable.remove` | `CONTEXT_WRITE` |
| 5 | Clear Variables | `variable.clear` | `CONTEXT_WRITE` |

## Set Variable

- Ports: `Name`, `Value T` in; `Value T`, `Previous T`, `Name`, `Valid`, `Exists Before`, `Error` out
- `output_valid` (V59 rename from `output_success`)

## Get Variable

- Ports: `Name`, `Default T` in; `Value T`, `Exists`, `Name`, `Is Null`, `Valid`, `Error` out
- Missing key → `Default`; existing null → `Exists=true`, `Is Null=true`, `Value=null`

## Variable List

- Outputs: `Names` (`STRING_LIST`), `Values` (`LIST`), `Count`
- No `Entries` port (removed V59)
- Property `Show Internal Variables` for diagnostics only

## Frame Local Variable

Command precedence:

1. Validate frame/name → `Valid=false` on failure
2. `Clear Frame=true` → clear frame map (`Cleared=true`)
3. `Write=true` → put `Value` at `Name`
4. Else → read `Name` or `Default`

Optional `Write` / `Clear Frame` booleans use connection-aware drives (default `false`).

## Remove Variable

- Removes user key; `Previous` is untyped (`ANY` without passthrough `T`)

## Clear Variables

- Optional `Clear` boolean (default `false`); `Valid` + `Error` outputs
- Clears **user variables only**; `__nodecraft.*` always preserved

## Migration V58→V59

- Rename `variable.set` wire `output_success` → `output_valid`
- Drop wires from `variable.list` `output_entries`
- Strip `includeInternalVariables` from `variable.clear` node state

## Contract suite

`VariableLanguageContractTest` — inventory, effects, passthrough T, fail-closed drives,
null/existence semantics, internal-key preservation, migration.
