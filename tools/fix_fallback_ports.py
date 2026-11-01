import io, re, glob, os

CALL = re.compile(r'addInputPort\(\s*new BasePort\(\s*(?P<pid>INPUT_\w+)\s*,(?P<rest>(?:[^()]|\([^()]*\))*?NodeDataType\.\w+,\s*this)\s*\)\s*\);', re.S)
RESOLVE = re.compile(r'(?P<fn>resolve\w+)\(\s*(?P<pid>INPUT_\w+)\s*,(?P<fb>(?:[^()]|\([^()]*\))*?)\)\s*;', re.S)

# resolvers that demand a wire => port stays required
STRICT = re.compile(r'(resolveRequired|resolveStrict)')

total = 0
for path in glob.glob('src/main/java/com/nodecraft/nodesystem/nodes/**/*.java', recursive=True):
    src = io.open(path, encoding='utf-8').read()
    fallback_ports = set()
    for m in RESOLVE.finditer(src):
        fn = m.group('fn')
        if STRICT.search(fn):
            continue
        fb = m.group('fb').strip()
        if fb.startswith('null') or fb.startswith('"'):
            continue
        fallback_ports.add(m.group('pid'))
    if not fallback_ports:
        continue
    changed = False
    hits = set()

    def repl(m):
        global changed
        pid = m.group('pid')
        rest = m.group('rest')
        if pid not in fallback_ports:
            return m.group(0)
        if 'false, false' in rest:
            return m.group(0)
        hits.add(pid)
        return 'addInputPort(new BasePort(' + pid + ',' + rest + ', false, false));'

    src = CALL.sub(repl, src)
    if src != io.open(path, encoding='utf-8').read():
        io.open(path, 'w', encoding='utf-8', newline='').write(src)
        total += len(hits)
        print(os.path.basename(path), sorted(hits))
print('TOTAL', total)
