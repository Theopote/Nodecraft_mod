import json, sys
d = json.load(open('build/node-schema-dump.json', encoding='utf-8'))
for k in sys.argv[1:]:
    e = d.get(k)
    if e is None:
        print('MISSING', k)
        continue
    print('==', k)
    print('  IN ', e.get('inputs'))
    print('  OUT', e.get('outputs'))
    print('  ST ', e.get('state'))
