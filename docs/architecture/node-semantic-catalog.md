# NodeSemanticCatalog & Semantic Composer

Status: design (P2 roadmap). Implementation of the catalog API and composer is **out of scope** for the SurfaceStrip PURE fix round; this document freezes direction so Local Planner stops expanding `MockTemplateKind`.

## Goal

Unify semantic signals used by:

- Remote Planner (schema retrieval / capability coverage)
- Local Planner (template match → composer → abstain)
- Suggested Connections (recommendation edges)
- Effect / Preview-first policy

into one **NodeSemanticCatalog** source of truth (SoT), then a thin **AiSemanticComposer** that builds small canonical chains without hardcoding new Mock templates.

## Local Planner path (locked)

```text
Prompt
  → Intent / DomainTag / Capability hints
  → Template Library match (enriched metadata)
       → confident → instantiate template
  → else AiSemanticComposer (small canonical chain)
       → confident → plan
  → else abstain (local_planner_no_confident_plan)
```

**Do not** add HOUSE / WALL / ROOF / … to `MockTemplateKind`. Keep the current offline smoke set as-is.

## Existing building blocks (reuse)

| Building block | Location | Role for Catalog / Composer |
|----------------|----------|-----------------------------|
| Recommendation edges | `NodeRecommendationRules` + `node_recommendations.json` | `recommendedNext` / `recommendedUpstream` with ports |
| Domain tags | `AiIntentAnalysisService.DomainTag` + `detectDomainTags` | Prompt seed (temporary until catalog-backed tags) |
| Capabilities | `AiPlanCapabilityCoverage` | Completeness after compose / template instantiate (today: typeId heuristics) |
| Type conversions | `TypeConversionRegistry` | Insert converter nodes when wiring |
| Effects | `NodeEffect` + `NodeEffectResolver` + `AiPlanEffectPolicy` | Candidate filter; Preview-first / WORLD_WRITE gate |
| Templates | `AiTemplateLibrary.Template(name, description, keywords, dslJson)` | Baseline match; needs metadata enrichment |
| Schema hints | `AiNodeSchemaCatalog.recommendedNext/Upstream` | Retrieval hints only — **not** SoT |

**Does not exist today:** `NodeSemanticCatalog` Java type. Suggested Connections has no separate “Phase 2 schema”; the live stack is UI + recommendation rules.

## NodeSemanticCatalog SoT (field draft)

Per `typeId`:

```text
typeId → {
  domainTags:      Set<DomainTag>          // ARCHITECTURE, CURVE, …
  capabilities:    Set<Capability>         // WALL, WINDOW, WORLD_APPLY, …
  effect:          NodeEffect              // from annotation / resolver
  recommendedNext: List<PortEdge>          // nodeId, fromPort, toPort, reason, order
  recommendedUpstream: List<PortEdge>
  conversionRoles: List<ConversionRole>    // e.g. SURFACE_STRIP → BLOCK_LIST via this node
  version:         int
}
```

### Data source priority

1. **Build-time / annotation catalog** (`@NodeInfo`, generated node catalog) — effect, category, ports  
2. **Recommendation rules JSON** — neighbor edges and port hints  
3. **TypeConversionRegistry** — conversionRoles  
4. **Heuristic fallback** — current `AiPlanCapabilityCoverage` / keyword DomainTag (to be retired)

Catalog API should start as a **read-only façade** over (1)–(3), not a parallel JSON dump of everything.

## Template metadata (follow-on)

Upgrade saved / library templates toward:

```json
{
  "meta": {
    "name": "Wall With Windows",
    "description": "...",
    "keywords": ["wall", "window", "墙", "窗户"],
    "domainTags": ["ARCHITECTURE"],
    "capabilities": ["WALL", "WINDOW", "OPENING"],
    "version": 1
  },
  "graph": { "nodes": [], "connections": [] }
}
```

Matching then becomes: DomainTag overlap + keyword overlap + capability hints — not CJK-as-one-token luck alone. Chinese tokenize + Save-as-Template writing `meta` are separate P2 slices after the catalog façade exists.

## AiSemanticComposer (thin)

Deterministic, not a local LLM:

1. Seed from prompt DomainTag / capability / exact primitive keywords (`sphere`, `wall`, …)  
2. Walk `recommendedNext` (and conversions) toward a **Preview-first** terminal:
   - Geometry / Sweep → voxel conversion (`geometry.voxel.*` PURE) → Assign Block Type → Preview Blocks  
   - Append Apply Changes **only** if `hasWorldApplyIntent`  
3. Low confidence → abstain (same product rule as Mock: Abstain > Wrong confident graph)

Composer must reuse the same voxel + material chain Local Mock already uses after the SurfaceStrip PURE fix (`geometry.voxel.surface_strip_to_blocks`, `geometry.voxel.voxelize_geometry`).

## Migration order

1. **Catalog API** — read-only wrapper over NodeInfo + rules + TypeConversionRegistry + effect resolver  
2. **Template metadata** — `meta.domainTags` / `capabilities` / `keywords` / `version` on library JSON  
3. **Chinese tokenize** — improve `AiTemplateLibrary.tokenize` for CJK  
4. **Save as Template** — persist meta (user edit or AI/graph-derived)  
5. **Thin AiSemanticComposer** — Template miss → compose → abstain; freeze Mock enum  

## Non-goals (this design doc / current round)

- Implementing `AiSemanticComposer` or `NodeSemanticCatalog` Java classes now  
- Landing Template file-format migration in this PR  
- Expanding Mock templates  
- Reclassifying `output.execute.sdf_to_blocks` (same WORLD_WRITE-vs-conversion smell; separate follow-up)

## Related

- Preview-first Local Mock: `AiMockPlanService`  
- Effect policy: `AiPlanEffectPolicy`  
- SurfaceStrip conversion (PURE): `geometry.voxel.surface_strip_to_blocks` (alias: `output.execute.bake_surface_strip_to_blocks`)  
- AI subsystem overview: `docs/architecture/ai-assistant-subsystem.md`
