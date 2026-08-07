# Quick Start: Fix Grayed-Out Presets

## Problem
Your 21 new presets appear grayed out in the UI and can't be used.

## Solution (5 minutes)

### Step 1: Run the Converter

**Option A: Using Java**
```bash
cd F:/development/NC/nodecraft
javac -cp "build/classes/java/main;lib/*" src/main/java/com/nodecraft/nodesystem/preset/PresetConverterTool.java
java -cp "build/classes/java/main;lib/*" com.nodecraft.nodesystem.preset.PresetConverterTool
```

**Option B: Using IDE**
1. Open `PresetConverterTool.java` in your IDE
2. Right-click → Run 'PresetConverterTool.main()'
3. Check console output

**Option C: Manual Script** (Copy-paste into terminal)
```java
// Save as RunConverter.java and run
import com.nodecraft.nodesystem.preset.*;
public class RunConverter {
    public static void main(String[] args) throws Exception {
        PresetConverterTool.main(args);
    }
}
```

### Step 2: Verify Output

Check that `src/main/resources/nodecraft/graph_presets_updated.json` was created:

```bash
ls -lh src/main/resources/nodecraft/graph_presets_updated.json
# Should be ~100KB+ in size
```

### Step 3: Replace Original

**Backup first:**
```bash
cp src/main/resources/nodecraft/graph_presets.json src/main/resources/nodecraft/graph_presets_backup.json
```

**Replace:**
```bash
cp src/main/resources/nodecraft/graph_presets_updated.json src/main/resources/nodecraft/graph_presets.json
```

### Step 4: Restart NodeCraft

Restart the application to load the new presets.

### Step 5: Verify

In the NodeCraft UI:
- ✅ All 21 presets should be visible
- ✅ All presets should be green (not grayed out)
- ✅ All presets should be draggable to canvas
- ✅ Try dragging "basic-box" to test

---

## If Converter Fails

### Alternative: Manual Integration

Edit `src/main/resources/nodecraft/graph_presets.json` directly:

1. Open the file
2. Find the `"composites"` category
3. Add your presets manually with this format:

```json
{
  "id": "quickstart.basic_box",
  "displayName": "基础方块",
  "description": "简单的方块结构",
  "kind": "composite",
  "nodes": [
    { "ref": "input_pos", "typeId": "input.context.player_position", "x": 50, "y": 200 },
    { "ref": "box", "typeId": "geometry.primitives.box_by_corner_and_size", "x": 250, "y": 200 },
    { "ref": "material", "typeId": "material.basic_assignment.assign_block_type", "x": 450, "y": 200 },
    { "ref": "bake", "typeId": "output.bake.geometry_to_blocks", "x": 650, "y": 200 },
    { "ref": "preview", "typeId": "output.preview.preview_blocks", "x": 850, "y": 200 }
  ],
  "connections": [
    { "fromRef": "input_pos", "fromPort": "output_position", "toRef": "box", "toPort": "input_corner" },
    { "fromRef": "box", "fromPort": "output_geometry", "toRef": "material", "toPort": "input_geometry" },
    { "fromRef": "material", "fromPort": "output_geometry", "toRef": "bake", "toPort": "input_geometry" },
    { "fromRef": "bake", "fromPort": "output_blocks", "toRef": "preview", "toPort": "input_blocks" }
  ]
}
```

**Key Points:**
- `"kind": "composite"` is REQUIRED
- Port names need `output_` or `input_` prefix
- Node `typeId` must match exactly

---

## Troubleshooting

### Presets Still Grayed Out
- Check `kind` field is `"composite"`
- Verify file was actually replaced
- Clear cache: Delete `config/nodecraft/cache/*`
- Restart application

### Conversion Errors
- Check preset.json files are valid JSON
- Verify all node types exist
- Check console for error messages

### Missing Presets
- Verify presets/ directory structure
- Check file names are `preset.json` (not `presets.json`)
- Look for errors in converter output

---

## Expected Result

After successful conversion, you should see:

**UI Categories:**
- 快速入门 (5 presets) ✅
- 建筑元素 (7 presets) ✅
- 建筑结构 (5 presets) ✅
- 装饰元素 (2 presets) ✅
- 建筑风格 (3 presets) ✅

**Total: 21 working presets**

All should be:
- ✅ Green (not gray)
- ✅ Draggable
- ✅ Functional

---

## Need Help?

Check these files:
- `docs/PresetFormatConversionGuide.md` - Detailed guide
- `docs/preset-system-final-report.md` - Complete documentation
- Console output from converter - Error messages

---

**Time Required**: 5 minutes  
**Difficulty**: Easy  
**Risk**: Low (backup created)  
**Impact**: Makes all 21 presets usable! 🎉
