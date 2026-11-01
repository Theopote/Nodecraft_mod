import json, sys, re
d = json.load(open('build/node-schema-dump.json', encoding='utf-8'))
ids = sys.argv[1:]
for i in ids:
    v = d.get(i)
    if v is None:
        print('== MISSING', i)
        continue
    print('==', i)
    print('  in :', ', '.join(v['inputs'] or []))
    print('  out:', ', '.join(v['outputs'] or []))
    print('  st :', json.dumps(v.get('state'), ensure_ascii=False))
