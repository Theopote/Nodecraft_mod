import io, os, re

BASE = 'src/main/java/com/nodecraft/nodesystem/nodes/geometry/profiles/'

# Match addInputPort(new BasePort(PID, ... , NodeDataType.X, this));  (semicolons allowed inside quoted descriptions)
CALL = re.compile(r'addInputPort\(new BasePort\((?P<pid>INPUT_\w+),(?P<rest>(?:[^;]|\s)*?NodeDataType\.\w+,\s*this)\)\);', re.S)

OPTIONAL = {
    'AnnularSectorOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'AnnulusOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'CapsuleOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'CircleOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_PLANE_ID', 'INPUT_RADIUS_ID', 'INPUT_SEGMENTS_ID', 'INPUT_START_DIRECTION_ID'],
    'CrossOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'EllipseOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'GearOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'HeartOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'RegularPolygonOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'RhombusOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'RoundedRectangleOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'SectorOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'SemiCircleOnPlaneNode.java': ['INPUT_CENTER_ID'],
    'StarPolygonOnPlaneNode.java': ['INPUT_CENTER_ID'],
}

for fname, port_ids in OPTIONAL.items():
    path = BASE + fname
    src = io.open(path, encoding='utf-8').read()
    orig = src
    hits = set()

    def repl(m):
        pid = m.group('pid')
        if pid not in port_ids:
            return m.group(0)
        rest = m.group('rest')
        if 'false, false' in rest:
            return m.group(0)
        hits.add(pid)
        return 'addInputPort(new BasePort(' + pid + ',' + rest + ', false, false));'

    src = CALL.sub(repl, src)
    missing = set(port_ids) - hits
    if missing:
        print('WARN', fname, 'missing', sorted(missing))
    if src != orig:
        io.open(path, 'w', encoding='utf-8', newline='').write(src)
        print('patched', fname, sorted(hits))
