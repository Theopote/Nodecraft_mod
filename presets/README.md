# NodeCraft Preset Library

This directory contains **folder-format** preset exports (`preset.json` per preset).

**Runtime source of truth:** `src/main/resources/nodecraft/graph_presets.json` — used by the preset library panel and Quickstart menu examples. Keep folder exports aligned when editing presets here, or export from the editor after changes.

## Directory Structure

```
presets/
├── quickstart/           # Simple presets for beginners
├── architectural/        # Building and structure presets
├── building-elements/    # Reusable components (windows, doors, etc.)
├── styles/              # Themed collections (medieval, modern, etc.)
├── patterns/            # Repeating patterns
└── workflows/           # Complete multi-step workflows
```

## Preset Format

Each preset is a directory containing:
- `preset.json` - Preset definition (required)
- `thumbnail.png` - Main preview image (optional)
- `previews/` - Additional screenshots (optional)

## Creating a Preset

1. Design your node graph in NodeCraft
2. Right-click and select "Save as Preset"
3. Fill in metadata (name, description, tags)
4. Mark parameters to expose
5. Add thumbnails
6. Save to preset library

## Available Presets

See `src/main/resources/nodecraft/graph_presets.json` for the full built-in catalog (quickstart, building elements, architectural, styles, workflows).

After editing graph presets, run `python tools/sync_folder_presets_from_graph.py` to refresh folder exports here.

## Documentation

See `/docs/preset-library-implementation-spec.md` for technical details.
