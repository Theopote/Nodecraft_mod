# Preset Format Conversion Guide

## Problem

The NodeCraft UI only shows presets with `kind: "composite"` as usable (green, draggable). All other presets appear grayed out.

## Root Cause

The UI loads presets from `/nodecraft/graph_presets.json` which uses a different format than our new parametric preset system:

**Old Format** (graph_presets.json):
```json
{
  "id": "composite.textured_box",
  "displayName": "创建带材质的立方体",
  "kind": "composite",
  "nodes": [
    { "ref": "box", "typeId": "geometry.primitives.box", "x": 0, "y": 0 }
  ],
  "connections": [
    { "fromRef": "box", "fromPort": "output_geometry", ... }
  ]
}
```

**New Format** (presets/*.json):
```json
{
  "preset_id": "quickstart.basic_box",
  "metadata": { ... },
  "parameters": [ ... ],
  "graph": {
    "nodes": [ ... ],
    "connections": [ ... ]
  }
}
```

## Solution

We've created two approaches:

### Approach 1: Format Adapter (Recommended)

Created `PresetFormatAdapter.java` to convert new presets to old format at runtime.

**Steps:**
1. Run `PresetConverterTool.main()` or edit `src/main/resources/nodecraft/graph_presets.json` directly
2. Review the updated preset catalog
3. Run `python tools/sync_folder_presets_from_graph.py` to refresh `presets/**/preset.json` exports
4. Restart NodeCraft

**Command:**
```bash
cd F:/development/NC/nodecraft
./gradlew runPresetConverter
# or
java -cp build/classes/java/main com.nodecraft.nodesystem.preset.PresetConverterTool
```

### Approach 2: Integrate at Startup

Modify `NodeCraft.initializePresetSystem()` to automatically convert and merge presets:

```java
private void initializePresetSystem() {
    Path presetDirectory = Path.of("config", MOD_ID, "presets");
    
    // Load new format presets
    PresetRegistry.getInstance().loadPresets(presetDirectory);
    
    // Convert and merge with old format for UI compatibility
    try {
        Path existingGraphPresets = Path.of("src/main/resources/nodecraft/graph_presets.json");
        Path outputGraphPresets = Path.of("config", MOD_ID, "graph_presets_merged.json");
        
        PresetFormatAdapter.generateGraphPresetsJson(
            presetDirectory,
            outputGraphPresets,
            existingGraphPresets
        );
        
        // Reload UI with merged presets
        GraphPresetCatalog.getInstance().reload();
        
    } catch (IOException e) {
        LOGGER.error("Failed to convert presets", e);
    }
}
```

## Conversion Details

### Category Mapping

| New Category | Display Name | Description |
|--------------|-------------|-------------|
| `quickstart` | 快速入门 | Simple beginner presets |
| `building_elements` | 建筑元素 | Reusable components |
| `architectural` | 建筑结构 | Complete structures |
| `decorative` | 装饰元素 | Decorative elements |
| `styles` | 建筑风格 | Style-specific designs |

### Node Reference Mapping

New format uses full node type IDs:
- `geometry.primitives.box_by_corner_and_size`
- `material.basic_assignment.assign_block_type`

Old format expects same IDs, so no mapping needed.

### Port Name Differences

**Critical**: Port names may differ between formats!

**New Format:**
- Outputs: `geometry`, `blocks`, `curve`, etc.
- Inputs: `geometry`, `center`, `size`, etc.

**Old Format:**
- Outputs: `output_geometry`, `output_blocks`, etc.
- Inputs: `input_geometry`, `input_center`, etc.

**Fix Required**: Update port names in connections when converting.

## Updated Converter

Here's the corrected port name mapping:

```java
// In PresetFormatAdapter.convertToOldFormat()
for (PresetGraph.PresetConnectionDefinition connDef : newPreset.getGraph().getConnections()) {
    GraphPresetRules.PresetConnection oldConn = new GraphPresetRules.PresetConnection();
    oldConn.fromRef = connDef.getFrom().getNode();
    oldConn.fromPort = mapPortName(connDef.getFrom().getPort(), true);  // output
    oldConn.toRef = connDef.getTo().getNode();
    oldConn.toPort = mapPortName(connDef.getTo().getPort(), false);     // input
    oldConnections.add(oldConn);
}

private static String mapPortName(String portName, boolean isOutput) {
    // Add output_ or input_ prefix if not present
    String prefix = isOutput ? "output_" : "input_";
    if (portName.startsWith("output_") || portName.startsWith("input_")) {
        return portName;
    }
    return prefix + portName;
}
```

## Testing

After conversion, verify:

1. ✅ All 21 presets appear in UI
2. ✅ All presets are green (not grayed out)
3. ✅ All presets can be dragged to canvas
4. ✅ Presets instantiate correctly when dropped
5. ✅ Node connections are valid
6. ✅ Parameters can be adjusted (if UI supports)

## Files Created

1. `PresetFormatAdapter.java` - Conversion logic
2. `PresetConverterTool.java` - Command-line tool
3. `PresetFormatConversionGuide.md` - This document

## Next Steps

1. **Immediate**: Run converter to generate updated graph_presets.json
2. **Short-term**: Fix port name mapping if needed
3. **Long-term**: Unify preset formats or build UI for new format

---

**Status**: Converter created, needs testing  
**Priority**: High - blocks preset usability  
**ETA**: 30 minutes to test and deploy
