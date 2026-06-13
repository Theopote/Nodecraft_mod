# NodeCraft Data Tree System Development

## Purpose

NodeCraft uses nodes to model Minecraft worlds. A plain list is not enough for many modeling workflows because it loses grouping information. Data Tree Lite keeps that grouping:

- Building -> floor -> room -> wall -> block
- Array result -> copy index -> generated geometry
- Sweep or loft -> section index -> section points
- Curve voxelize -> curve index -> generated blocks
- Surface divide -> row or column -> sampled points

The goal is not to copy the full Grasshopper Data Tree feature set immediately. The goal is to provide a small, predictable hierarchical list model that can grow into the modeling nodes.

## Data Model

Runtime type:

```text
com.nodecraft.nodesystem.datatypes.DataTreeData
```

Port type:

```text
NodeDataType.DATA_TREE
```

A tree contains ordered branches. Each branch has:

```text
path:  List<Integer>
items: List<Object>
```

Path examples:

```text
{}
{0}
{0;1}
{2;4;3}
```

Paths are integer addresses. UI and node inputs should accept common path forms where practical:

```text
0
{0}
{0;1}
0;1
0/1
0.1
```

## Current Core Nodes

Category:

```text
math.data_tree
```

Implemented nodes:

| Node | Purpose |
|---|---|
| Graft List | Converts each list item into one branch. |
| Flatten Tree | Converts all branches into one plain list. |
| Partition List To Tree | Splits a list into fixed-size branches. |
| Tree Branch | Gets one branch by path. |
| Tree Item | Gets one item by path and index. |
| Tree Statistics | Reports branch count, item count, max depth, paths, and branch sizes. |
| Tree Viewer | Outputs a readable tree summary. |
| Merge Trees | Merges two trees or lists. |
| Simplify Tree | Removes the common leading path prefix. |
| Shift Path | Removes leading path levels or prepends zero levels. |
| Tree Paths | Outputs branch paths as strings and index lists. |
| Cull Empty Branches | Removes branches with no items. |
| Entwine | Combines up to four sources into source-indexed branches. |

## Design Rules

### Keep List Compatibility Explicit

Do not make every list port implicitly accept trees. Tree-to-list conversion should stay explicit through `Flatten Tree`, `Tree Branch`, or modeling-node-specific tree handling. This prevents accidental loss of branch structure.

### Preserve Branch Semantics

When a modeling node receives a tree, it should preserve source branch paths where possible. If the node expands one branch into many branches, append a new path level instead of flattening.

Examples:

```text
Input branch {3} with 5 curves
Curve Voxelize output:
{3;0}, {3;1}, {3;2}, {3;3}, {3;4}
```

```text
Input branch {1} with one profile and one path
Sweep output sections:
{1;0}, {1;1}, {1;2}, ...
```

### Avoid Hidden Flattening

Flattening should only happen when:

- the user uses `Flatten Tree`
- a node is documented to flatten
- a preview or bake node has an explicit flatten/group mode

### Output Diagnostics

Tree-producing nodes should expose basic diagnostics when useful:

```text
Branch Count
Item Count
Valid
```

Debugging tools should expose:

```text
Path Strings
Branch Sizes
Summary
```

## Modeling Node Integration Plan

### Phase 1: Core Tree Tools

Status: started.

Required additions after the current node set:

- Replace Branch
- Insert Branch
- Path Mapper Lite
- Tree To Nested List
- Nested List To Tree
- Explode Tree

### Phase 2: Array Nodes

Status: started.

Array nodes should support tree output first because array workflows naturally create groups.

Targets:

- Linear Array: started, coordinate and geometry variants expose one branch per emitted copy.
- Polar Array: started, coordinate and geometry variants expose one branch per emitted copy.
- Rectangle Array: started through Grid Array; 2D outputs use `{x;y}` paths.
- Box Array: started through Grid Array; 3D outputs use `{x;y;z}` paths.
- Curve Array: started for geometry arrays; output paths use copy order `{i}`.

Recommended output rule:

```text
Each copy is one branch.
Nested arrays append dimensions as path levels.
```

Examples:

```text
Linear Array: {0}, {1}, {2}
Rectangle Array: {row;column}
Box Array: {x;y;z}
```

Current implementation notes:

- Existing flat outputs are preserved.
- New tree outputs are parallel outputs, not replacements.
- Coordinate array branches contain the coordinates generated for that copy.
- Geometry array branches contain one geometry copy per branch.
- Grid-style arrays use coordinate-like path order `{x;y}` or `{x;y;z}`.

### Phase 3: Curve and Surface Nodes

Status: started.

Targets:

