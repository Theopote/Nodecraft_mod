import json
import os
from pathlib import Path

# Node ID mapping: incorrect -> correct
NODE_ID_MAP = {
    # Geometry Primitives
    "geometry.primitives.box_by_corner_and_size": "geometry.primitives.box_from_corner_size",
    "geometry.primitives.cylinder_by_axis_and_radius": "geometry.primitives.cylinder",

    # Transform
    "transform.basic.move": "transform.basic_transforms.translate",
    "transform.basic.rotate": "transform.basic_transforms.rotate",
    "transform.basic.scale": "transform.basic_transforms.scale",

    # Material
    "material.gradient_mapping.height_gradient": "material.gradient_mapping.height_gradient_map",
    "material.basic_assignment.assign_block_type": "material.basic_assignment.assign_block_type",  # Correct

    # Output
    "output.bake.geometry_to_blocks": "output.execute.bake_geometry_to_blocks",
    "output.preview.preview_blocks": "output.preview.block_preview",
    "output.preview.geometry_viewer": "output.preview.geometry_preview",

    # Curves
    "geometry.curves.divide_curve": "geometry.curves.divide_curve_to_points",
    "geometry.curves.helix": "geometry.curves.helix",  # Correct
    "geometry.curves.arc": "geometry.curves.arc",  # Correct

    # Profiles
    "geometry.profiles.triangle": "geometry.profiles.polygon_profile",
    "geometry.profiles.rectangle": "geometry.profiles.rectangle_profile",
    "geometry.profiles.circle": "geometry.profiles.circle_profile",
    "geometry.profiles.arc": "geometry.profiles.sector_profile",

    # Boolean
    "geometry.boolean.union_multiple": "geometry.boolean.union",
    "geometry.boolean.union": "geometry.boolean.union",  # Correct
    "geometry.boolean.difference": "geometry.boolean.difference",  # Correct

    # Solids
    "geometry.solids.extrude": "geometry.solids.extrude_profile",
    "geometry.solids.sweep": "geometry.solids.sweep",  # Correct

    # Patterns
    "patterns.array.linear": "patterns.instances.array_linear",
    "patterns.instances.instance_on_points": "patterns.instances.instance_geometry_to_points",

    # Input
    "input.context.player_position": "input.context.player_position",  # Correct
}

def fix_preset_node_ids(preset_path):
    """Fix node IDs in a preset file"""
    with open(preset_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    modified = False

    # Fix node type IDs
    if 'graph' in data and 'nodes' in data['graph']:
        for node in data['graph']['nodes']:
            old_type = node.get('type')
            if old_type in NODE_ID_MAP:
                new_type = NODE_ID_MAP[old_type]
                if new_type != old_type:
                    print(f"  Fixing: {old_type} → {new_type}")
                    node['type'] = new_type
                    modified = True

    if modified:
        with open(preset_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        return True
    return False

# Fix all preset files
preset_dir = Path(r"F:\development\NC\nodecraft\presets")
print("Fixing node IDs in preset files...\n")

fixed_count = 0
for preset_file in preset_dir.rglob("preset.json"):
    print(f"Checking: {preset_file.relative_to(preset_dir)}")
    try:
        if fix_preset_node_ids(preset_file):
            print(f"✅ Fixed!\n")
            fixed_count += 1
        else:
            print(f"  No changes needed\n")
    except Exception as e:
        print(f"❌ Error: {e}\n")

print(f"{'='*60}")
print(f"Fixed {fixed_count} preset files")
print("\nNext: Run the converter again to regenerate graph_presets.json")
