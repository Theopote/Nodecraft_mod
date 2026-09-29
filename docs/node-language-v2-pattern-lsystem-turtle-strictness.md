# Node Language v2 — Pattern L-System Turtle Strictness (Graph V106)

**Status: ACTIVE** (Graph **V106**)

Closes Pattern L-System P1-small Turtle input contract and P2 pose stability after V84 freeze:

1. Turtle Commands port required (fail-closed when missing or wrong type)
2. Explicit empty string remains valid zero-draw
3. Quaternion normalization after each rotation

Related: [`node-language-v2-pattern-lsystem.md`](./node-language-v2-pattern-lsystem.md) (V84 foundation; V106 runtime contracts).

## Commands required

`LSystemTurtle3DNode` requires `input_commands` to be a connected `String`:

| Input state | Result |
|-------------|--------|
| Disconnected / missing / non-String | `Valid=false`, `"Commands are required"` |
| Connected `""` | `Valid=true`, zero segments (legal empty L-system output) |
| Connected non-empty string | unchanged turtle interpretation |

This aligns Turtle with Rule/Expand required-input contracts and prevents silent success when `Expand.String` is not wired.

## Quaternion stability

`LSystemTurtle3DInterpreter` normalizes orientation after each local yaw/pitch/roll rotation so long command sequences do not drift forward-vector length due to floating-point accumulation.

## Migration

V105 → V106 is a no-op format bump (runtime semantics only).
