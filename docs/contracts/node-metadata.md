# Node metadata contracts

Thin invariant suite for the registered node catalog. Prefer these checks over
per-node unit tests as the library grows past ~500 nodes.

## Source of truth

- Annotation: `com.nodecraft.nodesystem.api.NodeInfo`
- Runtime registry metadata: `com.nodecraft.gui.node.NodeInfo`
- Suite: `com.nodecraft.nodesystem.contract.NodeContractTest`

## Invariants (Phase B′)

| Contract | Rule |
|----------|------|
| Registry ID unique | Every `NodeRegistry` id appears once |
| Annotation ID unique | `@NodeInfo.id` unique across registered classes |
| Annotation ↔ registry | `@NodeInfo.id` (normalized) equals registry id |
| Runtime typeId | `INode.getTypeId()` equals registry id (case-insensitive) |
| Category present | `@NodeInfo.category` and registry category are non-blank |
| Port IDs unique | Within one instantiable node, all input+output port ids are unique and non-blank |

## Soft rules

- Instantiation may fail for a minority of nodes that touch Minecraft registries in `<clinit>` / ctor.
  The suite fails only if failure ratio ≥ 25%.
- Nodes missing `@NodeInfo` (convention registration) are tolerated under a 5% residual ceiling.
- Nested / anonymous / local classes are not catalog nodes; `AutoNodeScanner` skips them so test helpers under
  `com.nodecraft.nodesystem.nodes` do not pollute the registry.

## Running

```bash
./gradlew test --tests "com.nodecraft.nodesystem.contract.NodeContractTest"
```
