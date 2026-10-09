# NodeSemanticCatalog & Semantic Composer

Status: **P1 implemented** (read-only facade). AiSemanticComposer remains P2.

## Principle (locked)

`NodeSemanticCatalog` is a **derived view**, not a fifth knowledge base.

- No `node_semantics.json`
- No parallel edge table
- No long-lived `typeId.contains(...)` capability table in `gui.ai`

Sources of truth remain:

| Source | Role |
|--------|------|
| `NodeRegistry` / `NodeInfo` | Facts (typeId, category, ports) |
| `NodeEffect` / `NodeEffectResolver` | Behavior side effects |
| `NodeRecommendationRules` + `node_recommendations.json` | Workflow edges |
| `TypeConversionRegistry` | Type language / converters |

```text
NodeRegistry + NodeInfo + NodeEffect
NodeRecommendationRules
TypeConversionRegistry
        ↓
NodeSemanticCatalog   (read-only facade)
        ↓
AiPlanCapabilityCoverage / AiNodeSchemaCatalog / AiSchemaRetrievalService
        ⇣ (P2)
AiSemanticComposer / Suggested Connections (optional later)
```

## Package

`com.nodecraft.nodesystem.semantic` — **not** under `gui.ai`.

| Type | Role |
|------|------|
| `NodeCapability` | Product-level caps (WALL, WINDOW, VOXELIZE, PREVIEW, …) — keep coarse |
| `NodeDomain` | Domain tags (ARCHITECTURE, CURVE, …) — orthogonal to capability |
| `NodeSemanticEdge` | Derived from recommendation rules |
| `NodeSemanticDescriptor` | Per-node semantic view |
| `NodeSemanticCatalog` | Facade: `describe`, `capabilities`, `domains`, `downstream`/`upstream`, `conversionBetween` |
| `NodeSemanticDeriver` | Deterministic category/typeId/effect → caps/domains |

Catalog must **not** depend on AI classes. AI is a consumer.

Cache key: `(NodeRegistry.introspectionEpoch, NodeSemanticCatalog.getRulesRevision())`.
`DefaultNodeRecommendationService` bumps catalog rules revision on init/reload.

## Domain vs Capability

Do **not** merge these enums.

- Domain = area of modeling language (`ARCHITECTURE`, `SDF`, …)
- Capability = product ability (`WALL`, `BOOLEAN_CUT`, `PREVIEW`, …)

Fine-grained roles (`output_openings`, wall top path) stay on **edges / ports**, not as Capability explosion.

## P1 consumers

1. **AiPlanCapabilityCoverage** — prompt→required stays keyword heuristic; plan→present uses `catalog.capabilities(typeId)`.
2. **AiNodeSchemaCatalog** — builds `effect` / `domains` / `capabilities` / `recommendedNext|Upstream` from catalog.
3. **AiSchemaRetrievalService** — neighbor expansion + converters via catalog; still owns prompt→selection.
4. **AiPromptBuilder** — exports compact semantic metadata when present.

## Local Planner path (locked for P2)

```text
Prompt
  → Intent / Domain / Capability hints
  → Template Library match
       → confident → instantiate
  → else AiSemanticComposer (small canonical chain)
       → confident → AiGraphPlan
  → else abstain
```

Do **not** expand `MockTemplateKind`. Mock demotes to smoke/tests once Composer lands.

## AiSemanticComposer (P2 — not in this round)

Thin path searcher, **not** a second Mock Planner:

- Seed capabilities / seed nodes → walk tiered semantic edges
- Default goal `PREVIEW` for GENERATE_NEW without world intent
- Effect policy as search constraint (`WORLD_WRITE` / `FILE_IO` blocked unless explicit)
- Exclude generic scalar compatibility from default graph
- Output **only** `AiGraphPlan` (then validator / dry-run / apply)

Edge tiers (planned): exact source-node rule → category rule → typed output rule → explicit conversion → safe structural (never “all compatible ports”).

## Non-goals (P1)

- No Composer / Local Planner Template→Composer wiring
- No Suggested Connections rewrite
- No `@NodeInfo(capabilities=…)` mass annotation
- No new semantic config file

## Related

- Preview-first Local Mock: `AiMockPlanService`
- Effect policy: `AiPlanEffectPolicy`
- SurfaceStrip conversion (PURE): `geometry.voxel.surface_strip_to_blocks`
- AI subsystem: `docs/architecture/ai-assistant-subsystem.md`