- Curve Voxelize: started; blocks are grouped under `{0}`, path segment geometry uses `{segmentIndex}`.
- Tween Curve: started; generated curves, polylines, and point rows use `{tweenIndex}`.
- Join/Smooth Curve workflows
- Surface Divide
- Surface Sample
- Closest Point To Object
- Surface Strip Deconstruct: started; section paths and section points use `{sectionIndex}`, rail segments use source section index.

Recommended output rule:

```text
One source object produces one branch.
Sub-results append new path levels.
```

Current implementation notes:

- `Voxelize Curve` keeps flat `Blocks` and adds `Blocks Tree`.
- `Voxelize Curve` also exposes `Segment Geometry Tree` so downstream workflows can inspect or group generated cylinder segments.
- `Tween Curves` keeps flat curve/polyline/point-row lists and adds tree outputs keyed by tween index.
- `Deconstruct Surface Strip` keeps flat outputs and adds section/rail tree outputs keyed by section index.

### Phase 4: Solid and Voxel Bake

Status: started.

Targets:

- Sweep Profile Along Path: started; section profiles, section paths, and section points use `{sectionIndex}`.
- Sweep Point List Along Path: started; section paths, section points, and rail segment rows use `{sectionIndex}`.
- Sweep 2 Rails: started; section paths, section points, and rail segment rows use `{sectionIndex}`.
- Loft Profiles: started; source/target section points use `{0}` and `{1}`, rails use `{railIndex}`.
- Loft Point Lists: started; source/target paths and point rows use `{0}` and `{1}`, rails use `{railIndex}`.
- Multi Section Loft: started; profiles, section paths, and section points use `{sectionIndex}`, rails use `{vertexIndex}`.
- Section Cut: started; multi-plane cuts output slice blocks and slice points using `{planeIndex}` paths, with traced contour profiles and boundaries using `{planeIndex;contourIndex}` paths.
- Surface Strip To Geometry
- Bake / Preview nodes

Recommended output rule:

```text
Sections, profiles, strips, generated blocks, and material groups should remain branch-addressable until the user chooses to flatten.
```

Current implementation notes:

- Existing flat outputs are preserved.
- Sweep nodes expose section trees so downstream operations can work per profile/section.
- Loft nodes expose section point trees and rail trees so users can operate on cross-section rows or connecting rails independently.
- `SurfaceStripData` remains the compact reusable surface representation; tree outputs are parallel diagnostic/modeling views.
- `Surface Strip To Geometry` accepts `Surface Strip Tree` and outputs `Geometry Tree`, preserving source branch paths.
- `Bake Geometry To Blocks` accepts `Geometry Tree` and outputs `Blocks Tree`, preserving source branch paths.
- `Bake Surface Strip To Blocks` accepts `Surface Strip Tree` and outputs `Blocks Tree`, preserving source branch paths.
- Tree-aware bake nodes compute `Region` from all valid branch items, not only the first input object.
- `Section Cut` accepts a `Planes` list and adds list/tree outputs for traced section profiles, boundary contours, slice blocks, and projected slice points.
- `Section Cut` traces exposed slice-cell edges instead of using a convex hull, so concave outlines and multiple contours remain available as separate boundaries.

Bake nodes should eventually support:

- one group per branch
- block palette by branch
- branch path as generated object name or tag
- preview color by branch

## UI Plan

Short term:

- `Tree Viewer` string summary.
- `Tree Statistics` for numeric debugging.
- Path inputs accept strings such as `{0;1}`.

Medium term:

- Custom Tree Viewer UI with collapsible branches.
- Port tooltip summary: `Data Tree: 12 branches / 240 items`.
- Path input widget in the property panel.

Long term:

- Branch preview colors.
- Bake grouping by branch.
- Tree-aware wire preview in the editor.

## Implementation Checklist

- Add new tree nodes under `src/main/java/com/nodecraft/nodesystem/nodes/math/data_tree`.
- Use `NodeDataType.DATA_TREE` for tree ports.
- Keep helper logic in `DataTreeNodeUtils`.
- Use `DataTreeData.parsePath` and `DataTreeData.formatPath` for path IO.
- Update node library docs after adding nodes:

```text
python scripts/generate_node_library_docs.py
```

- Compile after Java changes:

```text
.\gradlew --no-daemon --console plain compileJava
```

## Open Decisions

- Whether to allow duplicate paths and merge their items, or reject duplicates.
- Whether branch paths should support negative indices. Current design does not.
- Whether tree-aware modeling nodes should add parallel tree outputs or replace existing list outputs.
- How much of Grasshopper Path Mapper syntax should be exposed to users.
- Whether custom Data Tree UI should live in the generic node custom UI system or as a special viewer component.
