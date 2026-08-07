# Node state ser/de contracts

Thin roundtrip fence for node persistence used by `GraphSerializer` /
`NodePropertyBindings`. Prefer catalog-wide invariants over per-node tests.

## Rule

For each instantiable registered node type:

1. `getNodeState()` must not throw.
2. `setNodeState(getNodeState())` on a fresh instance must not throw.
3. When both states are `Map`s, keys present in the original map must survive with
   value-equivalent payloads (numbers compared numerically; enums may coerce via name).

## Soft ceiling

Instantiation or roundtrip may fail for nodes that touch Minecraft registries.
The suite fails only if the combined failure ratio ≥ 25% of the registry size.

## Suites

- `com.nodecraft.nodesystem.contract.NodeStateSerDeContractTest` — catalog fence
- Existing focused tests remain: `NodePropertyBindingsTest`, `GraphSerializerTest`

## Non-goals

- Full JSON graph roundtrip for every node (covered sparsely by `GraphSerializerTest`)
- Guaranteeing identity of non-Map / opaque state objects
- Network graph sync / multiplayer formats

`GraphFormatVersion` freeze: [`graph-format.md`](./graph-format.md) / Phase I.
