# Custom UI Node Audit

Baseline: commit `4a9b239` (2025) introduced `NodeVisualLayout` and **globally disabled** canvas custom UI via `nodeHasCustomUI = false` in `ImGuiNodeRenderer`. Node-level `renderCustomUIScaled` implementations were **not removed**.

Phase 2 baseline: `8b4a70f` / `20934cf` (restore + interaction-state stabilization).

Phase 3 focus: continuous pointer interaction, headless vs in-game layout contract split, structural Undo/Redo.

**Full acceptance status: NOT claimed.** Do not mark a node Passed based solely on inheritance or headless fallback metrics.

## Summary

| Metric | Count / Status |
|--------|------:|
| Concrete custom UI nodes (`CustomUINodeAuditTypeIds`) | 44 |
| Recommended **Restore** | 44 |
| Recommended **Intentionally Panel-Only** | 0 |
| Headless contract (instantiate + hasCustomUI) | **Pass** |
| Direct-draw hovered/active/changed + hold-without-change | **Pass (unit)** |
| CreateList / SignalFork Undo+Redo E2E | **Pass (unit)** |
| In-game layout / interaction (44 nodes) | **Pending** |

### Pass/fail legend

| Column | Meaning |
|--------|---------|
| Headless | Registry instantiate as `ICustomUINode`, `hasCustomUI()` — **not** real layout sizes |
| In-game | Renders, usable, no clip, property/output updates, Undo where required — client QA only |

## Audit Table

Authoritative inventory: `CustomUINodeAuditTypeIds`.

| Priority | Type ID | Class | Headless | In-game (renders / usable / clip / props / undo) | Action |
|----------|---------|-------|----------|--------------------------------------------------|--------|
| A | `input.numeric.integer` | `IntegerInputNode` | Pass | Pending | Restore |
| A | `input.numeric.float` | `FloatInputNode` | Pass | Pending | Restore |
| A | `input.numeric.range` | `RangeInputNode` | Pass | Pending | Restore |
| A | `input.numeric.angle` | `AngleSliderNode` | Pass | Pending | Restore |
| A | `input.numeric.float_slider` | `FloatSliderNode` | Pass | Pending | Restore |
| A | `input.numeric.integer_slider` | `IntegerSliderNode` | Pass | Pending | Restore |
| A | `input.numeric.angle_picker` | `CircularAngleNode` | Pass | Pending | Restore |
| A | `input.numeric.xy_slider` | `XYSliderNode` | Pass | Pending | Restore |
| A | `reference.vectors.vector2_input` | `Vector2InputNode` | Pass | Pending | Restore |
| A | `reference.vectors.vector` | `VectorInputNode` | Pass | Pending | Restore |
| A | `reference.points.block_position` | `CoordinateInputNode` | Pass | Pending | Restore |
| A | `input.values.boolean_toggle` | `BooleanToggleNode` | Pass | Pending | Restore |
| A | `input.values.text_input` | `TextInputNode` | Pass | Pending | Restore |
| A | `input.values.color_picker` | `ColorPickerNode` | Pass | Pending | Restore |
| A | `input.values.gradient_ramp` | `GradientRampNode` | Pass | Pending | Restore |
| A | `input.values.file_path` | `FilePathInputNode` | Pass | Pending | Restore |
| A | `math.list.create_list` | `CreateListNode` | Pass | Pending | Restore |
| A | `math.scalar_math.expression` | `ExpressionNode` | Pass | Pending | Restore |
| A | `utilities.assist.signal_fork` | `SignalForkNode` | Pass | Pending | Restore |
| A | `input.type_selectors.block_type_selector` | `BlockTypeSelectorNode` | Pass | Pending | Restore |
| A | `input.type_selectors.item_type_selector` | `ItemTypeSelectorNode` | Pass | Pending | Restore |
| A | `input.type_selectors.entity_type_selector` | `EntityTypeSelectorNode` | Pass | Pending | Restore |
| A | `input.type_selectors.biome_selector` | `BiomeSelectorNode` | Pass | Pending | Restore |
| B | `output.debug.value_monitor` | `ValueMonitorNode` | Pass | Pending | Restore |
| B | `output.debug.data_inspector` | `PanelNode` | Pass | Pending | Restore |
| B | `output.debug.execution_timer` | `StopwatchNode` | Pass | Pending | Restore |
| B | `output.debug.print_to_chat` | `PrintToChatNode` | Pass | Pending | Restore |
| B | `output.preview.geometry_viewer` | `GeometryViewerNode` | Pass | Pending | Restore |
| B | `output.preview.preview_blocks` | `PreviewBlocksNode` | Pass | Pending | Restore |
| B | `output.execute.apply_changes` | `ApplyChangesNode` | Pass | Pending | Restore |
| B | `output.execute.undo_last_bake` | `UndoLastBakeNode` | Pass | Pending | Restore |
| B | `output.execute.redo_last_bake` | `RedoLastBakeNode` | Pass | Pending | Restore |
| B | `output.execute.clear_preview` | `ClearAllPreviewsNode` | Pass | Pending | Restore |
| B | `output.execute.bake_status` | `BakeStatusNode` | Pass | Pending | Restore |
| C | `output.export.export_worldedit` | `ExportWorldEditNode` | Pass | Pending | Restore |
| C | `output.export.export_schematic` | `ExportSchematicNode` | Pass | Pending | Restore |
| C | `output.export.export_litematic` | `ExportLitematicNode` | Pass | Pending | Restore |
| C | `input.context.player_position` | `PlayerPositionNode` | Pass | Pending | Restore |
| C | `input.context.player_raycast` | `PlayerRaycastNode` | Pass | Pending | Restore |
| C | `world.selection.selected_entity` | `SelectedEntityNode` | Pass | Pending | Restore |
| C | `world.selection.selected_region` | `SelectedRegionNode` | Pass | Pending | Restore |
| C | `world.selection.selected_block` | `SelectedBlockNode` | Pass | Pending | Restore |
| C | `world.selection.selected_block_sequence` | `SelectedBlockSequenceNode` | Pass | Pending | Restore |
| C | `utilities.assist.coalesce` | `CoalesceNode` | Pass | Pending | Restore |

