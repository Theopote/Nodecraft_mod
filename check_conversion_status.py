import json
import os

# Check which presets are in the converted file
with open(r"F:\development\NC\nodecraft\src\main\resources\nodecraft\graph_presets.json", 'r', encoding='utf-8') as f:
    data = json.load(f)

print("=== Current Presets in graph_presets.json ===\n")

composite_count = 0
placeholder_count = 0

for category in data['categories']:
    print(f"\nCategory: {category['id']} - {category.get('displayName', 'N/A')}")
    for preset in category['presets']:
        kind = preset.get('kind', 'unknown')
        if kind == 'composite':
            print(f"  ✅ {preset['id']} - {preset.get('displayName', 'N/A')}")
            composite_count += 1
        elif kind == 'placeholder':
            print(f"  ⚠️  {preset['id']} - {preset.get('displayName', 'N/A')} (placeholder)")
            placeholder_count += 1

print(f"\n{'='*60}")
print(f"Total composite presets: {composite_count}")
print(f"Total placeholder presets: {placeholder_count}")
print(f"Total presets: {composite_count + placeholder_count}")

# Check which preset files exist but weren't converted
print(f"\n{'='*60}")
print("=== Checking preset files ===\n")

preset_dir = r"F:\development\NC\nodecraft\presets"
found_presets = []

for root, dirs, files in os.walk(preset_dir):
    if 'preset.json' in files:
        preset_path = os.path.join(root, 'preset.json')
        try:
            with open(preset_path, 'r', encoding='utf-8') as f:
                preset_data = json.load(f)
                preset_id = preset_data.get('preset_id', 'unknown')
                found_presets.append(preset_id)
                print(f"✅ {preset_id}")
        except Exception as e:
            print(f"❌ {preset_path}: {e}")

print(f"\nTotal preset files found: {len(found_presets)}")

# Check which ones are missing from graph_presets.json
converted_ids = []
for category in data['categories']:
    for preset in category['presets']:
        if preset.get('kind') == 'composite':
            converted_ids.append(preset['id'])

print(f"\n{'='*60}")
print("=== Missing from conversion ===\n")

for preset_id in found_presets:
    if preset_id not in converted_ids:
        print(f"❌ {preset_id}")

print(f"\nMissing: {len(found_presets) - len([pid for pid in found_presets if pid in converted_ids])}")
