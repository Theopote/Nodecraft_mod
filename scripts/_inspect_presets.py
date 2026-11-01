import json
d = json.load(open('src/main/resources/nodecraft/graph_presets.json', encoding='utf-8'))
for cat in d['categories']:
    for p in cat.get('presets', []):
        if p['id'] == 'building_elements.windows.modern_window':
            print(json.dumps(p, ensure_ascii=False, indent=2))
