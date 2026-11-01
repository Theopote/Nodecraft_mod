import json, sys
d = json.load(open('build/node-schema-dump.json', encoding='utf-8'))
for k in sys.argv[1:]:
    if k not in d:
        print('==', k, 'MISSING'); continue
    v = d[k]
    print('==', k)
    print('  IN ', v.get('inputs'))
    print('  OUT', v.get('outputs'))
    print('  ST ', json.dumps(v.get('state'), ensure_ascii=False))
