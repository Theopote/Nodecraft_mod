# EditorInteractionMode

Design freeze for Phase G.

## Goal

Make canvas interaction **mode + selection** an explicit, testable state object —
separate from document data (`EditorDocumentState`) and from ImGui frame plumbing.

```
ImGuiNodeEditor
 ├── EditorDocumentState      (graph, positions, dirty)
 └── EditorInteractionState   (mode, selection)
        └── used by ImGuiNodeInteraction
```

## Owns (this slice)

| Concern | Type |
|---------|------|
| Interaction mode | `EditorInteractionMode` |
| Primary + multi selection | `EditorInteractionState` |
| Mode enter rules | Only `IDLE → *` and `* → IDLE` via `tryEnter` / `resetToIdle` |

## Modes

| Mode | Meaning |
|------|---------|
| `IDLE` | No exclusive gesture |
| `DRAGGING_NODE` | Moving one or more nodes |
| `BOX_SELECTING` | Marquee selection |
| `CREATING_CONNECTION` | Dragging a wire from a port |
| `PANNING_CANVAS` | Middle/right-drag pan |

## Non-goals

- Rewriting all gesture handlers
- World-picking `NodeEditorInteractionManager` (Minecraft interaction, separate)
- Subgraph stack (see breakup Phase later)
- Canvas zoom ownership — moved to Phase J [`imgui-node-editor-breakup.md`](./imgui-node-editor-breakup.md)
- Full input command bus

## Exit gates

1. `EditorInteractionState` unit tests (mode gate + selection)
2. `ImGuiNodeInteraction` uses `EditorInteractionMode`
3. `ImGuiNodeEditor` selection delegates to `EditorInteractionState`
4. Advancement Phase G marked PASS
