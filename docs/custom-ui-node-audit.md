# Custom UI Node Audit

Baseline: commit `4a9b239` (2025) introduced `NodeVisualLayout` and **globally disabled** canvas custom UI via `nodeHasCustomUI = false` in `ImGuiNodeRenderer`. Node-level `renderCustomUIScaled` implementations were **not removed**.

Phase 2 baseline: `8b4a70f` (restore canvas custom UI rendering) + interaction-state stabilization.

Comparison: `4a9b239^` vs `HEAD` — no commits after `4a9b239` removed custom UI from individual node classes. Regression is renderer-level only.

**Full acceptance status: NOT claimed.** Automated contracts pass; in-game interaction for all 44 nodes remains outstanding.

## Summary

| Metric | Count |
|--------|------:|
| `BaseCustomUINode` subclasses (incl. abstract) | 41 |
| Concrete custom UI nodes (incl. type selectors) | 44 |
| Nodes with `renderCustomUIScaled` present | 44 |
| Recommended **Restore** | 44 |
| Recommended **Intentionally Panel-Only** | 0 |
| Automated registry / metrics contract | **Pass** |
| In-game interaction acceptance | **Pending** |

### Pass/fail legend

| Column | Meaning |
|--------|---------|
| Auto | Instantiated via registry as `ICustomUINode`, `hasCustomUI()`, finite nonnegative height/width (headless metrics) |
| In-game | Mouse / keyboard / popup / zoom / undo verified in client (Phase 2 §3) — blank until manual QA |

## Audit Table

| Priority | Type ID | Class | Auto | In-game | Action |
|----------|---------|-------|------|---------|--------|
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

## Phase 2 fixes and root causes

| Issue | Root cause | Fix |
|-------|------------|-----|
| Custom UI missing on canvas | `ImGuiNodeRenderer` hardcoded `nodeHasCustomUI = false` | Use `layout.hasCustomUI()`; call `renderCustomUI()` |
| Layout ignored custom UI size | `NodeVisualLayout` omitted custom metrics | `CustomUIMetrics` height/width/regionTop |
| Multi-node interaction wipe | `resetCustomUIInteractionState()` per node | `beginInteractionFrame()` once/frame + merge |
| Screen mouse capture ignored active widgets | `ImGuiNodeInteraction.nodeCustomUIActive` never written | `CustomUIRenderer` syncs active widget node ID |
| Audit type IDs mismatched registry | Doc used guessed IDs (`float_input` etc.) | Aligned to `@NodeInfo.id` |

## Interaction-state contract (§1)

- Frame reset: `ImGuiNodeRenderer.renderNodesDirect` → `customUIRenderer.beginInteractionFrame()` once.
- Hover / active associated with node UUID via `mergeInteractionSnapshot`.
- Active widget on node A survives later render of node B.
- Empty custom-UI hover still allows node drag; widget hover/active blocks drag.
- Direct-draw and ImGui paths both call the same merge helper.
- `NodecraftScreen` reads `interaction.isNodeCustomUIActive()` (now synced).

## Automated tests (§2 / §4 / §5)

| Test | Covers |
|------|--------|
| `CustomUINodeRegistryTest` | 44 audit IDs registered + exact set match |
| `CustomUIRuntimeMetricsAuditTest` | Instantiate each `ICustomUINode`; `hasCustomUI`; finite metrics |
| `CustomUIRendererInteractionStateTest` | Multi-node merge + empty hover + shared merge semantics |
| `CustomUIStructuralOpsTest` | CreateList / SignalFork port count immediate update |
| `GraphStructureEditSupportTest` | CreateList / SignalFork undo + dangling edges |
| `NodeVisualLayoutTest` | Custom UI expands layout in TEXT_ONLY and ICON_ONLY |
| `ImGuiNodeRendererCustomUITest` | Guard against reintroducing hard-disable |

## Manual in-game verification checklist (§3 / screenshots)

Representative nodes (both TEXT_ONLY and ICON_ONLY):

- [ ] Float Slider / Integer Slider — drag value without moving node
- [ ] XY Slider / Circular Angle (`angle_picker`)
- [ ] Boolean Toggle
- [ ] Text Input — focus, edit, commit
- [ ] Color Picker / Gradient Ramp — popup behavior
- [ ] Create List — +/- ports, undo, no dangling wires
- [ ] Signal Fork — +/- branches, undo
- [ ] Block Type Selector — popup / search
- [ ] Value Monitor
- [ ] Geometry Viewer / Preview Blocks
- [ ] Apply Changes

Also:

- [ ] Two custom UI nodes: active slider on A not broken when B is visible
- [ ] Zoom screenshots at **50% / 100% / 200%** (no clipping)
- [ ] Empty custom UI region still selects / drags node

## Intentionally inactive

None. All 44 concrete custom UI nodes are **Restore**.
