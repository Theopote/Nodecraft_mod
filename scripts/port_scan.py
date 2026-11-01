import re, pathlib, json

root = pathlib.Path('src/main/java/com/nodecraft/nodesystem/nodes/geometry/primitives')
report = []
for f in sorted(root.glob('*.java')):
    t = f.read_text(encoding='utf-8')
    if 'resolveOptional' not in t:
        continue
    ports = re.findall(r'addInputPort\(new BasePort\((.*?)\)\);', t, re.S)
    for decl in ports:
        decl = ' '.join(decl.split())
        optional = decl.endswith('false, false') or decl.endswith('true, false')
        report.append((f.name, decl, optional))
for name, decl, opt in report:
    print(('OPT ' if opt else 'REQ ') + name + ' :: ' + decl[:150])
