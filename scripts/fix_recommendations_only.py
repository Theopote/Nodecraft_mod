from pathlib import Path
import json

path = Path(r"f:\development\ND\nodecraft\src\main\resources\nodecraft\node_recommendations.json")
text = path.read_bytes().decode("utf-8", errors="replace")
replacements = [
    ('"reason": "变换几何', '"reason": "变换几何体",'),
    ('"reason": "烘焙为方', '"reason": "烘焙为方块",'),
    ('"reason": "檐口', '"reason": "檐口梁",'),
    ('"reason": "屋脊', '"reason": "屋脊梁",'),
    ('"reason": "在梁位放置配', '"reason": "在梁位放置配件",'),
    ('"reason": "洞口作为差集刀', '"reason": "洞口作为差集刀具",'),
    ('"reason": "自定义窗框布', '"reason": "自定义窗框布局",'),
]

lines = []
for line in text.splitlines(keepends=True):
    if '"reason": "' in line and not line.rstrip().endswith('",'):
        indent = line[: line.index('"reason"')]
        for prefix, replacement in replacements:
            if prefix in line:
                line = indent + replacement + "\n"
                break
    lines.append(line)

fixed = "".join(lines)
data = json.loads(fixed)
path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("ok")
