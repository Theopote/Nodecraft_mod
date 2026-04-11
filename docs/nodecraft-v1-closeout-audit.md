# NodeCraft v1 Closeout Audit

Last updated: 2026-04-11

## 1. Result

The v1 node-system mainline taxonomy is now structurally closed for the scoped domains:

- `input`
- `reference`
- `geometry`
- `transform`
- `pattern`
- `material.basic_assignment`
- `world.read`
- `world.query`
- `world.selection`
- `world.write`
- `output`
- `math.scalar_math`
- `math.compare`
- `math.random`
- `math.trigonometry`
- `math.list_sequence`

The old `math.basic`, `math.randomness`, and `math.vector` domains have been removed from active implementation paths.

## 2. Closed Migrations

### 2.1 Math

The following are now canonical:

- `math.scalar_math.*`
- `math.compare.*`
- `math.random.*`
- `math.trigonometry.*`
- `math.list_sequence.*`

Legacy IDs are preserved through aliases in `NodeRegistry`.

### 2.2 Deferred math

`MathSeriesNode` is not part of the v1 main tree.

- Old ID: `math.basic.series`
- New deferred ID: `deferred.math.math_series`
- Reason: semantics overlap with range/sequence generation but are not committed in the v1 taxonomy

## 3. Explicitly Out Of v1 Scope

These domains still exist in the repository, but they are not v1 main-tree work:

- `animation.*`
- `flora.*`
- `world.inventory.*`
- `world.nbt.*`
- `inputs.basic.text_input`
- `inputs.basic.color_picker`
- `inputs.selectors.*` except `block_type_selector`
- `inputs.minecraft.selected_entity`
- `inputs.minecraft.selected_block_sequence`
- `inputs.sources.*`
- `spatial.legacy`
- `deferred.*`

These should not be used as evidence that the v1 mainline migration is incomplete.

## 4. Remaining Non-Mainline Domains

The scan still shows several older or separate systems that need later policy decisions, but not as part of the v1 scoped migration:

- `data.*`
- `utilities.*`
- `control.flow.*`
- `world.entity.*`
- `world.interaction.*`
- `spatial.sdf.*`

These are outside the currently committed v1 architecture boundary.

## 5. Current Migration Boundary

Going forward, new work should follow this rule:

1. If a node belongs to the committed v1 tree, add it directly under its canonical v1 category.
2. If a node is intentionally postponed, place it under `deferred.*`.
3. If a node exists only for backward compatibility with pre-v1 graphs, route it through `spatial.legacy`.

## 6. Practical Conclusion

For the original v1 refactor objective, the node taxonomy migration is functionally complete for the scoped domains.

The next engineering phase should not continue ad hoc taxonomy renames. It should focus on one of:

- full compile and runtime verification
- serialization / graph load compatibility validation
- removal or quarantine of obsolete helper code and package-level leftovers
- a separate architecture pass for out-of-scope systems such as `data`, `utilities`, `control`, `entity`, and `sdf`
