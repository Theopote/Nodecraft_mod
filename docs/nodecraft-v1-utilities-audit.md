# NodeCraft v1 Utilities Audit

This audit classifies the temporary `nodes/utilities` bucket against the NodeCraft v1 mainline taxonomy.

## Scope

This document uses the v1 source of truth:

- `input`
- `reference`
- `geometry`
- `transform`
- `pattern`
- `material`
- `world`
- `output`
- `math`

Anything outside that scope should be either:

- kept as editor/tooling support under `utilities.*`
- frozen as `legacy`
- moved to `deferred.*`
- deleted if it is both unused and redundant

## Current Utilities Inventory

- `advanced`: 6 files
- `assist`: 4 files
- `data_conversion`: 7 files
- `fileio`: 9 files
- `flow_control`: 8 files
- `legacy`: 34 files
- `organization`: 3 files
- `selectors`: 4 files
- `text_processing`: 6 files

## Category Decisions

### Keep As Utilities

These are not part of the v1 building/modeling taxonomy, but they are valid editor or workflow helpers.

#### `utilities.assist`

- `RerouteNode`
- `SignalForkNode`
- `SignalMergeNode`
- `TagRelayNode`

Decision:

- keep
- treat as editor/workflow support
- do not try to force them into `math`, `output`, or `world`

#### `utilities.organization`

- `CommentNode`
- `GroupNode`
- `AlignNodesNode`

Decision:

- keep
- treat as canvas/editor organization helpers
- hide from the main modeling path by default

#### `utilities.fileio`

- `FilePathNode`
- `LoadGraphNode`
- `SaveGraphNode`
- `ReadDataFileNode`
- `WriteDataFileNode`
- `ReadTextFileNode`
- `WriteTextFileNode`
- `TextPanelNode`
- `SafeFilePathResolver` helper

Decision:

- keep for now
- classify as tooling/workflow support, not v1 mainline modeling
- `LoadGraphNode` and `SaveGraphNode` are graph-editor workflow, not `output.export`
- text/data file nodes are external IO helpers, not part of v1 world-building scope

### Migrate Back To Mainline

These are currently in `utilities`, but conceptually belong to the v1 main tree.

#### `utilities.flow_control`

- `BranchNode` -> `math.logic.if`
- `SwitchNode` -> `math.logic.switch`

Decision:

- migrate
- these are canonical logic nodes and should not stay under `utilities`

#### `utilities.flow_control.CompareNode`

Decision:

- do not keep as a long-term canonical node under `utilities`
- either:
  - migrate to `math.compare.compare`, if a composite compare node is still wanted
  - or delete after confirming the existing compare-family nodes cover the need

Current recommendation:

- migrate or alias into `math.compare`
- do not leave it under `utilities.flow_control`

### Keep But Move To Deferred/Workflow

These nodes are useful, but they are not part of the current v1 modeling tree.

#### `utilities.flow_control`

- `ForEachNode`
- `GeometryGateNode`
- `GeometryMergeNode`
- `GeometryPassthroughNode`
- `GeometrySwitchNode`

Decision:

- do not force into the v1 mainline right now
- move to a non-mainline bucket such as `deferred.workflow` or keep under `utilities.workflow`

Reason:

- they are orchestration/workflow nodes, not core geometry/reference/material/world nodes

### Freeze As Out Of Scope

These are valid nodes, but they are outside the current v1 scope.

#### `utilities.selectors`

- `EffectTypeSelectorNode`
- `EntityTypeSelectorNode`
- `ItemTypeSelectorNode`
- `SoundEventSelectorNode`

Decision:

- freeze outside the v1 mainline
- keep under `utilities.selectors` or move to `deferred.out_of_scope`

Reason:

- entity, item, effect, and sound selection are not part of the current v1 building/modeling scope

#### `utilities.advanced`

- `EvalExpressionNode`
- `FilterByAttributeNode`
- `GetAttributeNode`
- `NodeGroupNode`
- `ScriptNode`
- `SetAttributeNode`

Decision:

- freeze outside the v1 mainline
- likely move to `deferred.experimental` or `deferred.workflow`

Reason:

- script execution and ad-hoc attribute systems are experimental/data-layer behavior
- they are not part of the current v1 modeling taxonomy

### Split: Mixed Quality / Mixed Scope

#### `utilities.data_conversion`

- `CoordinateToVectorNode`
- `VectorToCoordinateNode`
- `NumberToBooleanNode`
- `NumberToIntegerNode`
- `TextToValueNode`
- `ColorToComponentsNode`
- `ComponentsToColorNode`

Decision:

- do not leave this as one permanent category
- split by role

Recommended handling:

- `CoordinateToVectorNode`
  - migrate candidate
  - likely belongs near `reference.points` / `reference.vectors` as a type bridge
- `VectorToCoordinateNode`
  - migrate candidate
  - likely belongs near `world.selection` / `reference.points` as a grid snap or coordinate conversion helper
- `NumberToIntegerNode`
  - redundant with `math.scalar_math.floor / round / ceiling`
  - delete candidate after confirming no unique behavior is required
- `NumberToBooleanNode`
  - keep only if explicit casting is needed in graphs
  - otherwise delete/defer candidate
- `TextToValueNode`
  - deferred/tooling candidate
  - not part of the v1 main modeling tree
- `ColorToComponentsNode`
  - deferred/out-of-scope candidate
- `ComponentsToColorNode`
  - deferred/out-of-scope candidate

### Keep As Legacy Compatibility

The entire subtree below should stay isolated and not be mixed back into the canonical tree:

- `utilities.legacy.spatial.analysis.*`
- `utilities.legacy.spatial.generators.*`
- `utilities.legacy.spatial.instancing.*`
- `utilities.legacy.spatial.points.*`
- `utilities.legacy.spatial.voxel.*`

Decision:

- keep as compatibility debt only
- do not treat these as unfinished v1 migration targets

## Delete Candidates

These are the first candidates to remove, but only after reference and registration checks are completed.

- `utilities.data_conversion.NumberToIntegerNode`
  - overlaps with existing scalar math rounding nodes
- `utilities.flow_control.CompareNode`
  - overlaps with existing compare-family nodes

Possible later candidates:

- `utilities.data_conversion.NumberToBooleanNode`
- `utilities.data_conversion.TextToValueNode`

These should only be removed after checking whether saved graphs or editor templates still instantiate them.

## Immediate Migration Priority

### Priority 1

- `utilities.flow_control.BranchNode` -> `math.logic.if`
- `utilities.flow_control.SwitchNode` -> `math.logic.switch`
- resolve `utilities.flow_control.CompareNode`

### Priority 2

- split `utilities.data_conversion`
- migrate the coordinate/vector bridge nodes
- decide deletion vs defer for numeric/text/color conversion nodes

### Priority 3

- move workflow-only nodes into a stable non-mainline bucket
  - `utilities.workflow` or `deferred.workflow`

### Priority 4

- freeze out-of-scope selectors and advanced nodes
  - `utilities.selectors`
  - `utilities.advanced`

## Recommended Rule Going Forward

Do not use `utilities` as a long-term parking lot.

Every node placed under `utilities` should be explicitly one of:

- editor helper
- workflow helper
- deferred feature
- legacy compatibility

If a node is actually canonical for the v1 modeling tree, migrate it into the main taxonomy instead of leaving it in `utilities`.
