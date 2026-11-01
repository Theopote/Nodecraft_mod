import glob, re, sys, xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding='utf-8')
for f in sorted(glob.glob('build/test-results/test/TEST-*.xml')):
    root = ET.parse(f).getroot()
    print(root.get('name'), 'tests=%s failures=%s errors=%s skipped=%s' % (
        root.get('tests'), root.get('failures'), root.get('errors'), root.get('skipped')))
