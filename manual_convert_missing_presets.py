import json
import os
from pathlib import Path

# Load existing graph_presets.json
graph_presets_path = Path(r"F:\development\NC\nodecraft\src\main\resources\nodecraft\graph_presets.json")
with open(graph_presets_path, 'r', encoding='utf-8') as f:
    graph_data = json.load(f)

# Find or create categories
categories = {cat['id']: cat for cat in graph_data['categories']}

# Helper function to convert a preset
def convert_preset(preset_json_path):
    with open(preset_json_path, 'r', encoding='utf-8') as f:
        preset_data = json.load(f)

    preset_id = preset_data['preset_id']
    metadata = preset_data['metadata']
    graph = preset_data['graph']

    # Convert nodes
    nodes = []
    for node in graph['nodes']:
        nodes.append({
            'ref': node['id'],
            'typeId': node['type'],
            'x': float(node['position']['x']),
            'y': float(node['position']['y'])
        })

    # Convert connections
    connections = []
    for conn in graph['connections']:
        # Add output_/input_ prefix if not present
        from_port = conn['from']['port']
        to_port = conn['to']['port']

        if not from_port.startswith('output_') and not from_port.startswith('input_'):
            from_port = 'output_' + from_port
        if not to_port.startswith('output_') and not to_port.startswith('input_'):
            to_port = 'input_' + to_port

        connections.append({
            'fromRef': conn['from']['node'],
            'fromPort': from_port,
            'toRef': conn['to']['node'],
            'toPort': to_port
        })

    # Create the converted preset
    converted = {
        'id': preset_id,
        'displayName': metadata['name'],
        'description': metadata['description'],
        'kind': 'composite',
        'nodes': nodes,
        'connections': connections
    }

    return converted, metadata['category']

# List of preset files to convert
preset_files = [
    r"F:\development\NC\nodecraft\presets\quickstart\garden-wall\preset.json",
    r"F:\development\NC\nodecraft\presets\quickstart\basic-sphere\preset.json",
    r"F:\development\NC\nodecraft\presets\building-elements\stairs\straight-staircase\preset.json",
    r"F:\development\NC\nodecraft\presets\building-elements\windows\modern-window\preset.json",
    r"F:\development\NC\nodecraft\presets\building-elements\columns\classical-column\preset.json",
    r"F:\development\NC\nodecraft\presets\building-elements\doors\simple-door\preset.json",
    r"F:\development\NC\nodecraft\presets\architectural\residential\simple-house\preset.json",
    r"F:\development\NC\nodecraft\presets\architectural\infrastructure\stone-bridge\preset.json",
    r"F:\development\NC\nodecraft\presets\architectural\infrastructure\watchtower\preset.json",
    r"F:\development\NC\nodecraft\presets\decorative\fountain-circular\preset.json",
    r"F:\development\NC\nodecraft\presets\decorative\gazebo\preset.json",
    r"F:\development\NC\nodecraft\presets\styles\modern\glass-box-building\preset.json",
    r"F:\development\NC\nodecraft\presets\styles\fantasy\wizard-tower\preset.json",
    r"F:\development\NC\nodecraft\presets\styles\medieval\castle-keep\preset.json",
]

print("Converting missing presets...\n")

converted_count = 0
failed_count = 0

for preset_file in preset_files:
    if not os.path.exists(preset_file):
        print(f"❌ File not found: {preset_file}")
        failed_count += 1
        continue

    try:
        converted_preset, category = convert_preset(preset_file)
        preset_id = converted_preset['id']

        # Add to appropriate category
        if category == 'quickstart':
            cat_id = 'quickstart'
        elif category == 'building_elements':
            cat_id = 'building_elements'
        elif category == 'architectural':
            cat_id = 'architectural'
        elif category == 'decorative':
            cat_id = 'decorative'
        elif category == 'styles':
            # Styles category doesn't exist yet, we'll add to existing categories
            if 'modern' in preset_id:
                cat_id = 'design_tools'
            elif 'fantasy' in preset_id or 'medieval' in preset_id:
                cat_id = 'architectural_styles'
            else:
                cat_id = 'architectural_styles'
        else:
            cat_id = category

        # Find or create category
        if cat_id not in categories:
            new_cat = {
                'id': cat_id,
                'displayName': cat_id.replace('_', ' ').title(),
                'presets': []
            }
            categories[cat_id] = new_cat
            graph_data['categories'].append(new_cat)

        # Add preset to category
        categories[cat_id]['presets'].append(converted_preset)

        print(f"✅ {preset_id}")
        converted_count += 1

    except Exception as e:
        print(f"❌ Failed to convert {preset_file}: {e}")
        failed_count += 1

print(f"\n{'='*60}")
print(f"Converted: {converted_count}")
print(f"Failed: {failed_count}")

# Write back to file
output_path = Path(r"F:\development\NC\nodecraft\src\main\resources\nodecraft\graph_presets_complete.json")
with open(output_path, 'w', encoding='utf-8') as f:
    json.dump(graph_data, f, indent=2, ensure_ascii=False)

print(f"\nOutput written to: {output_path}")
print("\nNext steps:")
print("1. Review the file")
print("2. Copy graph_presets_complete.json to graph_presets.json")
print("3. Restart NodeCraft")
