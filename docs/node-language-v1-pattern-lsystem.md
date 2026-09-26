# Node Language v1 — Pattern L-System

**Status: PASSED / FROZEN** (Graph **V46**)

Language unification for the three canonical `pattern.lsystem.*` nodes: typed rule
authoring, deterministic string rewriting, and turtle interpretation as independent draw
segments — aligned with Pattern v41-v45 strict Integer / fail-closed validation.

Related: [`node-language-v1-pattern-voronoi-3d.md`](./node-language-v1-pattern-voronoi-3d.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Pipeline

```
Rule authoring (L-System Rule)
    -> String rewriting (L-System Expand)
    -> Spatial interpretation (L-System Turtle 3D)
    -> PATH_LIST segments for downstream geometry
```

## Inventory (3)

| Display name | Type id | Role |
|--------------|---------|------|
| L-System Rule | `pattern.lsystem.rule` | Construct one production rule |
| L-System Expand | `pattern.lsystem.expand` | Rewrite axiom for N iterations |
| L-System Turtle 3D | `pattern.lsystem.turtle_3d` | Interpret commands as draw segments |

## Typed rule language

- Domain types: `L_SYSTEM_RULE`, `L_SYSTEM_RULE_LIST` (already in NodeDataType)
- Generic `LIST` cannot wire to `L_SYSTEM_RULE_LIST` (typed-list asymmetry)
- Expand merges dynamic `input_rule_*` ports (`L_SYSTEM_RULE`) with optional `Rules` list port

### L-System Rule

Inputs: Symbol `STRING`, Production `STRING`, Weight `DOUBLE`

Outputs: Rule `L_SYSTEM_RULE`, Valid `BOOLEAN`

Rules:
- Symbol non-empty
- Production may be empty (deletion rule)
- Weight finite and >= 0

### Weight semantics

Weights are **relative** among competing rules for the same symbol.
Candidates with `weight <= 0` are excluded from random selection.
If all matching candidates have `weight <= 0`, the original symbol is kept unchanged.

## L-System Expand

Inputs: Axiom `STRING`, Rules `L_SYSTEM_RULE_LIST`, dynamic Rule ports,
Iterations `INTEGER`, Seed `INTEGER`

Outputs: String `STRING`, Iterations Applied `INTEGER`, Hit Limit `BOOLEAN`, Valid `BOOLEAN`

Rules:
- Axiom non-empty
- Iterations strict Integer; non-Integer uses property fallback
- Iterations < 0 or > 16 -> invalid (no silent clamp)
- **Iterations = 0 -> exact axiom passthrough** (rules not required)
- Iterations >= 1 requires at least one valid rule
- Rules list port is strict: non-`LSystemRule` entries -> invalid
- Seed deterministic via `@NodeProperty` default 12345
- Length cap: transactional per-round stop (returns previous complete string, Hit Limit=true)

Legacy id `pattern.lsystem.expand_string` and bare `LIST` rules port removed at V46.

## L-System Turtle 3D

Inputs: Commands `STRING`, Step `DOUBLE`, Angle `DOUBLE` (degrees), Origin `POINT`

Outputs: Paths `PATH_LIST`, Points `POINT_LIST`, Segment Count `INTEGER`,
Hit Limit `BOOLEAN`, Valid `BOOLEAN`

Command semantics:
- `F` draw one line segment (before -> after)
- `f` pen-up move (no segment)
- `+/-` yaw, `&/^` pitch, `/ \` roll
- `[` push state, `]` pop state (no segment on pop)

Topology rules:
- Each `F` emits one independent `PathData` line — no fake cross-branch edges
- `f` and `]` never connect disconnected runs
- Points output lists draw endpoints; **not** one continuous polyline

Validation:
- Step finite and > 0
- Angle finite (any value OK)
- Origin via `SpatialValueResolver.resolvePoint()`; default (0,0,0)
- Unmatched `]` or unclosed `[` at end -> invalid
- Command length, segment count, stack depth caps in GenerationLimits

## Limits

| Constant | Value |
|----------|-------|
| MAX_LSYSTEM_ITERATIONS | 16 |
| MAX_LSYSTEM_EXPANDED_LENGTH | 1,000,000 |
| MAX_LSYSTEM_COMMAND_LENGTH | 1,000,000 |
| MAX_LSYSTEM_TURTLE_SEGMENTS | 1,000,000 |
| MAX_LSYSTEM_TURTLE_STACK_DEPTH | 4096 |

## Deferred (P2)

- Context-sensitive L-system
- Parametric / environment-sensitive grammar
- Material / block / leaf commands
- Segment merging / polyline tree optimization
