import json, collections

gp = json.load(open('src/main/resources/nodecraft/graph_presets.json', encoding='utf-8'))
dump = json.load(open('build/node-schema-dump.json', encoding='utf-8'))

used = collections.Counter()
for cat in gp['categories']:
    for p in cat['presets']:
        for n in p['nodes']:
            used[n['typeId']] += 1

allids = set(dump)
print('total node types:', len(allids), 'covered:', len(set(used) & allids))
missing = sorted(allids - set(used))
print('missing count:', len(missing))
for m in missing:
    print('  ', m)
