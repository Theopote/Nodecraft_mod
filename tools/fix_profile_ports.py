import io, os, re, glob

BASE = 'src/main/java/com/nodecraft/nodesystem/nodes/geometry/profiles/'

PORT_CALL = re.compile(r'addInputPort\(new BasePort\((?P<args>[^;]*?)\)\);', re.S)

# ports that have property fallbacks => optional
OPTIONAL = {
    'AnnularSectorOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_INNER_RADIUS_ID', 'INPUT_OUTER_RADIUS_ID',
                                      'INPUT_START_ANGLE_ID', 'INPUT_END_ANGLE_ID', 'INPUT_SEGMENTS_ID',
                                      'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'AnnulusOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_INNER_RADIUS_ID', 'INPUT_OUTER_RADIUS_ID',
                                'INPUT_SEGMENTS_ID', 'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'CapsuleOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_LENGTH_ID', 'INPUT_RADIUS_ID',
                                'INPUT_CAP_SEGMENTS_ID', 'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'CircleOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_RADIUS_ID', 'INPUT_SEGMENTS_ID',
                               'INPUT_PLANE_ID', 'INPUT_START_DIRECTION_ID'],
    'ConvexHull2DOnPlaneNode.java': ['INPUT_PLANE_ID'],
    'CrossOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_ARM_LENGTH_ID', 'INPUT_ARM_WIDTH_ID',
                              'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'EllipseOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_RADIUS_X_ID', 'INPUT_RADIUS_Y_ID',
                                'INPUT_SEGMENTS_ID', 'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'GearOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_TOOTH_COUNT_ID', 'INPUT_ROOT_RADIUS_ID',
                             'INPUT_TIP_RADIUS_ID', 'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'HeartOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_WIDTH_ID', 'INPUT_HEIGHT_ID',
                              'INPUT_SEGMENTS_ID', 'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'ProfileOffsetInPlaneNode.java': ['INPUT_OFFSET_ID'],
    'RectangleOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_WIDTH_ID', 'INPUT_HEIGHT_ID',
                                  'INPUT_PLANE_ID', 'INPUT_X_AXIS_ID'],
    'RegionOffsetInPlaneNode.java': ['INPUT_OFFSET_ID'],
    'RegularPolygonOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_RADIUS_ID', 'INPUT_SIDE_COUNT_ID',
                                       'INPUT_PLANE_ID', 'INPUT_START_DIRECTION_ID'],
    'ResamplePolygonProfileNode.java': ['INPUT_EDGE_COUNT_ID'],
    'RhombusOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_DIAGONAL_X_ID', 'INPUT_DIAGONAL_Y_ID',
                                'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'RoundedRectangleOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_WIDTH_ID', 'INPUT_HEIGHT_ID',
                                         'INPUT_RADIUS_ID', 'INPUT_CORNER_SEGMENTS_ID',
                                         'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'SectorOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_RADIUS_ID', 'INPUT_START_ANGLE_ID',
                               'INPUT_END_ANGLE_ID', 'INPUT_SEGMENTS_ID', 'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'SemiCircleOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_RADIUS_ID', 'INPUT_SEGMENTS_ID',
                                   'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'StarPolygonOnPlaneNode.java': ['INPUT_CENTER_ID', 'INPUT_OUTER_RADIUS_ID', 'INPUT_INNER_RADIUS_ID',
                                    'INPUT_POINT_COUNT_ID', 'INPUT_PLANE_ID', 'INPUT_AXIS_ID'],
    'VoronoiCells2DOnPlaneNode.java': ['INPUT_PLANE_ID'],
}

for fname, port_ids in OPTIONAL.items():
    path = BASE + fname
    src = io.open(path, encoding='utf-8').read()
    orig = src
    hits = set()

    def repl(m):
        args = m.group('args')
        pid = args.split(',')[0].strip()
        if pid not in port_ids:
            return m.group(0)
        if 'false, false' in args:
            return m.group(0)
        # last two args: node data type, this
        new_args = re.sub(r'(NodeDataType\.\w+,\s*this)\s*$', r'\1, false, false', args)
        if new_args == args:
            print('  MISS-ARG', fname, pid)
            return m.group(0)
        hits.add(pid)
        return 'addInputPort(new BasePort(' + new_args + '));'

    src = PORT_CALL.sub(repl, src)
    missing = set(port_ids) - hits
    if missing:
        print('WARN', fname, 'missing', sorted(missing))
    if src != orig:
        io.open(path, 'w', encoding='utf-8', newline='').write(src)
        print('patched', fname, len(hits))
