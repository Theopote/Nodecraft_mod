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
- `actions/SignalForkActionProvider`, `SignalMergeActionProvider`,
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
