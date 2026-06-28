import json
import os
from pathlib import Path

# Comprehensive node ID mapping
NODE_ID_FIXES = {
    # Geometry Primitives
    "geometry.primitives.box_by_center_and_size": "geometry.primitives.box",
    "geometry.primitives.box_by_corner_and_size": "geometry.primitives.box_from_corner_size",
    "geometry.primitives.cylinder_by_axis_and_radius": "geometry.primitives.cylinder",
    "geometry.primitives.sphere_by_center_and_radius": "geometry.primitives.sphere",

    # Transform
    "transform.basic.move": "transform.basic_transforms.translate",
    "transform.basic.rotate": "transform.basic_transforms.rotate",
    "transform.basic.scale": "transform.basic_transforms.scale",

    # Output
    "output.bake.geometry_to_blocks": "output.execute.bake_geometry_to_blocks",
    "output.preview.preview_blocks": "output.preview.block_preview",
    "output.preview.geometry_viewer": "output.preview.geometry_preview",

    # Material
    "material.gradient_mapping.height_gradient": "material.gradient_mapping.height_gradient_map",

    # Curves
    "geometry.curves.divide_curve": "geometry.curves.divide_curve_to_points",

    # Profiles
    "geometry.profiles.triangle": "geometry.profiles.polygon_profile",
    "geometry.profiles.rectangle": "geometry.profiles.rectangle_profile",
    "geometry.profiles.circle": "geometry.profiles.circle_profile",
    "geometry.profiles.arc": "geometry.profiles.sector_profile",

    # Boolean
    "geometry.boolean.union_multiple": "geometry.boolean.union",

    # Solids
    "geometry.solids.extrude": "geometry.solids.extrude_profile",

    # Patterns
    "patterns.array.linear": "patterns.instances.array_linear",
    "patterns.instances.instance_on_points": "patterns.instances.instance_geometry_to_points",
}

def fix_preset_file(filepath):
    """Fix node IDs in a single preset file"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)

        fixed_count = 0

        # Fix node types
        if 'graph' in data and 'nodes' in data['graph']:
            for node in data['graph']['nodes']:
                old_type = node.get('type', '')
                if old_type in NODE_ID_FIXES:
                    new_type = NODE_ID_FIXES[old_type]
                    node['type'] = new_type
                    fixed_count += 1
                    print(f"    {old_type} → {new_type}")

        # Write back if changes were made
        if fixed_count > 0:
            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
            return fixed_count
        return 0

    except Exception as e:
        print(f"    ❌ Error: {e}")
        return 0

# Process all preset files
preset_dir = Path(r"F:\development\NC\nodecraft\presets")
print("="*60)
print("Fixing Node IDs in All Preset Files")
print("="*60)
print()

total_files = 0
total_fixes = 0

for preset_file in preset_dir.rglob("preset.json"):
    relative_path = preset_file.relative_to(preset_dir)
    print(f"📁 {relative_path}")

    fixes = fix_preset_file(preset_file)
    if fixes > 0:
        print(f"  ✅ Fixed {fixes} node ID(s)")
        total_fixes += fixes
    else:
        print(f"  ℹ️  No changes needed")

    total_files += 1
    print()

print("="*60)
print(f"Summary: Fixed {total_fixes} node IDs across {total_files} files")
print("="*60)
