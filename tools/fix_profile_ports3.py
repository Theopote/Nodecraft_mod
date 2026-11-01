import io, re, glob, os

BASE = 'src/main/java/com/nodecraft/nodesystem/nodes/geometry/profiles/'

# Match addInputPort(new BasePort(PID, ...) ... ) with balanced parens, ignoring semicolons in strings.
CALL = re.compile(r'addInputPort\(\s*new BasePort\(\s*(?P<pid>INPUT_\w+)\s*,(?P<rest>(?:[^()]|\([^()]*\))*?NodeDataType\.\w+,\s*this)\s*\)\s*\);', re.S)

for path in sorted(glob.glob(BASE + '*.java')):
    src = io.open(path, encoding='utf-8').read()
    orig = src
    hits = set()

    def repl(m):
        pid = m.group('pid')
        rest = m.group('rest')
        # only fallback-backed ports: those whose name starts with INPUT_ and are not strict data inputs
        if pid in ('INPUT_PROFILE_ID', 'INPUT_REGION_ID', 'INPUT_POINTS_ID', 'INPUT_SITES_ID'):
            return m.group(0)
        if 'false, false' in rest:
            return m.group(0)
        hits.add(pid)
        return 'addInputPort(new BasePort(' + pid + ',' + rest + ', false, false));'

    src = CALL.sub(repl, src)
    if src != orig:
        io.open(path, 'w', encoding='utf-8', newline='').write(src)
        print('patched', os.path.basename(path), sorted(hits))
