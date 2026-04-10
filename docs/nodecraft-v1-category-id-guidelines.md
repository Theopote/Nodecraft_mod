# NodeCraft v1.0 Category ID Guidelines

This document defines the canonical `category id` scheme for the NodeCraft v1.0 node system.

It is the source of truth for:

- category registration
- node documentation placement
- node search grouping
- migration planning
- future node additions

This document applies to the v1.0 main node system only.

Excluded domains such as `animation`, `flora`, `nbt`, `inventory`, and experimental nodes are intentionally outside this taxonomy.

## 1. Design Goals

The category id scheme must satisfy the following constraints:

- Stable enough to survive large-scale node refactors
- Semantic rather than package-structure-driven
- Readable to developers and maintainers
- Predictable for new node additions
- Strict enough to prevent category drift
- Flexible enough to absorb future v1.x nodes without renaming top-level domains again

## 2. Canonical Format

Canonical category ids use:

- lowercase letters
- snake_case within each segment
- dot-separated segments

Format:

```text
<top_level>.<sub_category>
```

Examples:

- `input.numeric`
- `reference.planes`
- `geometry.primitives`
- `transform.deformations`
- `pattern.along_curve`
- `material.block_state`
- `world.selection`
- `output.preview`
- `math.scalar_math`

## 3. Naming Rules

### 3.1 Top-Level Rules

Top-level category ids must be:

- singular nouns when referring to a domain
- short and stable
- semantically broad enough to host future subcategories

Canonical top-level ids for v1.0:

- `input`
- `reference`
- `geometry`
- `transform`
- `pattern`
- `material`
- `world`
- `output`
- `math`

### 3.2 Subcategory Rules

Subcategory ids must be:

- plural when they represent a family of object types
- singular only when the semantic group is naturally singular
- descriptive enough to survive growth
- implementation-agnostic

Examples:

- use `points`, not `point_ops`
- use `vectors`, not `vector_math`
- use `solids`, not `solid_generation`
- use `block_state`, not `blockstate`
- use `list_sequence`, not `data_lists`

### 3.3 Forbidden Patterns

Do not use:

- camelCase
- kebab-case
- package-history names as canonical ids
- implementation names such as `generator`, `voxelizer`, `scanner`, `custom_ui`
- temporary migration names such as `legacy_new`, `v2`, `new_geometry`

Bad examples:

- `spatial.construct`
- `visualization.preview`
- `math.vector`
- `world.modification`
- `geometry.voxelizer`
- `input.basicValues`

## 4. Canonical v1.0 Category IDs

## 4.1 Input

- `input.numeric`
- `input.context`
- `input.type_selectors`

## 4.2 Reference

- `reference.points`
- `reference.vectors`
- `reference.planes`
- `reference.frames`

## 4.3 Geometry

- `geometry.primitives`
- `geometry.curves`
- `geometry.profiles`
- `geometry.solids`
- `geometry.boolean`
- `geometry.architectural_primitives`

## 4.4 Transform

- `transform.basic_transforms`
- `transform.orientation`
- `transform.deformations`

## 4.5 Pattern

- `pattern.linear`
- `pattern.grid`
- `pattern.radial`
- `pattern.along_curve`
- `pattern.surface_volume_distribution`

## 4.6 Material

- `material.basic_assignment`
- `material.gradient_mapping`
- `material.directional_mapping`
- `material.pattern_mapping`
- `material.block_state`
- `material.surface_aging`

## 4.7 World

- `world.read`
- `world.query`
- `world.write`
- `world.selection`

## 4.8 Output

- `output.preview`
- `output.execute`
- `output.export`
- `output.debug`

## 4.9 Math

- `math.scalar_math`
- `math.compare`
- `math.trigonometry`
- `math.logic`
- `math.random`
- `math.list_sequence`

## 5. Semantic Boundary Rules

### 5.1 `input.*`

Use `input.*` only for:

- direct parameter entry
- player-context entry points
- type selectors

Do not place:

- list-construction nodes
- world queries
- formal world-selection nodes

