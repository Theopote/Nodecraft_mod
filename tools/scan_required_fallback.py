import io, re, glob, os, json, subprocess

# Find nodes whose input ports are declared required but whose processNode uses a fallback resolver.
FALLBACK_TOKENS = [
    'resolveOptionalPoint', 'resolveOptionalVector', 'resolveOptionalUsableAxis', 'resolveOptionalPlane',
    'resolveOptionalFiniteDouble', 'resolveOptionalPositiveFiniteDouble', 'resolveOptionalNonNegativeFiniteDouble',
    'resolveOptionalBoundedExactInteger', 'resolveOptionalExactPositiveInteger',
    'resolveConstructionPlane', 'resolveConstructionCenter', 'resolveConstructionBasis',
]

roots = ['src/main/java/com/nodecraft/nodesystem/nodes/']
rows = []
for root in roots:
    for path in glob.glob(root + '**/*.java', recursive=True):
        src = io.open(path, encoding='utf-8').read()
        if not any(t in src for t in FALLBACK_TOKENS):
            continue
        # collect input port ids declared required
        for m in re.finditer(r'addInputPort\(\s*new BasePort\(\s*(?P<pid>INPUT_\w+)\s*,(?P<rest>(?:[^()]|\([^()]*\))*?NodeDataType\.\w+,\s*this)\s*\)\s*\);', src, re.S):
            pid = m.group('pid')
            rest = m.group('rest')
            if 'false, false' in rest:
                continue
            # only if the port id is used with a fallback resolver
            if re.search(r'(resolve\w*)\(\s*' + pid + r'\s*,', src):
                rows.append((os.path.basename(path), pid))
for r in sorted(rows):
    print(r[0], r[1])
