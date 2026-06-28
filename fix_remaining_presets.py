import os
import re

# Files that still have JSON errors (need to fix the closing bracket issue)
files_to_fix = [
    ("simple-door", 72),
    ("modern-window", 80),
    ("fountain-circular", 99),
    ("gazebo", 99),
    ("basic-sphere", 58),
]

preset_base = r"F:\development\NC\nodecraft\presets"

fixed_count = 0

for filename, line_num in files_to_fix:
    # Find the file
    for root, dirs, files in os.walk(preset_base):
        if 'preset.json' in files and filename in root:
            filepath = os.path.join(root, 'preset.json')
            print(f"Fixing: {filepath}")

            with open(filepath, 'r', encoding='utf-8') as f:
                lines = f.readlines()

            # Look for the pattern around the line number
            for i in range(max(0, line_num - 5), min(len(lines), line_num + 5)):
                line = lines[i]
                # Fix:   }  },  to   }  ],
                if line.strip() == '},':
                    # Check if next non-empty line contains "graph"
                    for j in range(i+1, min(len(lines), i+10)):
                        if '"graph"' in lines[j]:
                            print(f"  Line {i+1}: Replacing '}},' with '],'")
                            lines[i] = line.replace('},', '],')
                            fixed_count += 1
                            break
                    break

            # Write back
            with open(filepath, 'w', encoding='utf-8') as f:
                f.writelines(lines)
            print(f"  ✅ Fixed!")
            break

print(f"\nTotal files fixed: {fixed_count}")
print("\nNow run the converter again!")
