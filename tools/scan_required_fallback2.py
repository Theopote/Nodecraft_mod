import io, re, glob, os

# Only ports resolved with a NON-NULL property fallback are truly optional.
CALL = re.compile(r'addInputPort\(\s*new BasePort\(\s*(?P<pid>INPUT_\w+)\s*,(?P<rest>(?:[^()]|\([^()]*\))*?NodeDataType\.\w+,\s*this)\s*\)\s*\);', re.S)

# resolver call: name(PORT_ID, <fallback>)  -> capture the fallback text up to matching close paren
RESOLVE = re.compile(r'(?P<fn>resolve\w+)\(\s*(?P<pid>INPUT_\w+)\s*,(?P<fb>(?:[^()]|\([^()]*\))*?)\)\s*;', re.S)

STRICT_FNS = {'resolveStrictProfile', 'resolveStrictRegion'}

rows = []
for path in glob.glob('src/main/java/com/nodecraft/nodesystem/nodes/**/*.java', recursive=True):
    src = io.open(path, encoding='utf-8').read()
    fallback_ports = set()
    for m in RESOLVE.finditer(src):
        fn = m.group('fn')
        if fn in STRICT_FNS:
            continue
        fb = m.group('fb').strip()
        if fb.startswith('null'):
            continue
        fallback_ports.add(m.group('pid'))
    if not fallback_ports:
        continue
    for m in CALL.finditer(src):
        pid = m.group('pid')
        rest = m.group('rest')
        if 'false, false' in rest:
            continue
        if pid in fallback_ports:
            rows.append((path, pid))

for p, pid in sorted(rows):
    print(p, pid)
print('TOTAL', len(rows))
