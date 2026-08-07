# EditorDocumentState split

Design freeze for Phase F.

## Goal

Separate **document data** (graph + node layout + unsaved/dirty generation) from
**editor chrome** (selection, canvas view, menus, ImGui frame loop).

```
ImGuiNodeEditor (UI / interaction)
    └── EditorDocumentState (graph, positions, dirty version)
            └── AutoPreviewController.DirtyVersionSource
```

## Owns (this slice)

| Field | Notes |
|-------|--------|
| `NodeGraph graph` | Active editable graph (including subgraph edit target) |
| `Map<UUID, NodePosition> nodePositions` | Canvas layout for the active graph |
| `dirty` / `dirtyVersion` | Unsaved flag + monotonic generation for auto-preview |

## Stays on the editor (deferred / later)

- Subgraph edit stack / rename UI
- Canvas zoom/offset, display mode, custom colors, disabled/hidden sets
- History / clipboard / menus / renderer

Selection + exclusive gesture mode moved to Phase G (`EditorInteractionState`).

## Wiring

- `ImGuiNodeEditor` holds one `EditorDocumentState`
- `ImGuiNodeIO.markDirty` / `isDirty` / `getDirtyVersion` delegate to the document
- Auto-preview uses `document` as `DirtyVersionSource` directly
- `setCurrentGraph` resets document graph and clears positions via editor orchestration

## Non-goals

- Multi-document tabs
- Detaching history into the document
- Full god-class breakup of `ImGuiNodeEditor`

## Exit gates

1. Unit tests for graph/positions/dirty version semantics
2. Editor compiles and uses `EditorDocumentState` as source of truth
3. Advancement Phase F marked PASS
