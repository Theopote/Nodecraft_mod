import json, sys, re
d = json.load(open('build/node-schema-dump.json', encoding='utf-8'))
pat = sys.argv[1] if len(sys.argv) > 1 else '.'
rx = re.compile(pat, re.I)
for k, v in d.items():
    if rx.search(k):
        print('==', k)
        print('  in :', ', '.join(v['inputs'] or []))
        print('  out:', ', '.join(v['outputs'] or []))
        st = v.get('state')
        if st:
            print('  st :', json.dumps(st, ensure_ascii=False)[:400])
