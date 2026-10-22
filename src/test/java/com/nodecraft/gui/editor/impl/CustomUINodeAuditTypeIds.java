package com.nodecraft.gui.editor.impl;

import java.util.Set;

/** Canonical 50 custom UI node type IDs from docs/custom-ui-node-audit.md (matches @NodeInfo.id). */
final class CustomUINodeAuditTypeIds {

    static final Set<String> ALL = Set.of(
            "input.numeric.integer",
            "input.numeric.float",
            "input.numeric.range",
            "input.numeric.angle",
            "input.numeric.float_slider",
            "input.numeric.integer_slider",
            "input.numeric.angle_picker",
            "input.numeric.xy_slider",
            "reference.vectors.vector2_input",
            "reference.vectors.vector",
            "reference.points.block_position",
            "input.values.boolean_toggle",
            "input.values.text_input",
            "input.values.color_picker",
            "input.values.gradient_ramp",
            "input.values.file_path",
            "math.list.create_list",
            "math.scalar_math.expression",
            "utilities.assist.signal_fork",
            "input.type_selectors.block_type_selector",
            "input.type_selectors.item_type_selector",
            "input.type_selectors.entity_type_selector",
            "input.type_selectors.biome_selector",
            "output.debug.value_monitor",
            "output.debug.data_inspector",
            "output.debug.execution_timer",
            "output.debug.print_to_chat",
            "output.preview.geometry_viewer",
            "output.preview.preview_blocks",
            "output.execute.apply_changes",
            "output.execute.undo_last_bake",
            "output.execute.redo_last_bake",
            "output.execute.clear_preview",
            "output.execute.bake_status",
            "output.export.export_worldedit",
            "output.export.export_schematic",
            "output.export.export_litematic",
            "input.context.player_position",
            "input.context.player_raycast",
            "world.selection.selected_entity",
            "world.selection.selected_region",
            "world.selection.selected_block",
            "world.selection.selected_block_sequence",
            "utilities.assist.coalesce",
            "math.scalar_math.graph_mapper",
            "math.data_tree.viewer",
            "material.basic_assignment.create_block_palette",
            "material.basic_assignment.weighted_palette",
            "material.block_state.build_block_state",
            "material.gradient_mapping.gradient_ramp_map"
    );

    private CustomUINodeAuditTypeIds() {
    }
}
