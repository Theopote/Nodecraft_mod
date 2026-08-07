# Node Library display / icon caches

Design freeze for Phase E.

## Goal

Keep Node Library scrolling/search responsive by caching:

1. **Icon path resolution** — which resource wins for `(nodeId, category, explicitIcon)`
2. **Sorted display lists** — category node order for the current filter epoch

Texture GPU upload cache + missing-resource negative cache already exist in
`NodeIconManager`; this phase adds the missing **resolution** and **display** layers.

## Resolutions

| Topic | Decision |
|-------|----------|
| Path logic | Extract pure `NodeIconPathResolver` (unit-testable, no GL/Minecraft) |
| Resolution cache | `NodeIconManager` remembers the winning texture-cache key per logical node |
| Display cache | `NodeLibraryDisplayCache` stores sorted `NodeInfo` lists keyed by registry epoch + category list identity |
| Invalidation | Icon caches clear on `cleanup()`; display cache clears when `NodeRegistry` introspection epoch changes |
| Fallback | Unchanged: category-colored solid texture |

## Lookup order (unchanged)

1. Explicit `@NodeInfo.icon` / registry icon override  
2. Node id path (`geometry.boolean.union` → `.../geometry/boolean/union.svg`)  
3. Subcategory path  
4. Main category path  
5. Colored fallback  

## Non-goals

- Preloading all 500+ SVGs at startup  
- Async icon decode off the render thread  
- Changing presentation mapper taxonomy (Phase F/G)  

## Exit gates

1. Path resolver unit tests green  
2. Display cache returns stable sorted lists and invalidates on epoch bump  
3. `NodeIconManager` uses resolver + resolution cache  
4. Advancement Phase E marked PASS  
