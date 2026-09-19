# Node metadata contracts

Thin invariant suite for the registered node catalog. Prefer these checks over
per-node unit tests as the library grows past ~500 nodes.

## Source of truth

- Annotation: `com.nodecraft.nodesystem.api.NodeInfo`
- Runtime registry metadata: `com.nodecraft.gui.node.NodeInfo`
- Build-time catalog: `GeneratedNodeCatalog` via Gradle `generateNodeCatalog`
- Suite: `com.nodecraft.nodesystem.contract.NodeContractTest`
- Catalog suite: `com.nodecraft.nodesystem.catalog.NodeCatalogContractTest`

## Invariants (Phase B′)

| Contract | Rule |
|----------|------|
| Registry ID unique | Every `NodeRegistry` id appears once |
| Annotation ID unique | `@NodeInfo.id` unique across registered classes |
| Annotation ↔ registry | `@NodeInfo.id` (normalized) equals registry id |
| Runtime typeId | `INode.getTypeId()` equals registry id (case-insensitive) |
| Category present | `@NodeInfo.category` and registry category are non-blank |
| Port IDs unique | Within one instantiable node, all input+output port ids are unique and non-blank |
| Catalog ⊆ registry | Every `GeneratedNodeCatalog.IDS` entry is present in `NodeRegistry` after init |

## Environment split

- `@ContractEnvironment(MINECRAFT_CLIENT)` (or `minecraft-client-only-nodes.txt`) marks nodes that need a live
  Minecraft client/registry. Headless `./gradlew test` skips them.
- **Known unsupported = explicit** (annotation or allowlist). **Unexpected failure on UNIT-eligible nodes = test failure.**

## Soft ceilings (phase **0.9-A**)

| Metric | Denominator | Ceiling |
|--------|-------------|---------|
| Missing `@NodeInfo` | UNIT-eligible catalog | 0% |
| Instantiate failures | UNIT-eligible catalog | &lt; 10% |
| Ser/de roundtrip failures | checked instantiable UNIT nodes | &lt; 10% |

Roadmap: 0.8 &lt;25% → 0.9-A &lt;10% → 0.9-B &lt;3% → 1.0 **0 unexpected**. See [`contract-thresholds.md`](./contract-thresholds.md).

- Nested / anonymous / local classes are not catalog nodes; `AutoNodeScanner` skips them so test helpers under
  `com.nodecraft.nodesystem.nodes` do not pollute the registry.

## Running

```bash
./gradlew test --tests "com.nodecraft.nodesystem.contract.*"
```

Related Phase D contracts:

- [`preview-side-effects.md`](./preview-side-effects.md)
- [`node-state-serde.md`](./node-state-serde.md)
