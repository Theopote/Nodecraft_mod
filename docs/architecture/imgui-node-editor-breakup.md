# ImGuiNodeEditor breakup (0.9)

Promote the former Phase F non-goal — full god-class breakup of `ImGuiNodeEditor` —
into a staged 0.9 program. Goal is **one mutable concern → one owner**, not smaller files.

## Target shape

```
ImGuiNodeEditor                    (frame loop + wiring only)
├─ EditorDocumentState             ✓ graph, positions, dirty
├─ EditorInteractionState          ✓ mode + selection
├─ EditorViewportState             ✓ zoom / pan / grid (Phase J)
├─ SubgraphEditService             ✓ stack + create/open/close/dissolve/rename (Phase K)
├─ ConnectionEditService           ✓ connect/disconnect/reroute/preview (Phase L)
├─ NodeCommandService              ✓ add/delete/duplicate/align (Phase M)
├─ EditorSession                   (open flag, presentation flags — later)
│
├─ ImGuiNodeHistory / Clipboard    (already extracted; delete still via clipboard)
├─ AutoPreviewController           ✓
└─ ImGuiNodeRenderer / Interaction (already extracted; navigation chrome stays on editor)
```

## Principle

| Rule | Meaning |
|------|---------|
| Single owner | Only one type writes a given state |
| Editor coordinates | Public `ICanvasEditor` API may stay as thin delegates |
| Behavior freeze | Each slice is zero-behavior-change unless noted |
| Not file-size | Exit when ownership is clear, not when KB targets are hit |

## Phase J — EditorViewportState

**Owns:** canvas zoom, offset X/Y, show-grid flag.

**Still deferred:**

- `CanvasComponent` mirrored zoom/offset (dual write until a later merge)

## Phase K — SubgraphEditService

**Owns:** nested edit stack; create / open / close / dissolve / rename.

**Stays on editor (chrome):** subgraph navigation overlay + rename popup.

## Phase L — ConnectionEditService

**Owns:** connect / disconnect / dangling cleanup / reroute insert / drag-preview validation.

**Stays on editor (chrome):** port hover tooltip; screen→world / `DragPreview` conversion.

## Phase M — NodeCommandService (current)

**Owns:** add / addWithState / deleteSelected / duplicateSelected / align.

**Still via clipboard:** multi-select delete snapshot/history (`Host.deleteSelectedViaClipboard`) — clipboard remains owner of that implementation until a later tighten.

**Host:** document, interaction, history, dirty notify, `node_added` event, selection helpers.

## Later phases (order)

1. **EditorSession** — `isOpen`, presentation flags (display mode, colors, disabled/hidden)
2. Optional: fold clipboard delete ownership into `NodeCommandService`
3. Optional: collapse CanvasComponent mirror onto `EditorViewportState`

## Non-goals (still)

- Multi-document tabs
- Rewriting ImGui / new UI framework
- Bulk AI panel / SelectedBlockNode breakup in the same track

## Exit gates

### Phase J–L
As previously documented.

### Phase M
1. Node add/delete/duplicate/align live in `NodeCommandService`
2. `NodeCommandServiceAlignTest` + subgraph / AI apply tests green
3. `ICanvasEditor` node-command methods remain thin delegates
