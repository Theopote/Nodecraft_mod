# Custom UI Node Audit

Baseline: commit `4a9b239` (2025) introduced `NodeVisualLayout` and **globally disabled** canvas custom UI via `nodeHasCustomUI = false` in `ImGuiNodeRenderer`. Node-level `renderCustomUIScaled` implementations were **not removed**.

Comparison: `4a9b239^` vs `HEAD` — no commits after `4a9b239` removed custom UI from individual node classes. Regression is renderer-level only.

## Summary

| Metric | Count |
|--------|------:|
| `BaseCustomUINode` subclasses (incl. abstract) | 41 |
| Concrete custom UI nodes (incl. type selectors) | 44 |
| Nodes with `renderCustomUIScaled` present | 44 |
| Recommended **Restore** | 44 |
| Recommended **Intentionally Panel-Only** | 0 |

## Audit Table

| Priority | Type ID | Class | Historical controls | Current UI code | Registered | Action |
|----------|---------|-------|---------------------|-----------------|------------|--------|
| A | `input.numeric.integer_input` | `IntegerInputNode` | int input + drag | Present | Yes | Restore |
| A | `input.numeric.float_input` | `FloatInputNode` | float input + drag | Present | Yes | Restore |
| A | `input.numeric.range_input` | `RangeInputNode` | min/max sliders | Present | Yes | Restore |
| A | `input.numeric.angle_slider` | `AngleSliderNode` | angle slider | Present | Yes | Restore |
| A | `input.numeric.float_slider` | `FloatSliderNode` | float slider | Present | Yes | Restore |
| A | `input.numeric.integer_slider` | `IntegerSliderNode` | int slider | Present | Yes | Restore |
| A | `input.numeric.circular_angle` | `CircularAngleNode` | circular angle dial | Present | Yes | Restore |
| A | `input.numeric.xy_slider` | `XYSliderNode` | 2D XY slider | Present | Yes | Restore |
| A | `reference.vectors.vector2_input` | `Vector2InputNode` | X/Y inputs | Present | Yes | Restore |
| A | `reference.vectors.vector_input` | `VectorInputNode` | X/Y/Z inputs | Present | Yes | Restore |
| A | `reference.points.coordinate_input` | `CoordinateInputNode` | coordinate fields | Present | Yes | Restore |
| A | `input.values.boolean_toggle` | `BooleanToggleNode` | checkbox/toggle | Present | Yes | Restore |
| A | `input.values.text_input` | `TextInputNode` | text field | Present | Yes | Restore |
| A | `input.values.color_picker` | `ColorPickerNode` | color picker | Present | Yes | Restore |
| A | `input.values.gradient_ramp` | `GradientRampNode` | gradient editor | Present | Yes | Restore |
| A | `input.values.file_path_input` | `FilePathInputNode` | path field + browse | Present | Yes | Restore |
| A | `math.list.create_list` | `CreateListNode` | +/- dynamic ports | Present | Yes | Restore |
| A | `math.scalar_math.expression` | `ExpressionNode` | expression field | Present | Yes | Restore |
| A | `utilities.assist.signal_fork` | `SignalForkNode` | fork count control | Present | Yes | Restore |
| A | `input.type_selectors.block_type_selector` | `BlockTypeSelectorNode` | registry picker | Present | Yes | Restore |
| A | `input.type_selectors.item_type_selector` | `ItemTypeSelectorNode` | registry picker | Present | Yes | Restore |
| A | `input.type_selectors.entity_type_selector` | `EntityTypeSelectorNode` | registry picker | Present | Yes | Restore |
| A | `input.type_selectors.biome_selector` | `BiomeSelectorNode` | registry picker | Present | Yes | Restore |
| B | `output.debug.value_monitor` | `ValueMonitorNode` | live value text | Present | Yes | Restore |
| B | `output.debug.panel` | `PanelNode` | preview panel | Present | Yes | Restore |
| B | `output.debug.stopwatch` | `StopwatchNode` | timer controls | Present | Yes | Restore |
| B | `output.debug.print_to_chat` | `PrintToChatNode` | send button | Present | Yes | Restore |
| B | `output.preview.geometry_viewer` | `GeometryViewerNode` | viewer controls | Present | Yes | Restore |
| B | `output.preview.preview_blocks` | `PreviewBlocksNode` | preview controls | Present | Yes | Restore |
| B | `output.execute.apply_changes` | `ApplyChangesNode` | apply/bake button | Present | Yes | Restore |
| B | `output.execute.undo_last_bake` | `UndoLastBakeNode` | undo button | Present | Yes | Restore |
| B | `output.execute.redo_last_bake` | `RedoLastBakeNode` | redo button | Present | Yes | Restore |
| B | `output.execute.clear_all_previews` | `ClearAllPreviewsNode` | clear button | Present | Yes | Restore |
| B | `output.execute.bake_status` | `BakeStatusNode` | status display | Present | Yes | Restore |
| C | `output.export.export_worldedit` | `ExportWorldEditNode` | export controls | Present | Yes | Restore |
| C | `output.export.export_schematic` | `ExportSchematicNode` | export controls | Present | Yes | Restore |
| C | `output.export.export_litematic` | `ExportLitematicNode` | export controls | Present | Yes | Restore |
| C | `input.context.player_position` | `PlayerPositionNode` | coords + Update button | Present | Yes | Restore |
| C | `input.context.player_raycast` | `PlayerRaycastNode` | raycast controls | Present | Yes | Restore |
| C | `world.selection.selected_entity` | `SelectedEntityNode` | pick controls | Present | Yes | Restore |
| C | `world.selection.selected_region` | `SelectedRegionNode` | region pick UI | Present | Yes | Restore |
| C | `world.selection.selected_block` | `SelectedBlockNode` | block pick + expand | Present | Yes | Restore |
| C | `world.selection.selected_block_sequence` | `SelectedBlockSequenceNode` | sequence pick UI | Present | Yes | Restore |
| C | `utilities.assist.coalesce` | `CoalesceNode` | coalesce controls | Present | Yes | Restore |

## Renderer fix (Phase 2)

- `NodeVisualLayout` now adds `customUIHeight`, `customUIRegionTop`, and `hasCustomUI` to layout results.
- `ImGuiNodeRenderer` re-enables `customUIRenderer.hasCustomUI(node)` and draws controls below ports in both TEXT_ONLY and ICON_ONLY modes.
- `invisibleButton` covers title/ports only so widgets remain clickable.

## Manual in-game verification checklist

- [ ] Float Slider / Boolean Toggle / Create List / Value Monitor visible in TEXT_ONLY
- [ ] Same nodes visible in ICON_ONLY
- [ ] Slider drag does not move node
- [ ] CreateList +/- updates ports with undo
- [ ] Zoom 25%–200% without clipping
