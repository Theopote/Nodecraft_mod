# NodeCraft v1 Legacy Inventory

This document records the nodes that currently resolve to `spatial.legacy` in the library.

It is the operational boundary for compatibility debt that remains loadable but is not part of the v1 canonical node tree.

## Rules

- A node listed here is compatibility-only or deferred.
- A node listed here must not be used as a precedent for new canonical taxonomy.
- If a node later receives a stable v1 home, it must leave this list and move to a canonical category with alias coverage.

## Current Legacy Inventory

### Legacy direct block-output generators

- `spatial.generators.line_blocks`
- `spatial.generators.rectangle_blocks`
- `spatial.generators.arc_blocks`
- `spatial.generators.semicircle_blocks`
- `spatial.generators.regular_polygon_blocks`
- `spatial.generators.star_blocks`
- `spatial.generators.ellipse_blocks`
- `spatial.generators.circle_sphere_blocks`
- `spatial.generators.box_blocks`
- `spatial.generators.region_box_blocks`
- `spatial.generators.cylinder_blocks`
- `spatial.generators.cone_blocks`
- `spatial.generators.ellipsoid_blocks`
- `spatial.generators.torus_blocks`
- `spatial.generators.octahedron_blocks`
- `spatial.generators.tetrahedron_blocks`
- `spatial.generators.triangular_prism_blocks`
- `spatial.generators.triangular_pyramid_blocks`
- `spatial.generators.polyline_blocks`
- `spatial.generators.curve_blocks`

### Deferred analysis helpers

- `spatial.analysis.geometry_info`
- `spatial.analysis.select_sphere_band_sector`
- `spatial.analysis.sphere_uv`
- `spatial.analysis.sphere_point_info`
- `spatial.analysis.deconstruct_surface_strip`

### Deferred instancing helpers

- `spatial.instancing.grow_along_normals`
- `spatial.instancing.grow_along_sphere_normal`

### Deferred voxel boolean helpers

- `spatial.voxel.union_coords`
- `spatial.voxel.intersection_coords`
- `spatial.voxel.difference_coords`

### Deferred Minecraft input helpers

- `inputs.minecraft.selected_block_sequence`
- `inputs.minecraft.selected_entity`

## Canonical Migrations Already Completed

These ids used to live in legacy domains but now have canonical v1 homes:

- `inputs.minecraft.current_time -> input.context.current_time`
- `inputs.minecraft.dimension_info -> input.context.dimension_info`
- `spatial.voxel.geometry_to_blocks -> output.execute.bake_geometry_to_blocks`
- `spatial.voxel.surface_strip_to_blocks -> output.execute.bake_surface_strip_to_blocks`
- `spatial.instancing` remains deferred; it does not yet have canonical v1 replacements

## Source of Truth

- Runtime category override:
  - `src/main/java/com/nodecraft/nodesystem/registry/NodeRegistry.java`
- Library display ordering:
  - `src/main/java/com/nodecraft/gui/components/NodeLibraryComponent.java`
- Node-level migration plan:
  - `docs/nodecraft-v1-node-migration-ledger.md`
