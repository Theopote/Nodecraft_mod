# NodeSemanticCatalog & Semantic Composer

Status: **Catalog locked for Composer** (S1–S3 + semantic port keys). **AiSemanticComposer v1** consumes Catalog as a frozen read-only facade.

## Principle (locked)

`NodeSemanticCatalog` is a **derived view**, not a fifth knowledge base.

- No `node_semantics.json`
- No parallel edge table
- Prefer missing a capability over a false positive
- Catalog is **static** semantic knowledge — never reads graph / node runtime state

```text
NodeRegistry + NodeInfo + NodeEffect
NodeRecommendationRules (+ Loader revision)
TypeConversionRegistry
        ↓
NodeSemanticCatalog   (read-only facade)
        ↓
AiPlanCapabilityCoverage / AiNodeSchemaCatalog / AiSchemaRetrievalService
        ↓
AiSemanticComposer (`com.nodecraft.gui.ai.compose`) → `AiGraphPlan` only
```

## Packages

| Package | Contents |
|---------|----------|
| `com.nodecraft.nodesystem.semantic` | Catalog, Descriptor, Edge(+Kind), PortKeys, Capability, Domain, Deriver |
| `com.nodecraft.nodesystem.recommendation` | `NodeRecommendationRules`, `NodeRecommendationRulesLoader` (shared SoT + revision) |
| `com.nodecraft.gui.recommendation` | Scorer, Connector, Overlay, UI — **consumers**, not rules owners |
| `com.nodecraft.gui.ai.compose` | **AiSemanticComposer v1** — deterministic Preview-first workflow completer over Catalog |

## Edge tiers

`NodeSemanticEdge.kind`:

| Kind | Source | Typical Composer cost |
|------|--------|------------------------|
| `EXACT` | `sourceNodes` port rules | 1 |
| `CATEGORY` | `sourceCategories` output/input type maps | 3 |
| `TYPE` | global `outputTypes` | 5 |

Dedupe key: `(targetNodeId, sourcePortId)` — best kind then better priority wins; prefer a filled `targetPortId` on ties (category string rules often omit connect ports). Different source ports stay distinct.

APIs:

- `exactDownstream` / `exactUpstream`
- `effectiveDownstream` / `effectiveUpstream` (merged)
- Port-local: `effectiveDownstream(nodeId, portKey, dataType)`

## Semantic port keys

Recommendation rules may use **synthetic** keys such as:

- `output_face:horizontal`
- `output_face:vertical`

while the physical port remains `output_face`.

| Query | Exact synthetic | CATEGORY / TYPE |
|-------|-----------------|-----------------|
| `output_face:horizontal` | horizontal EXACT only | via physical base `output_face` |
| `output_face:vertical` | vertical EXACT only | same |
| `output_face` | **does not** expand to both orientations | physical port only |

Helpers: `NodeSemanticPortKeys.physicalBase` / `isVariant` / `matchesEdgePort` (equality only).

**Boundary:** Suggested Connections / Composer context resolve orientation from runtime (e.g. GetBoxFace face name) and pass the semantic key into Catalog. Catalog never opens a graph or node instance for that purpose.

## Capability derivation

`NodeSemanticDeriver` priority: exact families → category-family → `NodeEffect` → conservative fallback.

- `window_array` → WINDOW + OPENING + ARRAY; other `window*` → WINDOW only
- `door_array` → OPENING + ARRAY; other door ids → OPENING
- `difference` / subtract → BOOLEAN_CUT; union/intersection → not
- `WORLD_APPLY` from `effect == WORLD_WRITE` (not `output.execute.*`)

### WORLD_APPLY vs APPLY

Both enum values still exist and are co-tagged on `WORLD_WRITE`. Composer v1 uses `AiComposeGoal` (`PREVIEW` / `WORLD_OUTPUT` / `CAPABILITY_SET`) rather than treating those capabilities as search terminals. No APPLY/WORLD_APPLY enum merge in this round.

## Cache / revision

Cache key: `(NodeRegistry.introspectionEpoch, NodeRecommendationRulesLoader.getRulesRevision())`.

## AiSemanticComposer v1

Composer is a **deterministic workflow completion engine** over Catalog edges — not a second Mock Planner / NL designer.

**No hardcoded workflow table.** Paths such as Sphere→Voxelize→Assign→Preview or Wall+Window→Difference must emerge from Catalog EXACT/CATEGORY/TYPE edges + upstream fill + explicit converters. Seed resolution may map capabilities to type ids; it must not encode recipe chains.

Algorithm sketch: multi-seed → downstream UCS (`bestCost` map) → required-input upstream fill (plan-local join, then Catalog upstream spawn) → converter insert → **`validatePlan` + `AiPlanValidator.checkBeforeApply`** + capability coverage → plan or `semantic_composer_no_confident_plan`.

**Success contract:** Composer success ⟺ full `validatePlan` (no Required-input soft filter) **and** `checkBeforeApply` allows. Default-backed / alternate-source ports use `required=false` so Validator matches runtime.

**v1 limit:** at most one instance per node type (`PlanBuilder.typeToRef`) — e.g. Wall and Window may share one `GetBoxFace`.

**P2 note:** remove `guessOutput` — introspect fail → abstain (no change required for P1 freeze).

Hard constraints (search-time):

- Effect gate via `NodeSemanticCatalog.effect(typeId)` **before enqueue**: allow PURE / CONTEXT_READ / WORLD_READ / PREVIEW_WRITE; block WORLD_WRITE / FILE_IO / CONTEXT_WRITE (WORLD_WRITE only under `AiComposeGoal.WORLD_OUTPUT`)
- Edges only from `effectiveDownstream` / `effectiveUpstream` + explicit `TypeConversionRegistry` converters
- No generic scalar (DOUBLE/FLOAT/INTEGER/BOOLEAN/STRING) CATEGORY/TYPE flood; no orientation expansion (`output_face:…`) without caller key
- Cap `maxNodes` (default 12); budget / unsupported conversion / missing hard caps → **abstain**
- Deterministic refs (`sphere_1`); layout `x=depth*280`, `y=branch*180`
- Output **`AiGraphPlan` only** (no second graph model)

Catalog gaps closed for Preview chains:

- Sweep: `surface_strip` / `geometry.solids.sweep.output_surface_strip` → `geometry.voxel.surface_strip_to_blocks`
- Architecture faces: `box_geometry` / `geometry.primitives.box.output_box_geometry` → `reference.points.get_box_face` → `box_face` consumers

## Local Planner path

```text
Template match (confident)
  → else AiSemanticComposer
    → else AiMockPlanService
      → else abstain
```

Do not expand `MockTemplateKind`. Do not rewrite Suggested Connections to consume Catalog yet.

## Non-goals

- No Catalog API redesign unless a real Composer gap appears
- No Suggested Connections → Catalog rewrite
- No APPLY vs WORLD_APPLY enum merge yet
- No remote planner wiring / complex param inference
