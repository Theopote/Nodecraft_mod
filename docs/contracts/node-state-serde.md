# Node state ser/de contracts

Thin roundtrip fence for node persistence used by `GraphSerializer` /
`NodePropertyBindings`. Prefer catalog-wide invariants over per-node tests.

## Rule

For each instantiable registered node type:

1. `getNodeState()` must not throw.
2. `setNodeState(getNodeState())` on a fresh instance must not throw.
3. When both states are `Map`s, keys present in the original map must survive with
   value-equivalent payloads (numbers compared numerically; enums may coerce via name).

## Environment split

Nodes annotated `@ContractEnvironment(MINECRAFT_CLIENT)` (or listed in
`src/test/resources/nodecraft/contracts/minecraft-client-only-nodes.txt`) are excluded from headless ser/de
fences. Cover them in a dedicated Minecraft client / gametest task.

## Soft ceiling (phase **0.9-A**)

Roundtrip failures on checked instantiable **UNIT-eligible** nodes must stay below **10%**.
Denominator is `checked` (instantiated nodes), not full registry size.
See [`contract-thresholds.md`](./contract-thresholds.md).

## Suites

- `com.nodecraft.nodesystem.contract.NodeStateSerDeContractTest` — catalog fence
- Existing focused tests remain: `NodePropertyBindingsTest`, `GraphSerializerTest`

## Non-goals

- Full JSON graph roundtrip for every node (covered sparsely by `GraphSerializerTest`)
- Guaranteeing identity of non-Map / opaque state objects
- Network graph sync / multiplayer formats

`GraphFormatVersion` freeze: [`graph-format.md`](./graph-format.md) / Phase I.