## Phase 3 fixes

| Issue | Root cause | Fix |
|-------|------------|-----|
| Headless metrics false positive | Fallback sizes treated as real layout | Split `CustomUIHeadlessContractTest`; document placeholders; in-game dump via `CustomUIInGameLayoutDiagnostics` |
| Direct-draw active = changed | Boolean return conflated value-change with pointer capture | `DirectDrawInteraction(hovered, active, changed)` + cross-frame pointer capture |
| Structural Undo without Redo | CreateList/SignalFork tests stopped at undo | `GraphStructureEditSupportTest` E2E: connect → mutate → Undo → Redo |
| SignalFork Redo kept default branch count | Gson history copy turns `Integer` into `Double`; `setNodeState` only accepted `Integer` | Accept `Number` (same pattern as CreateList) |

## Contract layers

### Headless (CI unit tests)

- Registration / instantiate / `hasCustomUI()`
- Interaction-state merge (no ImGui)
- Structure + Undo/Redo graph snapshots
- **Must not** claim real `calculateUIHeight` / widget clipping

### In-game layout contract

1. Launch client with `-Dnodecraft.customui.layoutDiagnostics=true` (and **without** `headlessMetrics`).
2. Place representative nodes; set zoom to 0.5 / 1.0 / 2.0; TEXT_ONLY and ICON_ONLY.
3. Confirm log lines show non-placeholder heights; visually check clipping.
4. Priority interactive set: Float Slider, XY Slider, Color Picker, Gradient Ramp, Create List, Geometry Viewer.

## Direct-draw nodes

| Type ID | Direct draw | Notes |
|---------|-------------|-------|
| `input.numeric.angle_picker` | Optional (`allowDirectDrawing`) | Read-only preview → `DirectDrawInteraction.NONE` |
| All others | `supportsDirectDrawing() == false` | ImGui path uses `isAnyItemActive()` (continuous hold) |

## Automated tests

| Test | Layer |
|------|--------|
| `CustomUIHeadlessContractTest` | Headless registry + hasCustomUI |
| `CustomUINodeRegistryTest` | Exact 44-ID set match |
| `CustomUIRendererInteractionStateTest` | Multi-node merge + hold-without-change capture |
| `CustomUIStructuralOpsTest` | Port count immediate update |
| `GraphStructureEditSupportTest` | CreateList / SignalFork Undo **and** Redo |
| `NodeVisualLayoutTest` | Layout expands for custom UI (both modes) |
| `ImGuiNodeRendererCustomUITest` | Guard against hard-disable regression |

## Manual in-game checklist (seal criterion)

- [ ] Float / Integer Slider — drag without moving node; hold still mid-drag
- [ ] XY Slider / Circular Angle
- [ ] Boolean Toggle / Text Input
- [ ] Color Picker / Gradient Ramp — popups
- [ ] Create List / Signal Fork — +/- → Undo → Redo, no dangling wires
- [ ] Block Type Selector / Value Monitor / Geometry Viewer / Preview Blocks / Apply Changes
- [ ] Two custom UI nodes: active control on A survives B on canvas
- [ ] Screenshots at **50% / 100% / 200%** zoom
- [ ] Layout diagnostics log at 0.5 / 1.0 / 2.0 without headless placeholders

## Intentionally inactive

None. All 44 are **Restore**.
