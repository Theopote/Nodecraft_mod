import json, sys
d = json.load(open('build/node-schema-dump.json', encoding='utf-8'))
for pat in sys.argv[1:]:
    for k in sorted(d):
        if pat in k:
            e = d[k]
            print('===', k)
            print('  in :', e['inputs'])
            print('  out:', e['outputs'])
            print('  st :', json.dumps(e.get('state'), ensure_ascii=False)[:600])
