# Node Language v2 — Pattern L-System

**Status: PASSED / FROZEN** (Graph **V84**; V46 remains historical v1)

Language modernization for the three canonical `pattern.lsystem.*` nodes:
Valid+Error, OptionalPortDrive, connection-aware rules, axiom/rewrite budgets,
weighted-sum finite fence, and Turtle hard-fail transactional geometry.

Related: [`node-language-v1-pattern-lsystem.md`](./node-language-v1-pattern-lsystem.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Pipeline

```
Rule → Expand → Turtle 3D → PATH_LIST
```

Grammar rewriting and spatial interpretation stay separate. Commands String may
also feed Turtle directly.

## Inventory (orders 0–2)

| Order | Display name | Type id |
|------:|--------------|---------|
| 0 | L-System Rule | `pattern.lsystem.rule` |
| 1 | L-System Expand | `pattern.lsystem.expand` |
| 2 | L-System Turtle 3D | `pattern.lsystem.turtle_3d` |

## Core rules

1. **Valid + Error** on all three nodes (Expand/Turtle keep Hit Limit).
2. **OptionalPortDrive** — Weight / Iterations / Seed / Step / Angle / Origin: unconnected → property (Origin default world origin); connected exact typed value → wire; connected invalid → fail.
3. **Symbol / Production** — must be `String`; Production may be `""` (deletion); missing/non-String fails.
4. **Dynamic Rule N** — unconnected skip; connected must be valid `L_SYSTEM_RULE`.
5. **Rules list** — unconnected → none; connected → strict typed list or fail; merge with port rules.
6. **Axiom length** — `<= MAX_LSYSTEM_EXPANDED_LENGTH` even when Iterations=0.
7. **Rule count / rewrite budget** — `MAX_LSYSTEM_RULES`, `MAX_LSYSTEM_REWRITE_MATCH_TESTS`.
8. **Weighted choice** — same-symbol positive weight sum must be finite.
9. **Expand length hit** — return previous complete round; `Valid=true`, `Hit Limit=true`.
10. **Turtle budgets** — command / stack / segments hard-fail; effective segments `<= MAX_LIST_ELEMENTS/2`; no partial geometry.
11. **Turtle Valid** — legal interpretation (including zero-draw) succeeds; finite pose/endpoints required.
12. **Unknown turtle symbols** ignored; `F` emits independent PATH segments; degrees rotations unchanged.

## Migration (V83 → V84)

Format bump only. Error ports additive; order is catalog metadata; `ruleInputCount` state preserved.