### 5.2 `reference.*`

Use `reference.*` for nodes whose primary output is a spatial reference object.

Examples:

- point
- vector
- plane
- frame

If a node modifies an existing object but still semantically produces a reference object, it remains in `reference.*`.

Examples:

- `Offset Plane` -> `reference.planes`
- `Rotate Plane` -> `reference.planes`

### 5.3 `geometry.*`

Use `geometry.*` for nodes that create or combine geometry.

Examples:

- primitive creation
- curve generation
- profile generation
- extrude / loft / revolve / sweep
- boolean operations

### 5.4 `transform.*`

Use `transform.*` for nodes whose primary semantic action is transforming an already-existing object.

Examples:

- move
- rotate
- scale
- mirror
- bend
- twist

### 5.5 `pattern.*`

Use `pattern.*` when the node distributes, repeats, tiles, arrays, or populates objects.

### 5.6 `material.*`

Use `material.*` when the node determines:

- block choice
- state rules
- directional assignment
- gradients
- surface aging

Do not put geometry generation here.

### 5.7 `world.*`

Use `world.*` only for direct interaction with the Minecraft world.

Boundary:

- `world.read` for factual data retrieval
- `world.query` for environment or condition checking
- `world.selection` for selected block / region ownership
- `world.write` for low-level direct write operations

### 5.8 `output.*`

Use `output.*` for:

- preview
- execution
- export
- debug

`output.execute` is the canonical home for build-commit style nodes that finalize modeling results.

### 5.9 `math.*`

Use `math.*` for reusable general-purpose logic and numeric operations.

This is the shared utility domain of the v1.0 main system.

## 6. Future Expansion Rules

The following rules exist to keep the taxonomy stable as new nodes are added later.

### 6.1 Add nodes before adding categories

When a new node can fit an existing canonical category, do not create a new subcategory.

Prefer:

- adding `Bezier Surface` to an existing geometry family if semantically appropriate

Avoid:

- creating `geometry.advanced_surfaces` too early

### 6.2 Add subcategories before adding top-level domains

If v1.x grows, first try to extend existing top-level domains.

Examples:

- new reference systems should first try `reference.*`
- new distribution systems should first try `pattern.*`
- new block-state rules should stay in `material.block_state`

### 6.3 New top-level domains require a separate design pass

Adding a new top-level domain is a taxonomy change, not a normal node addition.

That means domains such as the following must not silently re-enter v1.0:

- `animation`
- `flora`
- `data`
- `utilities`
- `control`
- `experimental`

If one of these returns, it should be designed explicitly as an extension taxonomy, not mixed informally into the main tree.

### 6.4 Do not encode workflow shortcuts into category ids

If a node is easy to discover from multiple places, solve that in:

- search keywords
- favorites
- quick-add menus
- aliases

Do not solve it by giving the node multiple canonical categories.

## 7. Node ID Relationship

Canonical node ids should align with canonical category ids.

Preferred format:

```text
<category_id>.<node_leaf_id>
```

Examples:

- `reference.points.mid_point`
- `geometry.primitives.box`
- `geometry.solids.extrude`
- `pattern.grid.grid_array`
- `world.selection.selected_region`

If a node changes canonical category, its canonical node id should change as well, with alias compatibility preserved separately.

## 8. Reserved and Deferred Domains

The following historical domains are not canonical v1.0 category ids:

- `animation.*`
- `flora.*`
- `data.*`
- `utilities.*`
- `control.*`
- `spatial.*`
- `visualization.*`

These may still appear in old assets, source packages, or compatibility aliases during migration.

They must not be used as the target category ids for new v1.0 work.

## 9. Decision Checklist

When adding or migrating a node, validate all of the following:

1. Does the node have exactly one canonical category?
2. Does the chosen category describe semantic responsibility rather than implementation?
3. Could the node fit an existing category without creating taxonomy drift?
4. Would this category still make sense if similar nodes are added later?
5. If this node moved categories, is alias compatibility handled separately from taxonomy?

If any answer is unclear, stop and resolve taxonomy first.
