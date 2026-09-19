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
└─ NodeActionPanel                     ✓ ResettableNode reset path (Phase 3)
```

## Phase 2 — Primitive PropertyEditorRegistry

**Owns:** bool / Boolean / String / int / Integer / float / Float / double / Double editors;
enum renderer used as fallback for `type.isEnum()`.

**Files:** `BooleanPropertyRenderer`, `StringPropertyRenderer`, `IntPropertyRenderer`,
`FloatPropertyRenderer`, `DoublePropertyRenderer`, `EnumPropertyRenderer`,
`PropertyEditorRegistry.registerPrimitives()`.

**Panel:** still hosts GeometryViewer / color-string / enum-label helpers used by those editors.

## Phase 3 — ResettableNode + drop Custom UI reflection (done)

**Owns:** typed reset capability and ICustomUINode-only custom UI sizing/rendering.

**Changes:**
- Added `ResettableNode`; `NodeActionPanel` calls `instanceof ResettableNode` (no `getMethod`)
- `CustomUIRenderer` / `PortPositionCalculator` use `ICustomUINode` only
- Removed `NodeDrawingUtils.shouldCheckReflection` and marker `ICustomUICapable`

**Note:** no production nodes implement `ResettableNode` yet — the interface is opt-in.
Custom UI nodes already extend `BaseCustomUINode` / implement `ICustomUINode`.

## Later phases (order)

1. Optional `NodeActionProvider` for assist-node action chrome
2. Optional `PropertyInspectorModel` for selection + category shell
3. Optional: move GeometryViewer/color helpers off the panel

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
