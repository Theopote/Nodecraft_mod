import re, sys, io

BASE = 'src/main/java/com/nodecraft/nodesystem/nodes/geometry/primitives/'

# file -> list of port id constants whose input ports should become optional
TARGETS = {
    'TorusByCenterAxisRadiiNode.java': ['INPUT_CENTER_ID', 'INPUT_AXIS_ID', 'INPUT_MAJOR_RADIUS_ID', 'INPUT_MINOR_RADIUS_ID'],
    'ConeByBaseApexRadiusNode.java': ['INPUT_BASE_CENTER_ID', 'INPUT_APEX_ID', 'INPUT_RADIUS_ID'],
    'CapsuleByAxisRadiusNode.java': ['INPUT_START_ID', 'INPUT_END_ID', 'INPUT_RADIUS_ID'],
    'EllipsoidByCenterRadiiNode.java': ['INPUT_CENTER_ID', 'INPUT_RADIUS_X_ID', 'INPUT_RADIUS_Y_ID', 'INPUT_RADIUS_Z_ID'],
    'SquarePyramidNode.java': ['INPUT_CENTER_ID', 'INPUT_BASE_SIZE_ID', 'INPUT_HEIGHT_ID', 'INPUT_PLANE_ID', 'INPUT_X_AXIS_ID'],
    'CylinderByAxisRadiusNode.java': ['INPUT_START_ID', 'INPUT_END_ID', 'INPUT_RADIUS_ID'],
    'HemisphereByCenterAxisRadiusNode.java': ['INPUT_CENTER_ID', 'INPUT_AXIS_ID', 'INPUT_RADIUS_ID'],
    'BoxCornerSizeNode.java': ['INPUT_CORNER_ID', 'INPUT_PLANE_ID', 'INPUT_SIZE_X_ID', 'INPUT_SIZE_Y_ID', 'INPUT_SIZE_Z_ID',
                               'INPUT_ROT_X_ID', 'INPUT_ROT_Y_ID', 'INPUT_ROT_Z_ID'],
    'FrustumByTwoCentersRadiiNode.java': ['INPUT_BASE_RADIUS_ID', 'INPUT_TOP_RADIUS_ID'],
}

for fname, port_ids in TARGETS.items():
    path = BASE + fname
    src = io.open(path, encoding='utf-8').read()
    orig = src
    for pid in port_ids:
        # match the addInputPort(...) line containing this port id
        pat = re.compile(r'(addInputPort\(new BasePort\(' + pid + r',.*?)NodeDataType\.(\w+), this\)\);', re.S)
        def repl(m):
            return m.group(1) + 'NodeDataType.' + m.group(2) + ', this, false, false));'
        src, n = pat.subn(repl, src, count=1)
        if n != 1:
            print('WARN no match', fname, pid)
    if src != orig:
        io.open(path, 'w', encoding='utf-8', newline='').write(src)
        print('patched', fname)

# AbstractPolyhedronNode: generic size port id
path = BASE + 'AbstractPolyhedronNode.java'
src = io.open(path, encoding='utf-8').read()
orig = src
src, n1 = re.subn(r'(addInputPort\(new BasePort\(INPUT_CENTER_ID,.*?)NodeDataType\.(\w+), this\)\);',
                  r'\1NodeDataType.\2, this, false, false));', src, count=1, flags=re.S)
src, n2 = re.subn(r'(addInputPort\(new BasePort\(inputSizeId,.*?)NodeDataType\.(\w+), this\)\);',
                  r'\1NodeDataType.\2, this, false, false));', src, count=1, flags=re.S)
src, n3 = re.subn(r'(addInputPort\(new BasePort\(INPUT_ORIENTATION_ID,.*?)NodeDataType\.(\w+), this\)\);',
                  r'\1NodeDataType.\2, this, false, false));', src, count=1, flags=re.S)
print('polyhedron', n1, n2, n3)
if src != orig:
    io.open(path, 'w', encoding='utf-8', newline='').write(src)
    print('patched AbstractPolyhedronNode.java')
