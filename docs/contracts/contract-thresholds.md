# Contract test thresholds

Catalog contract tests (`NodeContractTest`, `NodeStateSerDeContractTest`, …) enforce invariants over
500+ nodes. Soft ratio ceilings tighten by release phase; hard invariants (unique ids, port ids, …) never relax.

## Principle

| Outcome | Meaning |
|---------|---------|
| Known unsupported | Explicit — `@ContractEnvironment(MINECRAFT_CLIENT)` or `minecraft-client-only-nodes.txt` |
| Unexpected failure | UNIT-eligible node fails instantiate / ser/de / metadata invariant → counts toward ratio (or hard-fails at 1.0) |

## Ratio roadmap

| Phase | Instantiate / ser-de ceiling | Missing `@NodeInfo` |
|-------|------------------------------|---------------------|
| 0.8 | &lt; 25% | &lt; 5% |
| **0.9-A** (current) | **&lt; 10%** | **0%** |
| 0.9-B | &lt; 3% | 0% |
| 1.0 | **0 unexpected** | 0% |

Constants: `com.nodecraft.nodesystem.contract.ContractThresholds` (`PHASE = "0.9-A"`).

## Denominators

| Metric | Denominator |
|--------|-------------|
| Missing `@NodeInfo` | UNIT-eligible registry entries |
| Instantiate failures | UNIT-eligible registry entries |
| Ser/de roundtrip failures | UNIT-eligible nodes actually instantiated and checked |

Do **not** divide by full `NodeRegistry.getNodeCount()` when many nodes are skipped or fail before check — that
under-reports failure rate.

## Minecraft client coverage

Headless `./gradlew test` = `ContractTestEnvironment.UNIT`.

Future: `./gradlew gametest` (or client run config) runs the same contracts with environment
`MINECRAFT_CLIENT` for allowlisted / annotated nodes only.

## Baseline audit

```bash
./gradlew test --tests "com.nodecraft.nodesystem.contract.ContractCatalogAuditTest" --info
```

Prints current failure counts without failing the build.
