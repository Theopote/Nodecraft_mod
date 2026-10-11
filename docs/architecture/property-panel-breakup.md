# Property panel breakup (0.9 follow-on)

Stage ownership splits for `PropertyPanelComponent` so property UI is no longer
a single owner for discovery, edit session, editors, and node actions.

## Target shape

```
PropertyPanelComponent                 (layout / tabs / selection wiring)
├─ PropertyInspector                   ✓ descriptor cache over NodePropertyBindings
├─ PropertyEditSession                 ✓ temp widgets / edit locks / error counts (Phase 1)
├─ PropertyEditorRegistry              ✓ primitive editors (Phase 2)
├─ PropertyRendererRegistry            ✓ complex-type renderers already extracted
├─ NodeActionPanel                     ✓ ResettableNode + common Reset/Delete (Phase 3)
├─ NodeActionProviderRegistry          ✓ assist / apply chrome providers (Phase 4)
└─ property.support                    ✓ GeometryViewer / color / enum labels (Phase 5)
```

## Phase 2 — Primitive PropertyEditorRegistry

**Owns:** bool / Boolean / String / int / Integer / float / Float / double / Double editors;
enum renderer used as fallback for `type.isEnum()`.

**Files:** `BooleanPropertyRenderer`, `StringPropertyRenderer`, `IntPropertyRenderer`,
`FloatPropertyRenderer`, `DoublePropertyRenderer`, `EnumPropertyRenderer`,
`PropertyEditorRegistry.registerPrimitives()`.

**Panel:** still hosts layout / selection; GeometryViewer / color / enum helpers live in `property.support`.

## Phase 3 — ResettableNode + drop Custom UI reflection (done)

**Owns:** typed reset capability and ICustomUINode-only custom UI sizing/rendering.

**Changes:**
- Added `ResettableNode`; `NodeActionPanel` calls `instanceof ResettableNode` (no `getMethod`)
- `CustomUIRenderer` / `PortPositionCalculator` use `ICustomUINode` only
- Removed `NodeDrawingUtils.shouldCheckReflection` and marker `ICustomUICapable`

**Note:** no production nodes implement `ResettableNode` yet — the interface is opt-in.
Custom UI nodes already extend `BaseCustomUINode` / implement `ICustomUINode`.

## Phase 4 — NodeActionProvider (done)

**Owns:** node-specific action chrome above properties and in the action strip.

**Files:**
- `NodeActionProvider`, `NodeActionProviderRegistry`, `NodeActionGraphSupport`
- `actions/SignalForkActionProvider`, `CoalesceActionProvider`,
  `TagRelayActionProvider`, `ApplyChangesActionProvider`

**Panel:** `NodeActionPanel` only orchestrates providers + universal Reset/Delete.

## Phase 5 — GeometryViewer / color helpers off panel (done)

**Owns:** GeometryViewer visibility + chrome, hex-string color picker, enum display labels.

**Files:**
- `property.support.GeometryViewerPropertySupport`
- `property.support.StringColorPropertyEditor`
- `property.support.EnumPropertyLabels`

**Panel:** filters via `GeometryViewerPropertySupport.shouldDisplayProperty`;
primitive renderers call support classes directly.

## Later phases (order)

1. Optional `PropertyInspectorModel` for selection + category shell
2. Property-panel undo/redo via `PropertyChangeCommand` recorded in `ImGuiNodeHistory.recordPropertyChange` (Phase 2 — wired from `PropertyPanelComponent.applyPropertyValue`, coalesces consecutive edits to the same property)
3. Property-panel layout pass (Phase 3 — compact header, collapsed ports by default, no repeated Property/Value headers, responsive label width, readonly scalars as text)
4. Unified scalar/enum/vector editors (Phase 4 — `ScalarPropertyEditor`, semantic percent/angle/count modes, segmented enums, full-precision vector rows)
5. Per-node property metadata (Phase 5 — `@NodeProperty` min/max/step, `PropertyConstraints`, `PropertyVisibilitySupport`, audited GeometryViewer + attractor fields)

## Acceptance fix round (post-d5ce633)

- Property undo coalescing is scoped by `PropertyEditSession` edit-session id (drag coalesces; independent edits do not)
- Reset uses `NodeStateChangeAction` with before/after node-state snapshots and rollback on failure
- `PropertyConstraints` validates finite bounds and rejects contradictory min/max merges
- `PropertyValueSnapshots` adds typed copies for common mutable property values

## Phase 2A — vector / geometry property editors

- `VectorComponentEditor` finishes the edit session only when an axis deactivates and no axis remains active; per-axis text sync avoids wiping sibling inputs on X→Y focus changes
- `BlockPosPropertyRenderer` reuses the same session model with integer `dragInt` rows; read-only uses compact text (no disabled drag widgets)
- Vec3 / Vector3d length tooltip binds to any hovered axis in the row
- Polyline / Region default to one-line summaries (`PropertyValueFormatters`) with `treeNode` details; both honor `isDisabled`

## Phase 2B — Plane / Curve / Color / List

- Plane edits use unit-normal + offset D via `PlaneEquationEdit`; four-axis session finish uses `VectorEditSessionLogic`
- Curve default summary avoids `getSamplePoints()`; sample count only inside Details
- Color read-only shows swatch + hex/RGBA text (no disabled `colorEdit4`)
- List / BlockPosList: disabled state, count summary, Details fold with 32-item preview cap

## Phase 2C — Plane origin + snapshot / list follow-ups

- `PlaneEquationEdit.commit(previous, …)` preserves construction origin: rotate around `getPoint()`, move origin along normal when D changes (no `fromEquation` origin rewrite)
- Curve sample count is on-demand (`Compute sample count` / `Refresh`), not every frame while Details is open
- List / BlockPosList Details support start-index paging (page size 32)
- `PropertyValueSnapshots` throws `SnapshotIsolationException` instead of aliasing unsupported mutable values

## Exit gates — Phase 1

1. Panel no longer holds `tempValues` / edit-lock / `errorCounts` maps
2. `PropertyEditSessionTest` green
3. Renderers continue to go through panel facade methods (no renderer API break required)

## Exit gates — Phase 2

1. Primitive editors no longer defined inline on `PropertyPanelComponent`
2. `PropertyEditorRegistry.registerPrimitives()` owns registration
3. Project compiles; property session tests green

## Exit gates — Phase 3

1. Reset path uses `ResettableNode`, not reflection
2. Custom UI path uses `ICustomUINode` only (no reflection fallback)
3. `ResettableNodeContractTest` green; project compiles

## Exit gates — Phase 4

1. Assist / ApplyChanges chrome lives in `NodeActionProvider` implementations
2. `NodeActionPanel` no longer hardcodes fork/merge/tag/apply render bodies
3. `NodeActionProviderRegistryTest` green; project compiles

## Exit gates — Phase 5

1. GeometryViewer / color / enum label helpers are not methods on `PropertyPanelComponent`
2. `PropertySupportHelpersTest` green; project compiles
