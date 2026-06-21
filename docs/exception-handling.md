# Exception Handling

NodeCraft uses unchecked exceptions. Prefer the hierarchy below at module boundaries; keep plain JDK exceptions inside small value objects when that keeps call sites simple.

## Hierarchy

```
NodeCraftException
├── NodeValidationException      invalid inputs, unknown type IDs, parse errors
│   ├── GeometryException        solid/geometry operation errors
│   └── ExpressionEvaluationException
├── NodeExecutionException       graph run failures, assert fail-hard, node instantiation
└── GraphException               subgraph extraction and graph-level invariants
```

All types live in `com.nodecraft.core.exception`.

## When to use which type

| Situation | Exception |
|-----------|-----------|
| Unknown node type, bad registry lookup | `NodeValidationException` |
| Node class cannot be constructed at runtime | `NodeExecutionException` |
| Expression parse/eval failure | `ExpressionEvaluationException` |
| Assert node with fail-hard enabled | `NodeExecutionException` |
| Server-thread execution wrapper failure | `NodeExecutionException` |
| Subgraph extraction preconditions | `GraphException` |
| Solid helper switch/default on bad axis or face | `GeometryException` |
| Geometry **value object** constructor (`SphereData`, `PolylineData`, …) | `IllegalArgumentException` (JDK idiom) |
| GUI layout / renderer registry null checks | `IllegalArgumentException` |
| Mod bootstrap catastrophic failure | `RuntimeException` or `NodeCraftException` |

## Catching

- Editor load/clipboard code that probes `NodeRegistry.createNodeInstance` should catch `NodeValidationException`.
- Nodes that build geometry from user inputs may keep `catch (IllegalArgumentException)` around datatype constructors.
- Prefer catching the most specific NodeCraft type you can handle; use `NodeCraftException` only at top-level handlers.

## What we are not doing (dev stage)

- No mass replacement of every `IllegalArgumentException` in datatype classes.
- No checked-exception policy; node graphs stay fail-fast with runtime exceptions.
