# Property panel breakup (0.9 follow-on)

Stage ownership splits for `PropertyPanelComponent` so property UI is no longer
a single owner for discovery, edit session, editors, and node actions.

## Target shape

```
PropertyPanelComponent                 (layout / tabs / selection wiring)
├─ PropertyInspector                   ✓ descriptor cache over NodePropertyBindings
├─ PropertyEditSession                 ✓ temp widgets / edit locks / error counts (Phase 1)
├─ PropertyEditorRegistry              (later: bool/number/string/enum/…)
├─ PropertyRendererRegistry            ✓ complex-type renderers already extracted
└─ NodeActionPanel                     ✓ actions chrome; ResettableNode later
```

## Phase 1 — PropertyEditSession (current)

**Owns:** per-property temp widget state, edit locks, error counts.

**Panel:** thin delegates (`getOrCreateTempValue`, `clearPropertyError`, lock helpers).

**Not in this slice:** moving boolean/number/string renderers out of the panel;
`GeometryViewerNode` special cases; `ResettableNode` / custom-UI interface cleanup.

## Later phases (order)

1. Extract primitive editors into `PropertyEditorRegistry`
2. `ResettableNode` / `CustomNodeUI` / `NodeActionProvider` to retire reflection special-cases
3. Optional `PropertyInspectorModel` for selection + category shell

## Exit gates — Phase 1

1. Panel no longer holds `tempValues` / edit-lock / `errorCounts` maps
2. `PropertyEditSessionTest` green
3. Renderers continue to go through panel facade methods (no renderer API break required)
