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
├─ NodeCommandService              ✓ add/delete/duplicate/align (Phase M + delete tighten)
├─ EditorSession                   ✓ open + presentation flags (Phase N)
│
├─ ImGuiNodeHistory / Clipboard    (clipboard: copy/cut/paste; delete delegates to commands)
├─ AutoPreviewController           ✓
└─ ImGuiNodeRenderer / Interaction (already extracted; navigation chrome stays on editor)
```

`CanvasComponent` no longer mirrors viewport/session state — it reads/writes
`EditorViewportState` / `EditorSession` through `ICanvasEditor`.

## Principle

| Rule | Meaning |
|------|---------|
| Single owner | Only one type writes a given state |
| Editor coordinates | Public `ICanvasEditor` API may stay as thin delegates |
| Behavior freeze | Each slice is zero-behavior-change unless noted |
| Not file-size | Exit when ownership is clear, not when KB targets are hit |

## Phase J — EditorViewportState

**Owns:** canvas zoom, offset X/Y, show-grid flag.

**CanvasComponent:** pan/zoom/grid UI mutates the same `EditorViewportState` (no local copies).

## Phase K — SubgraphEditService

**Owns:** nested edit stack; create / open / close / dissolve / rename.

**Stays on editor (chrome):** subgraph navigation overlay + rename popup.

## Phase L — ConnectionEditService

**Owns:** connect / disconnect / dangling cleanup / reroute insert / drag-preview validation.

**Stays on editor (chrome):** port hover tooltip; screen→world / `DragPreview` conversion.

## Phase M — NodeCommandService

**Owns:** add / addWithState / deleteSelected / duplicateSelected / align.

**Still via clipboard:** ~~multi-select delete~~ — delete ownership folded into `NodeCommandService` (see Phase M tighten below).

**Host:** document, interaction, history, dirty notify, `node_added` event, selection helpers, remove position/selection.

## Phase N — EditorSession

**Owns:** `isOpen`; node display mode; show-previews flag; per-node custom colors; disabled / hidden sets.

**CanvasComponent:** display-mode / show-previews UI mutates the same `EditorSession` (no local copies).

**Side effects via Host:** structure-dirty notify; `PreviewManager.hideNodePreviews` on disable (kept on editor host, not inside session).

**API:** `ICanvasEditor.getEditorSession()`; open/color/disabled/visible methods remain thin delegates on `ImGuiNodeEditor`.

## Phase M tighten — delete ownership

**Owns:** multi-select delete + history snapshots (`captureRemovedNodeSnapshot` / `recordRemoveNodes`).

**Clipboard:** `ImGuiNodeClipboard.deleteSelectedNodes()` is a thin delegate to `editor.deleteSelectedNodes()` (cut still goes through clipboard → editor → commands).

## Phase O — CanvasComponent mirror collapse (current)

**Removed dual-write:** `CanvasComponent` no longer stores zoom/offset/grid/display/preview copies or pushes `setCanvasView` each frame.

**Still on CanvasComponent:** drag/zoom gesture chrome, grid drawing, context menu, `NodeDisplayMode` enum (maps to `EditorSession` ints).

## Later phases (order)

_None remaining on this breakup track._ Optional follow-ups live outside this doc (AI panel / SelectedBlockNode / multi-doc).

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

### Phase N
1. Open / display / colors / disabled / hidden live in `EditorSession`
2. `EditorSessionTest` green; editor public API unchanged as thin delegates
3. Renderer / menus still read via `ICanvasEditor` delegates (no direct session coupling required)

### Phase M tighten (delete)
1. Delete logic lives in `NodeCommandService`; no `deleteSelectedViaClipboard` Host hook
2. Clipboard delete is a delegate only; cut still works
3. Delete unit test + existing editor command tests green

### Phase O
1. `CanvasComponent` has no mirrored zoom/offset/grid/display/preview fields
2. Pan/zoom/grid/display/preview mutations go through `EditorViewportState` / `EditorSession`
3. Editor command / session / subgraph tests green
