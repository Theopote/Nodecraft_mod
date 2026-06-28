# NodeCraft Preset Library

This directory contains preset definitions for NodeCraft.

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

### Quickstart (Beginner-Friendly)
- **basic-box** - Simple box structure
- **simple-tower** - Cylindrical tower with windows

More presets coming soon!

## Documentation

See `/docs/preset-library-implementation-spec.md` for technical details.
